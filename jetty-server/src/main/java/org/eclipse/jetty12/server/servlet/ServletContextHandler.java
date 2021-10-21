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

import jakarta.servlet.ServletContext;
import org.eclipse.jetty12.server.Channel;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;
import org.eclipse.jetty12.server.handler.ContextHandler;

public class ServletContextHandler extends ContextHandler<ServletScopedRequest>
{
    private ServletHandler _servletHandler;
    private ServletContext _servletContext;

    @Override
    protected void doStart() throws Exception
    {
        super.doStart();
        getContainedBeans(ServletHandler.class).stream().findFirst().ifPresent(sh -> _servletHandler = sh);
        _servletContext = new ServletContextContext(getContext(), _servletHandler);
    }

    @Override
    protected void doStop() throws Exception
    {
        _servletHandler = null;
    }

    public ServletHandler getServletHandler()
    {
        return _servletHandler;
    }

    @Override
    protected ServletScopedRequest wrap(Request request, Response response, String pathInContext)
    {
        ServletHandler.MappedServlet mappedServlet = _servletHandler.findMapping(pathInContext);
        if (mappedServlet == null)
            return null;

        // Get a servlet request wrapper, possibly from a cached version in the channel attributes.
        Channel channel = request.getChannel();
        ServletScopedRequest servletScopedRequest = (ServletScopedRequest)channel.getAttribute(ServletScopedRequest.class.getName());
        if (servletScopedRequest == null)
        {
            servletScopedRequest = new ServletScopedRequest(getContext(), _servletContext, request, pathInContext, mappedServlet);
            if (channel.getMetaConnection().isPersistent())
                channel.setAttribute(ServletScopedRequest.class.getName(), servletScopedRequest);
        }
        else
            servletScopedRequest.remap(mappedServlet);

        return servletScopedRequest;
    }
}
