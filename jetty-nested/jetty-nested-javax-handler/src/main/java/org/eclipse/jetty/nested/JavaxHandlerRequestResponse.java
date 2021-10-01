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
import java.util.Enumeration;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

public class JavaxHandlerRequestResponse implements NestedRequestResponse
{
    private static final int BUFFER_SIZE = 1024;

    private final Request _request;
    private final Response _response;
    private final HttpInput _httpInput;
    private final HttpOutput _httpOutput;
    private AsyncContext _asyncContext;

    public JavaxHandlerRequestResponse(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
    {
        _request = baseRequest;
        _response = baseRequest.getResponse();
        _httpInput = _request.getHttpInput();
        _httpOutput = _response.getHttpOutput();
        _request.setHandled(true);
    }

    @Override
    public void startAsync()
    {
        if (_asyncContext != null)
            throw new IllegalStateException();
        _asyncContext = _request.startAsync();
    }

    @Override
    public void stopAsync()
    {
        _asyncContext.complete();
        _asyncContext = null;
    }

    @Override
    public String getRequestURI()
    {
        return _request.getRequestURI();
    }

    @Override
    public String getProtocol()
    {
        return _request.getProtocol();
    }

    @Override
    public String getMethod()
    {
        return _request.getMethod();
    }

    @Override
    public Enumeration<String> getHeaderNames()
    {
        return _request.getHeaderNames();
    }

    @Override
    public Enumeration<String> getHeaders(String headerName)
    {
        return _request.getHeaders(headerName);
    }

    @Override
    public boolean isSecure()
    {
        return _request.isSecure();
    }

    @Override
    public long getContentLengthLong()
    {
        return _request.getContentLengthLong();
    }

    @Override
    public boolean isReadReady()
    {
        return _httpInput.isReady();
    }

    @Override
    public boolean isReadClosed()
    {
        return _httpInput.isFinished();
    }

    @Override
    public void closeInput() throws IOException
    {
        _httpInput.close();
    }

    @Override
    public Content read() throws IOException
    {
        // TODO: how do we get Content without buffering from HttpInput?
        //  bypass HttpInput and use the methods on the HttpChannel?
        byte[] content = new byte[BUFFER_SIZE];
        int len = _httpInput.read(content);
        if (len < 0)
            return null;

        ByteBuffer contentBuffer = ByteBuffer.wrap(content, 0, len);
        return new Content()
        {
            @Override
            public ByteBuffer getByteBuffer()
            {
                return contentBuffer;
            }

            @Override
            public void release()
            {
            }
        };
    }

    @Override
    public void setReadListener(ReadListener readListener)
    {
        _httpInput.setReadListener(new javax.servlet.ReadListener()
        {
            @Override
            public void onDataAvailable() throws IOException
            {
                readListener.onDataAvailable();
            }

            @Override
            public void onAllDataRead() throws IOException
            {
                readListener.onAllDataRead();
            }

            @Override
            public void onError(Throwable t)
            {
                readListener.onError(t);
            }
        });
    }

    @Override
    public void setStatus(int status)
    {
        _response.setStatus(status);
    }

    @Override
    public void addHeader(String name, String value)
    {
        _response.addHeader(name, value);
    }

    @Override
    public boolean isWriteReady()
    {
        return _httpOutput.isReady();
    }

    @Override
    public boolean isWriteClosed()
    {
        return _httpOutput.isClosed();
    }

    @Override
    public void write(ByteBuffer buffer) throws IOException
    {
        _httpOutput.write(buffer);
    }

    @Override
    public void closeOutput() throws IOException
    {
        _httpOutput.close();
    }

    @Override
    public void setWriteListener(WriteListener writeListener)
    {
        _httpOutput.setWriteListener(new javax.servlet.WriteListener()
        {
            @Override
            public void onWritePossible() throws IOException
            {
                writeListener.onWritePossible();
            }

            @Override
            public void onError(Throwable t)
            {
                writeListener.onError(t);
            }
        });
    }
}
