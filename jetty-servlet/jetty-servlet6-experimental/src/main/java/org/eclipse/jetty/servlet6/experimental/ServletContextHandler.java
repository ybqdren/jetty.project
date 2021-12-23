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

package org.eclipse.jetty.servlet6.experimental;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ContextHandler;

public class ServletContextHandler extends ContextHandler
{
    public static ServletContext getServletContext()
    {
        return getServletContext(ContextHandler.getCurrentContext());
    }

    public static ServletContext getServletContext(ContextHandler.Context context)
    {
        if (context instanceof ServletContextHandler.Context)
            return ((Context)context).getServletContext();
        return null;
    }

    private ServletHandler _servletHandler;
    private ServletContextContext _servletContextContext;

    public ServletContextHandler()
    {
    }

    public void addServlet(Class<? extends HttpServlet> servlet, String pathSpec)
    {
        getServletHandler().addServletWithMapping(servlet, pathSpec);
    }

    @Override
    protected void doStart() throws Exception
    {
        super.doStart();
        if (_servletHandler == null)
            _servletHandler = newServletHandler();
        setHandler(_servletHandler);

        _servletContextContext = new ServletContextContext(getContext(), _servletHandler);
    }

    @Override
    protected void doStop() throws Exception
    {
        _servletHandler = null;
    }

    @Override
    protected ContextHandler.Context newContext()
    {
        return super.newContext();
    }

    public ServletHandler getServletHandler()
    {
        if (_servletHandler == null && !isStarted())
            _servletHandler = newServletHandler();
        return _servletHandler;
    }

    protected ServletHandler newServletHandler()
    {
        ServletHandler servletHandler = getContainedBeans(ServletHandler.class).stream()
            .findFirst()
            .orElse(null);

        if (servletHandler == null)
            servletHandler = new ServletHandler();

        return servletHandler;
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
        HttpChannel channel = request.getChannel();
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

    public class Context extends ContextHandler.Context
    {
        public ServletContextContext getServletContext()
        {
            return _servletContextContext;
        }
    }
}
