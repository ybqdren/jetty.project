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
    public static final HttpInput.EofContent EOF = new HttpInput.EofContent();

    private final HttpInput _httpInput;
    private final ServletInputStream _inputStream;
    private final Callback _asyncCompleteCallback;
    private final ByteBufferPool _bufferPool;

    NestedContent _content;

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
        return _httpInput.isReady();
    }

    @Override
    public HttpInput.Content produceContent()
    {
        if (_inputStream.isFinished())
            return EOF;

        try
        {
            if (_content == null)
                _content = new NestedContent(_bufferPool);
            _content.readFrom(_inputStream);
        }
        catch (IOException e)
        {
            _content.failed(e);
            _content = null;
            fail(e);
            return new HttpInput.ErrorContent(e);
        }

        if (_content.hasContent())
        {
            HttpInput.Content content = _content;
            _content = null;
            return content;
        }

        if (_inputStream.isFinished())
            return EOF;

        return null;
    }

    @Override
    public boolean failAllContent(Throwable failure)
    {
        return _inputStream.isFinished();
    }

    private void fail(Throwable failure)
    {
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
        return _httpInput.onContentProducible();
    }

    @Override
    public void onDataAvailable()
    {
        boolean handle = _httpInput.onContentProducible();
        if (handle)
            execute(this);
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