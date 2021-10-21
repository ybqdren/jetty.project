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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty12.server.MetaConnection;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.handler.ContextHandler;
import org.eclipse.jetty12.server.handler.ScopedRequest;

public class ServletScopedRequest extends ScopedRequest implements Runnable
{
    private final State _state = new State();
    private final ServletContext _servletContext;
    private final MappedHttpServletRequest _httpServletRequest;
    private final HttpServletResponse _httpServletResponse;
    private ServletHandler.MappedServlet _mappedServlet;

    protected ServletScopedRequest(ContextHandler.Context context,
                                   ServletContext servletContext,
                                   Request wrapped,
                                   String pathInContext,
                                   ServletHandler.MappedServlet mappedServlet)
    {
        super(context, wrapped, pathInContext);
        _servletContext = servletContext;
        _httpServletRequest = new MappedHttpServletRequest();
        _httpServletResponse = null;
        _mappedServlet = mappedServlet;
    }

    void remap(ServletHandler.MappedServlet mappedServlet)
    {
        _mappedServlet = mappedServlet;
    }

    public MappedHttpServletRequest getHttpServletRequest()
    {
        return _httpServletRequest;
    }

    public HttpServletResponse getHttpServletResponse()
    {
        return _httpServletResponse;
    }

    public ServletHandler.MappedServlet getMappedServlet()
    {
        return _mappedServlet;
    }

    public static ServletScopedRequest getRequest(HttpServletRequest httpServletRequest)
    {
        while (httpServletRequest != null)
        {
            if (httpServletRequest instanceof MappedHttpServletRequest)
                return ((MappedHttpServletRequest)httpServletRequest).getServletScopedRequest();
            if (httpServletRequest instanceof HttpServletRequestWrapper)
                httpServletRequest = (HttpServletRequest)((HttpServletRequestWrapper)httpServletRequest).getRequest();
            else
                break;
        }
        return null;
    }

    @Override
    public void run()
    {
        handle();
    }

    public void handle()
    {
        // implement the state machine from HttpChannelState and HttpChannel
        // Note that sendError may already have been called before we are handling for the first time.

        HttpChannelState.Action action = _state.handling();
        loop: while (true)
        {
            try
            {
                switch (action)
                {
                    case COMPLETE:
                        succeeded();
                        break loop;

                    case WAIT:
                        break;

                    case DISPATCH:
                        _mappedServlet.handle(_httpServletRequest, _httpServletResponse);
                        break;

                    // TODO etc.
                    default:
                        break;
                }
            }
            catch (Throwable failure)
            {
                // TODO
            }

            action = _state.unhandle();
        }
    }

    private static class State extends HttpChannelState
    {
        public State()
        {
            super(null);
        }

        @Override
        public Action handling()
        {
            return super.handling();
        }

        @Override
        protected Action unhandle()
        {
            return super.unhandle();
        }
    }

    class MappedHttpServletRequest implements HttpServletRequest
    {

        public void setSessionManager(SessionHandler sessionHandler)
        {
            // TODO
        }

        public void setSession(Object o)
        {
            // TODO
        }

        ServletScopedRequest getServletScopedRequest()
        {
            return ServletScopedRequest.this;
        }

        @Override
        public String getRequestId()
        {
            return getId();
        }

        @Override
        public String getProtocolRequestId()
        {
            return getChannel().getStream().getId();
        }

        @Override
        public ServletConnection getServletConnection()
        {
            // TODO cache the results
            final MetaConnection metaConnection = getMetaConnection();
            return new ServletConnection()
            {
                @Override
                public String getConnectionId()
                {
                    return metaConnection.getId();
                }

                @Override
                public String getProtocol()
                {
                    return metaConnection.getProtocol();
                }

                @Override
                public String getProtocolConnectionId()
                {
                    return metaConnection.getConnection().toString(); // TODO getId
                }

                @Override
                public boolean isSecure()
                {
                    return metaConnection.isSecure();
                }
            };
        }

        @Override
        public String getAuthType()
        {
            return null;
        }

        @Override
        public Cookie[] getCookies()
        {
            return new Cookie[0];
        }

        @Override
        public long getDateHeader(String name)
        {
            return 0;
        }

