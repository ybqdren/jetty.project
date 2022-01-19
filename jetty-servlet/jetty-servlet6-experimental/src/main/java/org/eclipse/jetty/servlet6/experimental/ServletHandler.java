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

import java.io.IOException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.pathmap.MappedResource;
import org.eclipse.jetty.http.pathmap.PathMappings;
import org.eclipse.jetty.http.pathmap.ServletPathSpec;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

public class ServletHandler extends Handler.Abstract
{
    private final PathMappings<MappedServlet> _servletPathMap = new PathMappings<>();

    public void addServletWithMapping(HttpServlet servlet, String pathSpec)
    {
        _servletPathMap.put(pathSpec, new MappedServletImpl(servlet, pathSpec));
    }

    public void addServletWithMapping(Class<? extends HttpServlet> servlet, String pathSpec)
    {
        _servletPathMap.put(pathSpec, new MappedServletImpl(createInstance(servlet), pathSpec));
    }

    public ServletContext getServletContext()
    {
        return null;
    }

    public interface MappedServlet
    {
        ServletPathMapping getServletPathMapping(String pathInContext);

        void handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
    }

    private static class MappedServletImpl implements MappedServlet
    {
        private final HttpServlet _httpServlet;
        private final ServletPathSpec _servletPathSpec;

        public MappedServletImpl(HttpServlet httpServlet, String pathSpec)
        {
            _httpServlet = httpServlet;
            _servletPathSpec = new ServletPathSpec(pathSpec);
        }

        @Override
        public ServletPathMapping getServletPathMapping(String pathInContext)
        {
            return new ServletPathMapping(_servletPathSpec, _httpServlet.getServletName(), pathInContext);
        }

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
        {
            _httpServlet.service(request, response);
        }
    }

    public MappedServlet findMapping(String pathInContext)
    {
        MappedResource<MappedServlet> match = _servletPathMap.getMatch(pathInContext);
        if (match == null)
            return null;
        return match.getResource();
    }

    @Override
    public boolean handle(Request request, Response response)
    {
        ServletScopedRequest servletRequest = request.as(ServletScopedRequest.class);
        MappedServlet mappedServlet = servletRequest.getMappedServlet();
        if (mappedServlet == null)
            return false; // TODO or 404 or ISE?

        servletRequest.getServletRequestState().handle();
        return true;
    }

    private <T> T createInstance(Class<T> clazz)
    {
        // TODO: Create with ServletContext for decoration.
        try
        {
            return clazz.getDeclaredConstructor().newInstance();
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException(e);
        }
    }
}
