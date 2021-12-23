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
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.URIUtil;

public class ServletDispatcher implements RequestDispatcher
{
    private final ContextHandler.Context _context;
    private final ServletHandler _servletHandler;
    private final ServletHandler.MappedServlet _mappedServlet;

    public ServletDispatcher(ContextHandler.Context context, ServletHandler servletHandler, ServletHandler.MappedServlet mapping)
    {
        _context = context;
        _servletHandler = servletHandler;
        _mappedServlet = mapping;
    }

    @Override
    public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        // TODO stuff about parameters

        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;

        _mappedServlet.handle(new ForwardRequest(httpRequest), httpResponse);

        if (!httpRequest.isAsyncStarted())
        {
            try
            {
                httpResponse.getOutputStream().close();
            }
            catch (IllegalStateException e)
            {
                httpResponse.getWriter().close();
            }
        }
    }

    @Override
    public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        // TODO stuff about parameters
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;

        _mappedServlet.handle(new IncludeRequest(httpRequest), new IncludeResponse(httpResponse));
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
            // TODO handle writers etc.
            final ServletOutputStream out = getResponse().getOutputStream();

            return new ServletOutputStream()
            {
                @Override
                public void close() throws IOException
                {
                    // noop for include
                }

                @Override
                public boolean isReady()
                {
                    return out.isReady();
                }

                @Override
                public void setWriteListener(WriteListener writeListener)
                {
                    out.setWriteListener(writeListener);
                }

                @Override
                public void write(int b) throws IOException
                {
                    out.write(b);
                }
            };
        }

        @Override
        public void setCharacterEncoding(String charset)
        {
            // noop for include
        }

        @Override
        public void setContentLength(int len)
        {
            // noop for include
        }

        @Override
        public void setContentLengthLong(long len)
        {
            // noop for include
        }

        @Override
        public void setContentType(String type)
        {
            // noop for include
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
            // noop for include
        }

        @Override
        public void addDateHeader(String name, long date)
        {
            // noop for include
        }

        @Override
        public void setHeader(String name, String value)
        {
            // noop for include
        }

        @Override
        public void addHeader(String name, String value)
        {
            // noop for include
        }

        @Override
        public void setIntHeader(String name, int value)
        {
            // noop for include
        }

        @Override
        public void addIntHeader(String name, int value)
        {
            // noop for include
        }

        @Override
        public void setStatus(int sc)
        {
            // noop for include
        }
    }
}
