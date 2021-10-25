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

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty12.server.Content;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;
import org.eclipse.jetty12.server.Stream;

public class StatsHandler extends Handler.Wrapper<Request>
{
    private ConcurrentHashMap<String, Object> _connectionStats = new ConcurrentHashMap<>();

    @Override
    public boolean handle(Request request, Response response)
    {
        Object connectionStats = _connectionStats.computeIfAbsent(request.getMetaConnection().getId(), id ->
        {
            request.getChannel().whenConnectionComplete(x ->
            {
                // complete connections stats
                _connectionStats.remove(request.getMetaConnection().getId());
            });
            return "SomeConnectionStatsObject";
        });

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
                    // TODO count write stats here
                }

                super.send(response, last, callback, content);
            }

            @Override
            public Content readContent()
            {
                // TODO count content read
                return super.readContent();
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
            return super.handle(request, response);
        }
        finally
        {
            // TODO initial dispatch duration stats collected here.
        }
    }
}
