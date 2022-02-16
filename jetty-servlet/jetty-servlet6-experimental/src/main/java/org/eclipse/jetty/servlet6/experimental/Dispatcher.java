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
import java.util.Enumeration;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.servlet6.experimental.util.ServletOutputStreamWrapper;
import org.eclipse.jetty.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dispatcher implements RequestDispatcher
{
    private static final Logger LOG = LoggerFactory.getLogger(Dispatcher.class);

    /**
     * Dispatch include attribute names
     */
    public static final String __INCLUDE_PREFIX = "jakarta.servlet.include.";

    /**
     * Dispatch include attribute names
     */
    public static final String __FORWARD_PREFIX = "jakarta.servlet.forward.";

    private final ServletContextHandler _contextHandler;
    private final HttpURI _uri;
    private final String _pathInContext;
    private final String _named;
    private final ServletHandler.MappedServlet _mappedServlet;
    private final ServletHandler _servletHandler;

    public Dispatcher(ServletContextHandler contextHandler, HttpURI uri, String pathInContext)
    {
        _contextHandler = contextHandler;
        _uri = uri.asImmutable();
        _pathInContext = pathInContext;
        _named = null;

        _servletHandler = _contextHandler.getServletHandler();
        _mappedServlet = _servletHandler.getMappedServlet(pathInContext);
    }

    public Dispatcher(ServletContextHandler contextHandler, String name) throws IllegalStateException
    {
        _contextHandler = contextHandler;
        _uri = null;
        _pathInContext = null;
        _named = name;

        _servletHandler = _contextHandler.getServletHandler();
        _mappedServlet = _servletHandler.getMappedServlet(name);
    }

    @Override
    public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;

        _mappedServlet.handle(_servletHandler, new ForwardRequest(httpRequest), httpResponse);
    }

    @Override
    public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;

        _mappedServlet.handle(_servletHandler, new IncludeRequest(httpRequest), new IncludeResponse(httpResponse));
    }

    private class ForwardRequest extends HttpServletRequestWrapper
    {
        private final HttpServletRequest _httpServletRequest;

        public ForwardRequest(HttpServletRequest httpRequest)
        {
            super(httpRequest);
            _httpServletRequest = httpRequest;
        }

        @Override
        public String getPathInfo()
        {
            return _mappedServlet.getServletPathMapping(URIUtil.addPaths(getServletPath(), getPathInfo())).getPathInfo();
        }

        @Override
        public String getServletPath()
        {
            return _mappedServlet.getServletPathMapping(URIUtil.addPaths(getServletPath(), getPathInfo())).getServletPath();
        }

        @Override
        public Object getAttribute(String name)
        {
            switch (name)
            {
                case RequestDispatcher.FORWARD_REQUEST_URI:
                    return _httpServletRequest.getRequestURI();
                case RequestDispatcher.FORWARD_SERVLET_PATH:
                    return _httpServletRequest.getServletPath();
                case RequestDispatcher.FORWARD_PATH_INFO:
                    return _httpServletRequest.getPathInfo();

                // TODO etc.
                default:
                    break;
            }
            return super.getAttribute(name);
        }
    }

    private class IncludeRequest extends HttpServletRequestWrapper
    {
        public IncludeRequest(HttpServletRequest request)
        {
            super(request);
        }

        @Override
        public Object getAttribute(String name)
        {
            String pathInContext = URIUtil.addPaths(getServletPath(), getPathInfo());
            switch (name)
            {
                case RequestDispatcher.INCLUDE_MAPPING:
                    return _mappedServlet.getServletPathMapping(pathInContext);

                case RequestDispatcher.INCLUDE_SERVLET_PATH:
                    return _mappedServlet.getServletPathMapping(pathInContext).getServletPath();

                case RequestDispatcher.INCLUDE_PATH_INFO:
                    return _mappedServlet.getServletPathMapping(pathInContext).getPathInfo();

                default:
                    // TODO etc.
            }
            return super.getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames()
        {
            // TODO get the enumeration, add to a new set, add in extra INCLUDE params and return enumeration
            return super.getAttributeNames();
        }
    }

    private static class IncludeResponse extends HttpServletResponseWrapper
    {
        public IncludeResponse(HttpServletResponse response)
        {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException
        {
            return new ServletOutputStreamWrapper(getResponse().getOutputStream())
            {
                @Override
                public void close() throws IOException
                {
                    // NOOP for include.
                }
            };
        }

        @Override
        public void setCharacterEncoding(String charset)
        {
            // NOOP for include.
        }

        @Override
        public void setContentLength(int len)
        {
            // NOOP for include.
        }

        @Override
        public void setContentLengthLong(long len)
        {
            // NOOP for include.
        }

        @Override
        public void setContentType(String type)
        {
            // NOOP for include.
        }

        @Override
        public void reset()
        {
            // TODO can include do this?
            super.reset();
        }

        @Override
        public void resetBuffer()
        {
            // TODO can include do this?
            super.resetBuffer();
        }

        @Override
        public void setDateHeader(String name, long date)
        {
            // NOOP for include.
        }

        @Override
        public void addDateHeader(String name, long date)
        {
            // NOOP for include.
        }

        @Override
        public void setHeader(String name, String value)
        {
            // NOOP for include.
        }

        @Override
        public void addHeader(String name, String value)
        {
            // NOOP for include.
        }

        @Override
        public void setIntHeader(String name, int value)
        {
            // NOOP for include.
        }

        @Override
        public void addIntHeader(String name, int value)
        {
            // NOOP for include.
        }

        @Override
        public void setStatus(int sc)
        {
            // NOOP for include.
        }
    }

    @Override
    public String toString()
    {
        return String.format("Dispatcher@0x%x{%s,%s}", hashCode(), _named, _uri);
    }
}
