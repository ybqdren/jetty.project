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

package org.eclipse.jetty12.server.servlet;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Response;
import org.eclipse.jetty12.server.Stream;

public class SessionHandler extends Handler.Wrapper<ServletScopedRequest>
{
    @Override
    public boolean handle(ServletScopedRequest request, Response response)
    {
        ServletScopedRequest.MutableHttpServletRequest servletRequest = request.getMutableHttpServletRequest();

        // TODO servletRequest can be mutable, so we can add session stuff to it
        servletRequest.setSessionManager(this);
        servletRequest.setSession(null);

        request.getChannel().onStreamEvent(s ->
            new Stream.Wrapper(s)
            {
                @Override
                public void send(MetaData.Response response, boolean last, Callback callback, ByteBuffer... content)
                {
                    if (response != null)
                    {
                        // commit session to data store
                    }
                }

                @Override
                public void succeeded()
                {
                    super.succeeded();
                    // exit session
                }

                @Override
                public void failed(Throwable x)
                {
                    super.failed(x);
                    // exit session
                }
            });

        return super.handle(request, response);
    }
}
