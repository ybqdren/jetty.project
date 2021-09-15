//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.nested;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.io.ByteBufferOutputStream;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IO;

public class NestedChannel extends HttpChannel implements ReadListener
{
    private static final int BUFFER_SIZE = 1024;

    private final HttpInput _httpInput;
    private final ServletInputStream _inputStream;
    private final Callback _asyncCompleteCallback;
    private final ByteBufferPool _bufferPool;

    private ReadState _state = ReadState.AWAITING_DATA;
    private boolean _inputEOF;
    private Throwable _error;

    private enum ReadState
    {
        AWAITING_DATA,
        NEED_DATA,
        DATA_AVAILABLE,
        ERROR,
        EOF
    }

    public NestedChannel(NestedConnector connector, HttpConfiguration configuration, NestedEndpoint endPoint, NestedTransport transport, Callback asyncCompleteCallback) throws IOException
    {
        super(connector, configuration, endPoint, transport);
        HttpServletRequest request = endPoint.getRequest();
        _inputStream = request.getInputStream();
        _httpInput = getRequest().getHttpInput();
        _asyncCompleteCallback = asyncCompleteCallback;
        _bufferPool = connector.getByteBufferPool();
        _inputStream.setReadListener(this);
    }

    @Override
    public boolean needContent()
    {
        synchronized (this)
        {
            switch (_state)
            {
                case NEED_DATA:
                    return false;

                case AWAITING_DATA:
                    _state = ReadState.NEED_DATA;
                    return false;

                case DATA_AVAILABLE:
                case ERROR:
                case EOF:
                    return true;

                default:
                    throw new IllegalStateException(_state.name());
            }
        }
    }

    @Override
    public HttpInput.Content produceContent()
    {
        boolean produce = false;
        synchronized (this)
        {
            switch (_state)
            {
                case DATA_AVAILABLE:

                    if (_inputEOF)
                    {
                        _state = ReadState.EOF;
                        return new HttpInput.EofContent();
                    }

                    if (_inputStream.isReady())
                        produce = true;
                    else
                        _state = ReadState.AWAITING_DATA;
                    break;

                case EOF:
                    return new HttpInput.EofContent();

                case ERROR:
                    return new HttpInput.ErrorContent(_error);
            }
        }

        if (produce)
        {
            NestedContent content = new NestedContent(_bufferPool);
            try
            {
                content.readFrom(_inputStream);
            }
            catch (IOException e)
            {
                fail(e);
                return new HttpInput.ErrorContent(e);
            }
            return content;
        }

        return null;
    }

    @Override
    public boolean failAllContent(Throwable failure)
    {
        synchronized (this)
        {
            switch (_state)
            {
                case EOF:
                    return true;

                case ERROR:
                    _error.addSuppressed(failure);
                    return false;

                default:
                    _state = ReadState.ERROR;
                    _error = failure;
                    return false;
            }
        }
    }

    private void fail(Throwable failure)
    {
        synchronized (this)
        {
            switch (_state)
            {
                case ERROR:
                    _error.addSuppressed(failure);
                    break;

                default:
                    _state = ReadState.ERROR;
                    _error = failure;
                    break;
            }
        }
    }

    @Override
    public boolean failed(Throwable failure)
    {
        fail(failure);
        return _httpInput.onContentProducible();
    }

    @Override
    protected boolean eof()
    {
        synchronized (this)
        {
            _inputEOF = true;
            switch (_state)
            {
                case NEED_DATA:
                case AWAITING_DATA:
                    _state = ReadState.DATA_AVAILABLE;
                    break;

                default:
                    break;
            }
        }

        return _httpInput.onContentProducible();
    }

    @Override
    public void onDataAvailable()
    {
        boolean isContentProducible = false;
        synchronized (this)
        {
            switch (_state)
            {
                case DATA_AVAILABLE:
                    throw new IllegalStateException();

                case AWAITING_DATA:
                    if (_inputStream.isReady())
                        _state = ReadState.DATA_AVAILABLE;
                    break;

                case NEED_DATA:
                    isContentProducible = _inputStream.isReady();
                    if (isContentProducible)
                        _state = ReadState.DATA_AVAILABLE;
                    break;

                default:
                    // Do nothing.
                    break;
            }
        }

        if (isContentProducible)
        {
            boolean handle = _httpInput.onContentProducible();
            if (handle)
                execute(this);
        }
    }

    @Override
    public void onAllDataRead()
    {
        boolean reschedule = eof();
        if (reschedule)
            execute(this);
        _asyncCompleteCallback.succeeded();
    }

    @Override
    public void onError(Throwable t)
    {
        boolean handle = failed(t);
        if (handle)
            execute(this);
        _asyncCompleteCallback.failed(t);
    }

    public static class NestedContent extends HttpInput.Content
    {
        private final ByteBufferPool _bufferPool;

        public NestedContent(ByteBufferPool bufferPool)
        {
            super(bufferPool.acquire(BUFFER_SIZE, false));
            _bufferPool = bufferPool;
        }

        public void readFrom(ServletInputStream inputStream) throws IOException
        {
            ByteBuffer byteBuffer = getByteBuffer();
            IO.copy(inputStream, new ByteBufferOutputStream(byteBuffer), BufferUtil.space(byteBuffer));
        }

        @Override
        public void succeeded()
        {
            super.succeeded();
            _bufferPool.release(getByteBuffer());
        }

        @Override
        public void failed(Throwable x)
        {
            super.failed(x);
            _bufferPool.release(getByteBuffer());
        }
    }
}