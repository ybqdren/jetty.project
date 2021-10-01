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
import java.util.Enumeration;
import java.util.EventListener;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;

public class NestedConnection implements Connection
{
    private final NestedConnector _connector;
    private final NestedEndpoint _endpoint;

    public NestedConnection(NestedConnector connector, NestedEndpoint endpoint)
    {
        _connector = connector;
        _endpoint = endpoint;
    }

    @Override
    public void addEventListener(EventListener listener)
    {
    }

    @Override
    public void removeEventListener(EventListener listener)
    {
    }

    @Override
    public void onOpen()
    {
        _endpoint.onOpen();
    }

    @Override
    public void onClose(Throwable cause)
    {
    }

    @Override
    public EndPoint getEndPoint()
    {
        return _endpoint;
    }

    @Override
    public void close()
    {
        _endpoint.close();
    }

    @Override
    public boolean onIdleExpired()
    {
        return false;
    }

    @Override
    public long getMessagesIn()
    {
        return 0;
    }

    @Override
    public long getMessagesOut()
    {
        return 0;
    }

    @Override
    public long getBytesIn()
    {
        return 0;
    }

    @Override
    public long getBytesOut()
    {
        return 0;
    }

    @Override
    public long getCreatedTimeStamp()
    {
        return _endpoint.getCreatedTimeStamp();
    }

    public void handle() throws IOException
    {
        NestedRequestResponse nestedRequestResponse = _endpoint.getNestedRequestResponse();
        nestedRequestResponse.startAsync();

        // TODO: We want to recycle the channel instead of creating a new one every time.
        // TODO: Implement the NestedChannel with the top layers HttpChannel.
        NestedTransport transport = new NestedTransport(_endpoint);
        HttpChannel httpChannel = new NestedChannel(_connector, _connector.getHttpConfiguration(), _endpoint, transport);

        Request request = httpChannel.getRequest();
        request.setAsyncSupported(false, null); // TODO: Is this necessary?
        request.setSecure(nestedRequestResponse.isSecure());

        // Collect the request Headers.
        HttpFields.Mutable httpFields = HttpFields.build();
        Enumeration<String> headerNames = nestedRequestResponse.getHeaderNames();
        while (headerNames.hasMoreElements())
        {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValues = nestedRequestResponse.getHeaders(headerName);
            while (headerValues.hasMoreElements())
            {
                String headerValue = headerValues.nextElement();
                httpFields.add(headerName, headerValue);
            }
        }

        // Generate the Request MetaData.
        String method = nestedRequestResponse.getMethod();
        HttpURI httpURI = HttpURI.build(nestedRequestResponse.getRequestURI());
        HttpVersion httpVersion = HttpVersion.fromString(nestedRequestResponse.getProtocol());
        long contentLength = nestedRequestResponse.getContentLengthLong();
        MetaData.Request requestMetadata = new MetaData.Request(method, httpURI, httpVersion, httpFields, contentLength);
        httpChannel.onRequest(requestMetadata);
        httpChannel.onContentComplete();

        _connector.getExecutor().execute(() ->
        {
            try
            {
                httpChannel.handle();
            }
            finally
            {
                nestedRequestResponse.stopAsync();
            }
        });
    }
}
