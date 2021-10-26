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

package org.eclipse.jetty12.server.handler;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty12.server.Content;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;
import org.eclipse.jetty12.server.Stream;

public class RequestStatsHandler extends Handler.Wrapper<Request>
{
    private ConcurrentHashMap<String, Object> _connectionStats = new ConcurrentHashMap<>();

    @Override
    public boolean handle(Request request, Response response)
    {
        Object connectionStats = _connectionStats.computeIfAbsent(request.getConnectionMetaData().getId(), id ->
        {
            request.getChannel().whenConnectionComplete(x ->
            {
                // complete connections stats
                _connectionStats.remove(request.getConnectionMetaData().getId());
            });
            return "SomeConnectionStatsObject";
        });

        final LongAdder bytesRead = new LongAdder();
        final LongAdder bytesWritten = new LongAdder();

        request.getChannel().onStreamEvent(s -> new Stream.Wrapper(s)
        {
            @Override
            public void send(MetaData.Response response, boolean last, Callback callback, ByteBuffer... content)
            {
                if (response != null)
                {
                    // TODO status stats collected here.
                }

                for (ByteBuffer b : content)
                {
                    bytesWritten.add(b.remaining());
                }

                super.send(response, last, callback, content);
            }

            @Override
            public Content readContent()
            {
                Content content =  super.readContent();
                bytesRead.add(content.remaining());
                return content;
            }

            @Override
            public void succeeded()
            {
                super.succeeded();
                // TODO request duration stats collection
            }

            @Override
            public void failed(Throwable x)
            {
                // TODO abort stats collection
                super.failed(x);
            }
        });

        try
        {
            return super.handle(new Request.Wrapper(request)
            {
                // TODO make this wrapper optional. Only needed if requestLog asks for these attributes.
                @Override
                public Object getAttribute(String name)
                {
                    // return hidden attributes for requestLog
                    switch (name)
                    {
                        case "o.e.j.s.h.StatsHandler.bytesRead":
                            return bytesRead;
                        case "o.e.j.s.h.StatsHandler.bytesWritten":
                            return bytesWritten;
                        default:
                            return super.getAttribute(name);
                    }
                }
            }, response);
        }
        finally
        {
            // TODO initial dispatch duration stats collected here.
        }
    }
}