        @Override
        public String getHeader(String name)
        {
            return ServletScopedRequest.this.getHeaders().get(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name)
        {
            return ServletScopedRequest.this.getHeaders().getValues(name);
        }

        @Override
        public Enumeration<String> getHeaderNames()
        {
            return ServletScopedRequest.this.getHeaders().getFieldNames();
        }

        @Override
        public int getIntHeader(String name)
        {
            return (int)ServletScopedRequest.this.getHeaders().getLongField(name);
        }

        @Override
        public String getMethod()
        {
            return getMethod();
        }

        @Override
        public String getPathInfo()
        {
            return _mappedServlet.getServletPathMapping().getPathInfo();
        }

        @Override
        public String getPathTranslated()
        {
            return null;
        }

        @Override
        public String getContextPath()
        {
            return ServletScopedRequest.this.getContext().getContextPath();
        }

        @Override
        public String getQueryString()
        {
            return ServletScopedRequest.this.getURI().getQuery();
        }

        @Override
        public String getRemoteUser()
        {
            return null;
        }

        @Override
        public boolean isUserInRole(String role)
        {
            return false;
        }

        @Override
        public Principal getUserPrincipal()
        {
            return null;
        }

        @Override
        public String getRequestedSessionId()
        {
            return null;
        }

        @Override
        public String getRequestURI()
        {
            return ServletScopedRequest.this.getURI().toString();
        }

        @Override
        public StringBuffer getRequestURL()
        {
            return null;
        }

        @Override
        public String getServletPath()
        {
            return _mappedServlet.getServletPathMapping().getServletPath();
        }

        @Override
        public HttpSession getSession(boolean create)
        {
            return null;
        }

        @Override
        public HttpSession getSession()
        {
            return null;
        }

        @Override
        public String changeSessionId()
        {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid()
        {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie()
        {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL()
        {
            return false;
        }

        @Override
        public boolean authenticate(HttpServletResponse response) throws IOException, ServletException
        {
            return false;
        }

        @Override
        public void login(String username, String password) throws ServletException
        {

        }

        @Override
        public void logout() throws ServletException
        {

        }

        @Override
        public Collection<Part> getParts() throws IOException, ServletException
        {
            return null;
        }

        @Override
        public Part getPart(String name) throws IOException, ServletException
        {
            return null;
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException
        {
            return null;
        }

        @Override
        public Object getAttribute(String name)
        {
            return ServletScopedRequest.this.getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames()
        {
            return ServletScopedRequest.this.getAttributeNames();
        }

        @Override
        public String getCharacterEncoding()
        {
            return null;
        }

        @Override
        public void setCharacterEncoding(String env) throws UnsupportedEncodingException
        {

        }

        @Override
        public int getContentLength()
        {
            return 0;
        }

        @Override
        public long getContentLengthLong()
        {
            return 0;
        }

        @Override
        public String getContentType()
        {
            return null;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException
        {
            return null;
        }

        @Override
        public String getParameter(String name)
        {
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames()
        {
            return null;
        }

        @Override
        public String[] getParameterValues(String name)
        {
            return new String[0];
        }

        @Override
        public Map<String, String[]> getParameterMap()
        {
            return null;
        }

        @Override
        public String getProtocol()
        {
            return null;
        }

        @Override
        public String getScheme()
        {
            return null;
        }

        @Override
        public String getServerName()
        {
            return null;
        }

        @Override
        public int getServerPort()
        {
            return 0;
        }

        @Override
        public BufferedReader getReader() throws IOException
        {
            return null;
        }

        @Override
        public String getRemoteAddr()
        {
            return null;
        }

        @Override
        public String getRemoteHost()
        {
            return null;
        }

        @Override
        public void setAttribute(String name, Object o)
        {

        }

        @Override
        public void removeAttribute(String name)
        {

        }

        @Override
        public Locale getLocale()
        {
            return null;
        }

        @Override
        public Enumeration<Locale> getLocales()
        {
            return null;
        }

        @Override
        public boolean isSecure()
        {
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path)
        {
            return null;
        }

        @Override
        public int getRemotePort()
        {
            return 0;
        }

        @Override
        public String getLocalName()
        {
            return null;
        }

        @Override
        public String getLocalAddr()
        {
            return null;
        }

        @Override
        public int getLocalPort()
        {
            return 0;
        }

        @Override
        public ServletContext getServletContext()
        {
            return _servletContext;
        }

        @Override
        public AsyncContext startAsync() throws IllegalStateException
        {
            return null;
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException
        {
            return null;
        }

        @Override
        public boolean isAsyncStarted()
        {
            return false;
        }

        @Override
        public boolean isAsyncSupported()
        {
            return false;
        }

        @Override
        public AsyncContext getAsyncContext()
        {
            return null;
        }

        @Override
        public DispatcherType getDispatcherType()
        {
            return null;
        }
    }
}
