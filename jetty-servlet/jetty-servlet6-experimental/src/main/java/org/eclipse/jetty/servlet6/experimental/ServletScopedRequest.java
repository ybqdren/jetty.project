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
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextRequest;
import org.eclipse.jetty.util.HostPort;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServletScopedRequest extends ContextRequest implements Runnable
{
    public static final String __MULTIPART_CONFIG_ELEMENT = "org.eclipse.jetty.multipartConfig";

    private static final Logger LOG = LoggerFactory.getLogger(ServletScopedRequest.class);
    private static final Collection<Locale> __defaultLocale = Collections.singleton(Locale.getDefault());
    private static final int INPUT_NONE = 0;
    private static final int INPUT_STREAM = 1;
    private static final int INPUT_READER = 2;

    private static final MultiMap<String> NO_PARAMS = new MultiMap<>();
    private static final MultiMap<String> BAD_PARAMS = new MultiMap<>();

    ServletChannel _servletChannel;
    final MutableHttpServletRequest _httpServletRequest;
    final ServletHandler.MappedServlet _mappedServlet;
    final ServletScopedResponse _response;
    final HttpInput _httpInput;
    final String _pathInContext;
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
        _httpInput = new HttpInput(_servletChannel);
        _response = new ServletScopedResponse(_servletChannel, response);
        _pathInContext = pathInContext;
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
        return _response.getHttpOutput();
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
        private String _characterEncoding;
        private int _inputState = INPUT_NONE;
        private BufferedReader _reader;
        private String _readerEncoding;
        private String _contentType;

        public Request getRequest()
        {
            return ServletScopedRequest.this;
        }

        public HttpFields getFields()
        {
            return ServletScopedRequest.this.getHeaders();
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
            // TODO
            return null;
        }

        @Override
        public Cookie[] getCookies()
        {
            // TODO
            return new Cookie[0];
        }

        @Override
        public long getDateHeader(String name)
        {
            HttpFields fields = getFields();
            return fields == null ? -1 : fields.getDateField(name);
        }

        @Override
        public String getHeader(String name)
        {
            return getFields().get(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name)
        {
            return getFields().getValues(name);
        }

        @Override
        public Enumeration<String> getHeaderNames()
        {
            return getFields().getFieldNames();
        }

        @Override
        public int getIntHeader(String name)
        {
            HttpFields fields = getFields();
            return fields == null ? -1 : (int)fields.getLongField(name);
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
            String pathInfo = getPathInfo();
            if (pathInfo == null || getContext() == null)
                return null;
            return getContext().getServletContext().getRealPath(pathInfo);
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
            Principal p = getUserPrincipal();
            if (p == null)
                return null;
            return p.getName();
        }

        @Override
        public boolean isUserInRole(String role)
        {
            // TODO
            return false;
        }

        @Override
        public Principal getUserPrincipal()
        {
            // TODO
            return null;
        }

        @Override
        public String getRequestedSessionId()
        {
            // TODO
            return null;
        }

        @Override
        public String getRequestURI()
        {
            HttpURI uri = ServletScopedRequest.this.getHttpURI();
            return uri == null ? null : uri.getPath();
        }

        @Override
        public StringBuffer getRequestURL()
        {
            return new StringBuffer(ServletScopedRequest.this.getHttpURI().asString());
        }

        @Override
        public String getServletPath()
        {
            return ServletScopedRequest.this._mappedServlet.getServletPathMapping(getRequest().getPath()).getServletPath();
        }

        @Override
        public HttpSession getSession(boolean create)
        {
            // TODO
            return null;
        }

        @Override
        public HttpSession getSession()
        {
            // TODO
            return null;
        }

        @Override
        public String changeSessionId()
        {
            // TODO
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid()
        {
            // TODO
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie()
        {
            // TODO
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL()
        {
            // TODO
            return false;
        }

        @Override
        public boolean authenticate(HttpServletResponse response) throws IOException, ServletException
        {
            // TODO
            return false;
        }

        @Override
        public void login(String username, String password) throws ServletException
        {
            // TODO
        }

        @Override
        public void logout() throws ServletException
        {
            // TODO
        }

        @Override
        public Collection<Part> getParts() throws IOException, ServletException
        {
            // TODO
            return null;
        }

        @Override
        public Part getPart(String name) throws IOException, ServletException
        {
            // TODO
            return null;
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException
        {
            // TODO
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
            if (_characterEncoding == null)
            {
                if (getContext() != null)
                    _characterEncoding = getContext().getServletContext().getRequestCharacterEncoding();

                if (_characterEncoding == null)
                {
                    String contentType = getContentType();
                    if (contentType != null)
                    {
                        MimeTypes.Type mime = MimeTypes.CACHE.get(contentType);
                        String charset = (mime == null || mime.getCharset() == null) ? MimeTypes.getCharsetFromContentType(contentType) : mime.getCharset().toString();
                        if (charset != null)
                            _characterEncoding = charset;
                    }
                }
            }
            return _characterEncoding;
        }

        @Override
        public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException
        {
            if (_inputState != INPUT_NONE)
                return;

            _characterEncoding = encoding;

            // check encoding is supported
            if (!StringUtil.isUTF8(encoding))
            {
                try
                {
                    Charset.forName(encoding);
                }
                catch (UnsupportedCharsetException e)
                {
                    throw new UnsupportedEncodingException(e.getMessage());
                }
            }
        }

        @Override
        public int getContentLength()
        {
            long contentLength = getContentLengthLong();
            if (contentLength > Integer.MAX_VALUE)
                // Per ServletRequest#getContentLength() javadoc this must return -1 for values exceeding Integer.MAX_VALUE
                return -1;
            return (int)contentLength;
        }

        @Override
        public long getContentLengthLong()
        {
            // Even thought the metadata might know the real content length,
            // we always look at the headers because the length may be changed by interceptors.
            if (getFields() == null)
                return -1;

            return getFields().getLongField(HttpHeader.CONTENT_LENGTH);
        }

        @Override
        public String getContentType()
        {
            if (_contentType == null)
                _contentType = getFields().get(HttpHeader.CONTENT_TYPE);
            return _contentType;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException
        {
            if (_inputState != INPUT_NONE && _inputState != INPUT_STREAM)
                throw new IllegalStateException("READER");
            _inputState = INPUT_STREAM;

            if (_servletChannel.isExpecting100Continue())
                _servletChannel.continue100(_httpInput.available());

            return _httpInput;
        }

        @Override
        public String getParameter(String name)
        {
            List<String> strings = ServletScopedRequest.this.extractQueryParameters().get(name);
            if (strings == null || strings.isEmpty())
                return null;
            return strings.get(0);
        }

        @Override
        public Enumeration<String> getParameterNames()
        {
            // TODO
            return null;
        }

        @Override
        public String[] getParameterValues(String name)
        {
            // TODO
            return new String[0];
        }

        @Override
        public Map<String, String[]> getParameterMap()
        {
            // TODO
            return null;
        }

        @Override
        public String getProtocol()
        {
            return ServletScopedRequest.this.getConnectionMetaData().getProtocol();
        }

        @Override
        public String getScheme()
        {
            return ServletScopedRequest.this.getHttpURI().getScheme();
        }

        @Override
        public String getServerName()
        {
            HttpURI uri = ServletScopedRequest.this.getHttpURI();
            if ((uri != null) && StringUtil.isNotBlank(uri.getAuthority()))
                return formatAddrOrHost(uri.getHost());
            else
                return findServerName();
        }

        private String formatAddrOrHost(String name)
        {
            return _servletChannel == null ? HostPort.normalizeHost(name) : _servletChannel.formatAddrOrHost(name);
        }

        private String findServerName()
        {
            if (_servletChannel != null)
            {
                HostPort serverAuthority = _servletChannel.getServerAuthority();
                if (serverAuthority != null)
                    return formatAddrOrHost(serverAuthority.getHost());
            }

            // Return host from connection
            String name = getLocalName();
            if (name != null)
                return formatAddrOrHost(name);

            return ""; // not allowed to be null
        }

        @Override
        public int getServerPort()
        {
            int port = -1;

            HttpURI uri = ServletScopedRequest.this.getHttpURI();
            if ((uri != null) && StringUtil.isNotBlank(uri.getAuthority()))
                port = uri.getPort();
            else
                port = findServerPort();

            // If no port specified, return the default port for the scheme
            if (port <= 0)
                return HttpScheme.getDefaultPort(getScheme());

            // return a specific port
            return port;
        }

        private int findServerPort()
        {
            if (_servletChannel != null)
            {
                HostPort serverAuthority = _servletChannel.getServerAuthority();
                if (serverAuthority != null)
                    return serverAuthority.getPort();
            }

            // Return host from connection
            return getLocalPort();
        }

        @Override
        public BufferedReader getReader() throws IOException
        {
            if (_inputState != INPUT_NONE && _inputState != INPUT_READER)
                throw new IllegalStateException("STREAMED");

            if (_inputState == INPUT_READER)
                return _reader;

            String encoding = getCharacterEncoding();
            if (encoding == null)
                encoding = StringUtil.__ISO_8859_1;

            if (_reader == null || !encoding.equalsIgnoreCase(_readerEncoding))
            {
                final ServletInputStream in = getInputStream();
                _readerEncoding = encoding;
                _reader = new BufferedReader(new InputStreamReader(in, encoding))
                {
                    @Override
                    public void close() throws IOException
                    {
                        in.close();
                    }
                };
            }
            _inputState = INPUT_READER;
            return _reader;
        }

        @Override
        public String getRemoteAddr()
        {
            return ServletScopedRequest.this.getRemoteAddr();
        }

        @Override
        public String getRemoteHost()
        {
            // TODO: review.
            return ServletScopedRequest.this.getRemoteAddr();
        }

        @Override
        public void setAttribute(String name, Object o)
        {
            ServletScopedRequest.this.setAttribute(name, o);
        }

        @Override
        public void removeAttribute(String name)
        {
            ServletScopedRequest.this.removeAttribute(name);
        }

        @Override
        public Locale getLocale()
        {
            HttpFields fields = getFields();
            if (fields == null)
                return Locale.getDefault();

            List<String> acceptable = fields.getQualityCSV(HttpHeader.ACCEPT_LANGUAGE);

            // handle no locale
            if (acceptable.isEmpty())
                return Locale.getDefault();

            String language = acceptable.get(0);
            language = HttpField.stripParameters(language);
            String country = "";
            int dash = language.indexOf('-');
            if (dash > -1)
            {
                country = language.substring(dash + 1).trim();
                language = language.substring(0, dash).trim();
            }
            return new Locale(language, country);
        }

        @Override
        public Enumeration<Locale> getLocales()
        {
            HttpFields fields = getFields();
            if (fields == null)
                return Collections.enumeration(__defaultLocale);

            List<String> acceptable = fields.getQualityCSV(HttpHeader.ACCEPT_LANGUAGE);

            // handle no locale
            if (acceptable.isEmpty())
                return Collections.enumeration(__defaultLocale);

            List<Locale> locales = acceptable.stream().map(language ->
            {
                language = HttpField.stripParameters(language);
                String country = "";
                int dash = language.indexOf('-');
                if (dash > -1)
                {
                    country = language.substring(dash + 1).trim();
                    language = language.substring(0, dash).trim();
                }
                return new Locale(language, country);
            }).collect(Collectors.toList());

            return Collections.enumeration(locales);
        }

        @Override
        public boolean isSecure()
        {
            return ServletScopedRequest.this.getConnectionMetaData().isSecure();
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path)
        {
            ServletContextHandler.Context context = ServletScopedRequest.this.getContext();
            if (path == null || context == null)
                return null;

            // handle relative path
            if (!path.startsWith("/"))
            {
                String relTo = _pathInContext;
                int slash = relTo.lastIndexOf("/");
                if (slash > 1)
                    relTo = relTo.substring(0, slash + 1);
                else
                    relTo = "/";
                path = URIUtil.addPaths(relTo, path);
            }

            return context.getServletContext().getRequestDispatcher(path);
        }

        @Override
        public int getRemotePort()
        {
            return ServletScopedRequest.this.getRemotePort();
        }

        @Override
        public String getLocalName()
        {
            if (_servletChannel != null)
            {
                String localName = _servletChannel.getLocalName();
                return formatAddrOrHost(localName);
            }

            return ""; // not allowed to be null
        }

        @Override
        public String getLocalAddr()
        {
            return ServletScopedRequest.this.getLocalAddr();
        }

        @Override
        public int getLocalPort()
        {
            return ServletScopedRequest.this.getLocalPort();
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
        public HttpServletMapping getHttpServletMapping()
        {
            return _mappedServlet.getServletPathMapping(_pathInContext);
        }

        @Override
        public boolean isAsyncStarted()
        {
            return getState().isAsyncStarted();
        }

        @Override
        public boolean isAsyncSupported()
        {
            return true;
        }

        @Override
        public AsyncContext getAsyncContext()
        {
            ServletRequestState state = _servletChannel.getState();
            if (_async == null || !state.isAsyncStarted())
                throw new IllegalStateException(state.getStatusString());

            return _async;
        }

        @Override
        public DispatcherType getDispatcherType()
        {
            return DispatcherType.REQUEST;
        }
    }
}
