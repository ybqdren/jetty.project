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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextRequest;

public class ServletScopedRequest extends ContextRequest implements Runnable
{
    public static final String __MULTIPART_CONFIG_ELEMENT = "org.eclipse.jetty.multipartConfig";

    ServletChannel _servletChannel;
    final MutableHttpServletRequest _httpServletRequest;
    final ServletHandler.MappedServlet _mappedServlet;
    final ServletScopedResponse _response;
    final HttpOutput _httpOutput;
    final HttpInput _httpInput;
    boolean _newContext;
    private UserIdentity.Scope _scope;

    final List<ServletRequestAttributeListener> _requestAttributeListeners = new ArrayList<>();

    protected ServletScopedRequest(
        ServletContextHandler.ServletContextContext servletContextContext,
        ServletChannel servletChannel,
        Request request,
        Response response,
        String pathInContext,
        ServletHandler.MappedServlet mappedServlet)
    {
        super(servletContextContext.getContextHandler(), request, pathInContext);
        _servletChannel = servletChannel;
        _httpServletRequest = new MutableHttpServletRequest();
        _mappedServlet = mappedServlet;
        _httpOutput = new HttpOutput(response);
        _httpInput = new HttpInput(this);
        _response = new ServletScopedResponse(_servletChannel, response, _httpOutput);
    }

    public ServletRequestState getState()
    {
        return _servletChannel.getState();
    }

    @Override
    public ServletScopedResponse getResponse()
    {
        return _response;
    }

    @Override
    public ServletContextHandler.Context getContext()
    {
        return (ServletContextHandler.Context)super.getContext();
    }

    public HttpInput getHttpInput()
    {
        return _httpInput;
    }

    public HttpOutput getHttpOutput()
    {
        return _httpOutput;
    }

    public void errorClose()
    {
        // Make the response immutable and soft close the output.
    }

    // TODO: should be on Request instead?
    private long _timeStamp = 0;
    public long getTimeStamp()
    {
        return 0;
    }

    public boolean isHead()
    {
        return HttpMethod.HEAD.is(getMethod());
    }

    public void setTimeStamp(long timeStamp)
    {
        _timeStamp = timeStamp;
    }

    @Override
    public Object getAttribute(String name)
    {
        // return hidden attributes for request logging
        switch (name)
        {
            case "o.e.j.s.s.ServletScopedRequest.request":
                return _httpServletRequest;
            case "o.e.j.s.s.ServletScopedRequest.response":
                return _response.getHttpServletResponse();
            case "o.e.j.s.s.ServletScopedRequest.servlet":
                return _mappedServlet.getServletPathMapping(getPath()).getServletName();
            case "o.e.j.s.s.ServletScopedRequest.url-pattern":
                return _mappedServlet.getServletPathMapping(getPath()).getPattern();
            default:
                return super.getAttribute(name);
        }
    }

    /**
     * @return The current {@link ContextHandler.Context context} used for this error handling for this request.  If the request is asynchronous,
     * then it is the context that called async. Otherwise it is the last non-null context passed to #setContext
     */
    public ContextHandler.Context getErrorContext()
    {
        return _servletChannel.getContext();
    }

    public boolean takeNewContext()
    {
        boolean nc = _newContext;
        _newContext = false;
        return nc;
    }

    ServletChannel getServletRequestState()
    {
        return _servletChannel;
    }

    public HttpServletRequest getHttpServletRequest()
    {
        return _httpServletRequest;
    }

    public MutableHttpServletRequest getMutableHttpServletRequest()
    {
        return _httpServletRequest;
    }

    public HttpServletResponse getHttpServletResponse()
    {
        return _response.getHttpServletResponse();
    }

    public ServletHandler.MappedServlet getMappedServlet()
    {
        return _mappedServlet;
    }

    public static ServletScopedRequest getRequest(HttpServletRequest httpServletRequest)
    {
        while (httpServletRequest != null)
        {
            if (httpServletRequest instanceof ServletChannel)
                return ((ServletChannel)httpServletRequest).getRequest();
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
        _servletChannel.handle();
    }

    public String getServletName()
    {
        if (_scope != null)
            return _scope.getName();
        return null;
    }

    Runnable onContentAvailable()
    {
        // TODO not sure onReadReady is right method or at least could be renamed.
        return getState().onReadReady() ? this : null;
    }

    public void addEventListener(final EventListener listener)
    {
        if (listener instanceof ServletRequestAttributeListener)
            _requestAttributeListeners.add((ServletRequestAttributeListener)listener);
        if (listener instanceof AsyncListener)
            throw new IllegalArgumentException(listener.getClass().toString());
    }

    public void removeEventListener(final EventListener listener)
    {
        _requestAttributeListeners.remove(listener);
    }

    public class MutableHttpServletRequest implements HttpServletRequest
    {
        private AsyncContextState _async;
        
        public Request getRequest()
        {
            return ServletScopedRequest.this;
        }

        @Override
        public String getRequestId()
        {
            return ServletScopedRequest.this.getId();
        }

        @Override
        public String getProtocolRequestId()
        {
            return ServletScopedRequest.this.getHttpChannel().getHttpStream().getId();
        }

        @Override
        public ServletConnection getServletConnection()
        {
            // TODO cache the results
            final ConnectionMetaData connectionMetaData = ServletScopedRequest.this.getConnectionMetaData();
            return new ServletConnection()
            {
                @Override
                public String getConnectionId()
                {
                    return connectionMetaData.getId();
                }

                @Override
                public String getProtocol()
                {
                    return connectionMetaData.getProtocol();
                }

                @Override
                public String getProtocolConnectionId()
                {
                    return connectionMetaData.getConnection().toString(); // TODO getId
                }

                @Override
                public boolean isSecure()
                {
                    return connectionMetaData.isSecure();
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
            return getRequest().getMethod();
        }

        @Override
        public String getPathInfo()
        {
            return ServletScopedRequest.this._mappedServlet.getServletPathMapping(getRequest().getPath()).getPathInfo();
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
            return ServletScopedRequest.this.getHttpURI().getQuery();
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
            return ServletScopedRequest.this.getHttpURI().toString();
        }

        @Override
        public StringBuffer getRequestURL()
        {
            return null;
        }

        @Override
        public String getServletPath()
        {
            return ServletScopedRequest.this._mappedServlet.getServletPathMapping(getRequest().getPath()).getServletPath();
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
            return Collections.enumeration(ServletScopedRequest.this.getAttributeNames());
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
            return _httpInput;
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
            return _servletChannel.getServletContext();
        }

        @Override
        public AsyncContext startAsync() throws IllegalStateException
        {
            ServletRequestState state = getState();
            if (_async == null)
                _async = new AsyncContextState(state);
            // TODO adapt to new context and base Request
            AsyncContextEvent event = new AsyncContextEvent(null, _async, state, ServletScopedRequest.this, this, _response.getHttpServletResponse());
            state.startAsync(event);
            return _async;
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException
        {
            ServletRequestState state = getState();
            if (_async == null)
                _async = new AsyncContextState(state);
            // TODO adapt to new context and base Request
            AsyncContextEvent event = new AsyncContextEvent(null, _async, state, null, servletRequest, servletResponse);
            state.startAsync(event);
            return _async;
        }

        @Override
        public boolean isAsyncStarted()
        {
            return getState().isAsyncStarted();
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
            return DispatcherType.REQUEST;
        }
    }
}
