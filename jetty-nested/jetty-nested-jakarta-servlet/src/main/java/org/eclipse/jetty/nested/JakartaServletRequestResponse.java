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

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.util.URIUtil;

public class JakartaServletRequestResponse implements NestedRequestResponse
{
    private static final int BUFFER_SIZE = 1024;

    private final HttpServletRequest _httpServletRequest;
    private final HttpServletResponse _httpServletResponse;
    private final ServletInputStream _inputStream;
    private final ServletOutputStream _outputStream;
    private final byte[] _outputBuffer = new byte[BUFFER_SIZE];
    private AsyncContext _asyncContext;
    private boolean _outClosed = false;

    public JakartaServletRequestResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException
    {
        _httpServletRequest = httpServletRequest;
        _httpServletResponse = httpServletResponse;
        _inputStream = httpServletRequest.getInputStream();
        _outputStream = httpServletResponse.getOutputStream();
    }

    @Override
    public void startAsync()
    {
        if (_asyncContext != null)
            throw new IllegalStateException();
        _asyncContext = _httpServletRequest.startAsync();
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
        String pathInContext = URIUtil.addPaths(_httpServletRequest.getContextPath(), _httpServletRequest.getServletPath());
        return URIUtil.addPathQuery(pathInContext, _httpServletRequest.getQueryString());
    }

    @Override
    public String getProtocol()
    {
        return _httpServletRequest.getProtocol();
    }

    @Override
    public String getMethod()
    {
        return _httpServletRequest.getMethod();
    }

    @Override
    public Enumeration<String> getHeaderNames()
    {
        return _httpServletRequest.getHeaderNames();
    }

    @Override
    public Enumeration<String> getHeaders(String headerName)
    {
        return _httpServletRequest.getHeaders(headerName);
    }

    @Override
    public boolean isSecure()
    {
        return _httpServletRequest.isSecure();
    }

    @Override
    public long getContentLengthLong()
    {
        return _httpServletRequest.getContentLengthLong();
    }

    @Override
    public boolean isReadReady()
    {
        return _inputStream.isReady();
    }

    @Override
    public boolean isReadClosed()
    {
        return _inputStream.isFinished();
    }

    @Override
    public void closeInput() throws IOException
    {
        _inputStream.close();
    }

    @Override
    public Content read() throws IOException
    {
        byte[] content = new byte[BUFFER_SIZE];
        int len = _inputStream.read(content);
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
        _inputStream.setReadListener(new jakarta.servlet.ReadListener()
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
        _httpServletResponse.setStatus(status);
    }

    @Override
    public void addHeader(String name, String value)
    {
        _httpServletResponse.addHeader(name, value);
    }

    @Override
    public boolean isWriteReady()
    {
        return _outputStream.isReady();
    }

    @Override
    public boolean isWriteClosed()
    {
        return _outClosed;
    }

    @Override
    public void write(ByteBuffer buffer) throws IOException
    {
        if (buffer.hasArray())
        {
            byte[] array = buffer.array();
            int offset = buffer.arrayOffset() + buffer.position();
            int length = buffer.remaining();
            _outputStream.write(array, offset, length);
            buffer.position(buffer.position() + length);
        }
        else
        {
            int len = Math.min(buffer.remaining(), _outputBuffer.length);
            buffer.get(_outputBuffer, 0, len);
            _outputStream.write(_outputBuffer, 0, len);
        }
    }

    @Override
    public void closeOutput() throws IOException
    {
        _outClosed = true;
        _outputStream.close();
    }

    @Override
    public void setWriteListener(WriteListener writeListener)
    {
        _outputStream.setWriteListener(new jakarta.servlet.WriteListener()
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

    @Override
    public String getRemoteAddr()
    {
        return _httpServletRequest.getRemoteAddr();
    }

    @Override
    public int getRemotePort()
    {
        return _httpServletRequest.getRemotePort();
    }

    @Override
    public String getLocalAddr()
    {
        return _httpServletRequest.getLocalAddr();
    }

    @Override
    public int getLocalPort()
    {
        return _httpServletRequest.getLocalPort();
    }
}
