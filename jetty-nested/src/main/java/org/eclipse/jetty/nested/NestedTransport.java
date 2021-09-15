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
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.server.HttpTransport;
import org.eclipse.jetty.util.Callback;

public class NestedTransport implements HttpTransport
{
    private final NestedEndpoint _endpoint;
    private final Callback _asyncCompleteCallback;
    private ContentFlusher _flusher;

    public NestedTransport(NestedEndpoint endpoint, Callback asyncCompleteCallback)
    {
        _endpoint = endpoint;
        _asyncCompleteCallback = asyncCompleteCallback;
    }

    @Override
    public void send(MetaData.Request request, MetaData.Response response, ByteBuffer content, boolean lastContent, Callback callback)
    {
        HttpServletResponse httpServletResponse = _endpoint.getResponse();
        if (response != null)
        {
            httpServletResponse.setStatus(response.getStatus());
            for (HttpField field : response.getFields())
            {
                httpServletResponse.addHeader(field.getName(), field.getValue());
            }

            try
            {
                ServletOutputStream outputStream = httpServletResponse.getOutputStream();
                _flusher = new ContentFlusher(outputStream);
            }
            catch (IOException e)
            {
                callback.failed(e);
                return;
            }
        }

        // If last content we want to also signal we are done to asyncContext when done.
        if (lastContent)
            callback = Callback.from(callback, _asyncCompleteCallback);
        _flusher.write(content, lastContent, callback);
        if (lastContent)
            _flusher = null;
    }

    @Override
    public boolean isPushSupported()
    {
        return false;
    }

    @Override
    public void push(MetaData.Request request)
    {
        throw new UnsupportedOperationException("push not supported");
    }

    @Override
    public void onCompleted()
    {
        HttpServletResponse response = _endpoint.getResponse();
        try
        {
            response.getOutputStream().close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void abort(Throwable failure)
    {
        _endpoint.close();
    }
}
