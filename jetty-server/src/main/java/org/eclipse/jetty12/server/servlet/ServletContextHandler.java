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

import org.eclipse.jetty12.server.Channel;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;
import org.eclipse.jetty12.server.handler.ContextHandler;

public class ServletContextHandler extends ContextHandler<ServletScopedRequest>
{
    private ServletHandler _servletHandler;
    private ServletContextContext _servletContextContext;

    @Override
    protected void doStart() throws Exception
    {
        super.doStart();
        getContainedBeans(ServletHandler.class).stream().findFirst().ifPresent(sh -> _servletHandler = sh);
        _servletContextContext = new ServletContextContext(getContext(), _servletHandler);
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

        // Get a servlet request, possibly from a cached version in the channel attributes.
        // TODO there is a little bit of effort here to recycle the ServletRequest, but not the underlying jetty request.
        //      the servlet request is still a heavy weight object with state, input streams, cookie caches etc. so it is
        //      probably worth while.
        Channel channel = request.getChannel();
        ServletRequestState servletRequestState = (ServletRequestState)channel.getAttribute(ServletRequestState.class.getName());
        if (servletRequestState == null)
        {
            servletRequestState = new ServletRequestState(_servletContextContext);
            if (channel.getMetaConnection().isPersistent())
                channel.setAttribute(ServletRequestState.class.getName(), servletRequestState);
        }

        ServletScopedRequest servletScopedRequest = new ServletScopedRequest(servletRequestState, request, response, pathInContext, mappedServlet);
        servletRequestState.setServletScopedRequest(servletScopedRequest);
        return servletScopedRequest;
    }
}
