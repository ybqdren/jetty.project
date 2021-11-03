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

package org.eclipse.jetty12.server.servlet6;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;
import org.eclipse.jetty12.server.Server;
import org.eclipse.jetty12.server.SessionIdManager;
import org.eclipse.jetty12.server.SessionManager;
import org.eclipse.jetty12.server.Stream;
import org.eclipse.jetty12.server.handler.ContextHandler;
import org.eclipse.jetty12.server.session.DefaultSessionCache;
import org.eclipse.jetty12.server.session.DefaultSessionIdManager;
import org.eclipse.jetty12.server.session.NullSessionDataStore;
import org.eclipse.jetty12.server.session.Session;
import org.eclipse.jetty12.server.session.SessionCache;
import org.eclipse.jetty12.server.session.SessionCacheFactory;
import org.eclipse.jetty12.server.session.SessionContext;
import org.eclipse.jetty12.server.session.SessionDataStore;
import org.eclipse.jetty12.server.session.SessionDataStoreFactory;
import org.eclipse.jetty12.server.session.UnreadableSessionDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionHandler extends Handler.Nested implements SessionManager
{    
    private static final Logger LOG = LoggerFactory.getLogger(SessionHandler.class);
    
    private boolean _usingURLs;
    private boolean _usingCookies = true;
    private SessionIdManager _sessionIdManager;
    private ClassLoader _loader;
    private ContextHandler.Context _context;
    private SessionContext _sessionContext;
    private SessionCache _sessionCache;
    private final List<HttpSessionAttributeListener> _sessionAttributeListeners = new CopyOnWriteArrayList<>();
    private final List<HttpSessionListener> _sessionListeners = new CopyOnWriteArrayList<>();
    private final List<HttpSessionIdListener> _sessionIdListeners = new CopyOnWriteArrayList<>();
    private Set<String> _candidateSessionIdsForExpiry = ConcurrentHashMap.newKeySet();
    
    public class ServletAPISession implements HttpSession
    {
        private Session _session;
        
        public ServletAPISession(Session session)
        {
            _session = session;
            _session.setWrapper(this);
        }

        public Session getSession()
        {
            return _session;
        }
        
        @Override
        public long getCreationTime()
        {
            _session.getCreationTime();
        }

        @Override
        public String getId()
        {
            return _session.getId();
        }

        @Override
        public long getLastAccessedTime()
        {
            return _session.getLastAccessedTime();
        }

        @Override
        public ServletContext getServletContext()
        {
            return _session.getServletContext();
        }

        @Override
        public void setMaxInactiveInterval(int interval)
        {
            _session.setMaxInactiveInterval(interval);
        }

        @Override
        public int getMaxInactiveInterval()
        {
            return _session.getMaxInactiveInterval();
        }

        @Override
        public Object getAttribute(String name)
        {
            return _session.getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames()
        {
            return _session.getAttributeNames();
        }

        @Override
        public void setAttribute(String name, Object value)
        {
            _session.setAttribute(name, value);
        }

        @Override
        public void removeAttribute(String name)
        {
            _session.removeAttribute(name);

        @Override
        public void invalidate()
        {
            _session.invalidate();
        }

        @Override
        public boolean isNew()
        {
            return _session.isNew();
        }
    }
    
        /**
         * Adds an event listener for session-related events.
         *
         * @param listener the session event listener to add
         * Individual SessionManagers implementations may accept arbitrary listener types,
         * but they are expected to at least handle HttpSessionActivationListener,
         * HttpSessionAttributeListener, HttpSessionBindingListener and HttpSessionListener.
         * @return true if the listener was added
         * @see #removeEventListener(EventListener)
         * @see HttpSessionAttributeListener
         * @see HttpSessionListener
         * @see HttpSessionIdListener
         */
        @Override
        public boolean addEventListener(EventListener listener)
        {
            if (super.addEventListener(listener))
            {
                if (listener instanceof HttpSessionAttributeListener)
                    _sessionAttributeListeners.add((HttpSessionAttributeListener)listener);
                if (listener instanceof HttpSessionListener)
                    _sessionListeners.add((HttpSessionListener)listener);
                if (listener instanceof HttpSessionIdListener)
                    _sessionIdListeners.add((HttpSessionIdListener)listener);
                return true;
            }
            return false;
        }    
        
    protected void doStart() throws Exception
    {
        //check if session management is set up, if not set up defaults
        final Server server = getServer();

        _context = ContextHandler.getCurrentContext();
        _loader = Thread.currentThread().getContextClassLoader();

        // Use a coarser lock to serialize concurrent start of many contexts.
        synchronized (server)
        {
            //Get a SessionDataStore and a SessionDataStore, falling back to in-memory sessions only
            if (_sessionCache == null)
            {
                SessionCacheFactory ssFactory = server.getBean(SessionCacheFactory.class);
                setSessionCache(ssFactory != null ? ssFactory.getSessionCache(this) : new DefaultSessionCache(this));
                SessionDataStore sds = null;
                SessionDataStoreFactory sdsFactory = server.getBean(SessionDataStoreFactory.class);
                if (sdsFactory != null)
                    sds = sdsFactory.getSessionDataStore(this);
                else
                    sds = new NullSessionDataStore();

                _sessionCache.setSessionDataStore(sds);
            }

            if (_sessionIdManager == null)
            {
                _sessionIdManager = server.getSessionIdManager();
                if (_sessionIdManager == null)
                {
                    //create a default SessionIdManager and set it as the shared
                    //SessionIdManager for the Server, being careful NOT to use
                    //the webapp context's classloader, otherwise if the context
                    //is stopped, the classloader is leaked.
                    ClassLoader serverLoader = server.getClass().getClassLoader();
                    try
                    {
                        Thread.currentThread().setContextClassLoader(serverLoader);
                        _sessionIdManager = new DefaultSessionIdManager(server);
                        server.setSessionIdManager(_sessionIdManager);
                        server.manage(_sessionIdManager);
                        _sessionIdManager.start();
                    }
                    finally
                    {
                        Thread.currentThread().setContextClassLoader(_loader);
                    }
                }

                // server session id is never managed by this manager
                addBean(_sessionIdManager, false);
            }

            _scheduler = server.getBean(Scheduler.class);
            if (_scheduler == null)
            {
                _scheduler = new ScheduledExecutorScheduler(String.format("Session-Scheduler-%x", hashCode()), false);
                _ownScheduler = true;
                _scheduler.start();
            }
        }

        // Look for a session cookie name
        if (_context != null)
        {
            String tmp = _context.getInitParameter(__SessionCookieProperty);
            if (tmp != null)
                _sessionCookie = tmp;

            tmp = _context.getInitParameter(__SessionIdPathParameterNameProperty);
            if (tmp != null)
                setSessionIdPathParameterName(tmp);

            // set up the max session cookie age if it isn't already
            if (_maxCookieAge == -1)
            {
                tmp = _context.getInitParameter(__MaxAgeProperty);
                if (tmp != null)
                    _maxCookieAge = Integer.parseInt(tmp.trim());
            }

            // set up the session domain if it isn't already
            if (_sessionDomain == null)
                _sessionDomain = _context.getInitParameter(__SessionDomainProperty);

            // set up the sessionPath if it isn't already
            if (_sessionPath == null)
                _sessionPath = _context.getInitParameter(__SessionPathProperty);

            tmp = _context.getInitParameter(__CheckRemoteSessionEncoding);
            if (tmp != null)
                _checkingRemoteSessionIdEncoding = Boolean.parseBoolean(tmp);
        }

        _sessionContext = new SessionContext(_sessionIdManager.getWorkerName(), _context);
        _sessionCache.initialize(_sessionContext);
        super.doStart();
    }
    
    /**
     * Gets the cross context session id manager
     *
     * @return the session id manager
     */
    public SessionIdManager getSessionIdManager()
    {
        return _sessionIdManager;
    }
    
    /**
     * Get a known existing session
     *
     * @param extendedId The session id, possibly with worker name.
     * @return A Session or null if none exists.
     */
    public Session getSession(String extendedId)
    {
        String id = getSessionIdManager().getId(extendedId);
        try
        {        
            Session session = _sessionCache.get(id);
            if (session != null)
            {
                //If the session we got back has expired
                if (session.isExpiredAt(System.currentTimeMillis()))
                {
                    //Expire the session
                    try
                    {
                        session.invalidate();
                    }
                    catch (Exception e)
                    {
                        LOG.warn("Invalidating session {} found to be expired when requested", id, e);
                    }

                    return null;
                }

                session.setExtendedId(_sessionIdManager.getExtendedId(id, null));
            }
            
            if (session != null && !session.getExtendedId().equals(extendedId))
                session.setIdChanged(true);
            
            return session;
        }
        catch (UnreadableSessionDataException e)
        {
            LOG.warn("Error loading session {}", id, e);
            try
            {
                //tell id mgr to remove session from all other contexts
                getSessionIdManager().invalidateAll(id);
            }
            catch (Exception x)
            {
                LOG.warn("Error cross-context invalidating unreadable session {}", id, x);
            }
            return null;
        }
        catch (Exception other)
        {
            LOG.warn("Unable to get Session", other);
            return null;
        }
    }
    
    /**
     * @return whether the session management is handled via URLs.
     */
    public boolean isUsingURLs()
    {
        return _usingURLs;
    }
    
    /**
     * @return true if using session cookies is allowed, false otherwise
     */
    public boolean isUsingCookies()
    {
        return _usingCookies;
    }

    /**
     * Check if id is in use by this context
     *
     * @param id identity of session to check
     * @return <code>true</code> if this manager knows about this id
     * @throws Exception if any error occurred
     */
    public boolean isIdInUse(String id) throws Exception
    {
        //Ask the session store
        return _sessionCache.exists(id);
    }
    
    /**
     * @param usingCookies 
     */
    public void setUsingCookies(boolean usingCookies)
    {
        _usingCookies = usingCookies;
    }
    
    /**
     * Change the existing session id.
     *
     * @param oldId the old session id
     * @param oldExtendedId the session id including worker suffix
     * @param newId the new session id
     * @param newExtendedId the new session id including worker suffix
     */
    public void renewSessionId(String oldId, String oldExtendedId, String newId, String newExtendedId)
    {
        Session session = null;
        try
        {
            //the use count for the session will be incremented in renewSessionId
            session = _sessionCache.renewSessionId(oldId, newId, oldExtendedId, newExtendedId); //swap the id over
            if (session == null)
            {
                //session doesn't exist on this context
                return;
            }

            //inform the listeners
            callSessionIdListeners(session, oldId);
        }
        catch (Exception e)
        {
            LOG.warn("Unable to renew Session Id {}:{} -> {}:{}", oldId, oldExtendedId, newId, newExtendedId, e);
        }
        finally
        {
            if (session != null)
            {
                try
                {
                    _sessionCache.release(newId, session);
                }
                catch (Exception e)
                {
                    LOG.warn("Unable to release {}", newId, e);
                }
            }
        }
    }   

    /**
     * Called by SessionIdManager to remove a session that has been invalidated,
     * either by this context or another context. Also called by
     * SessionIdManager when a session has expired in either this context or
     * another context.
     *
     * @param id the session id to invalidate
     */
    public void invalidate(String id)
    {

        if (StringUtil.isBlank(id))
            return;

        try
        {
            // Remove the Session object from the session cache and any backing
            // data store
            Session session = _sessionCache.delete(id);
            if (session != null)
            {
                //start invalidating if it is not already begun, and call the listeners
                try
                {
                    if (session.beginInvalidate())
                    {
                        try
                        {
                            callSessionDestroyedListeners(session);
                        }
                        catch (Exception e)
                        {
                            LOG.warn("Error during Session destroy listener", e);
                        }
                        //call the attribute removed listeners and finally mark it as invalid
                        session.finishInvalidate();
                    }
                }
                catch (IllegalStateException e)
                {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Session {} already invalid", session, e);
                }
            }
        }
        catch (Exception e)
        {
            LOG.warn("Unable to delete Session {}", id, e);
        }
    }
    
    /**
     * Called periodically by the HouseKeeper to handle the list of
     * sessions that have expired since the last call to scavenge.
     */
    public void scavenge()
    {
        //don't attempt to scavenge if we are shutting down
        if (isStopping() || isStopped())
            return;

        if (LOG.isDebugEnabled())
            LOG.debug("{} scavenging sessions", this);
        //Get a snapshot of the candidates as they are now. Others that
        //arrive during this processing will be dealt with on 
        //subsequent call to scavenge
        String[] ss = _candidateSessionIdsForExpiry.toArray(new String[0]);
        Set<String> candidates = new HashSet<>(Arrays.asList(ss));
        _candidateSessionIdsForExpiry.removeAll(candidates);
        if (LOG.isDebugEnabled())
            LOG.debug("{} scavenging session ids {}", this, candidates);
        try
        {
            candidates = _sessionCache.checkExpiration(candidates);
            for (String id : candidates)
            {
                try
                {
                    getSessionIdManager().expireAll(id);
                }
                catch (Exception e)
                {
                    LOG.warn("Unable to expire Session {}", id, e);
                }
            }
        }
        catch (Exception e)
        {
            LOG.warn("Failed to check expiration on {}",
                candidates.stream().map(Objects::toString).collect(Collectors.joining(", ", "[", "]")),
                e);
        }
    }

    public void callSessionAttributeListeners(Session session, String name, Object old, Object value)
    {
        if (!_sessionAttributeListeners.isEmpty())
        {
            HttpSessionBindingEvent event = new HttpSessionBindingEvent(session.getWrapper(), name, old == null ? value : old);

            for (HttpSessionAttributeListener l : _sessionAttributeListeners)
            {
                if (old == null)
                    l.attributeAdded(event);
                else if (value == null)
                    l.attributeRemoved(event);
                else
                    l.attributeReplaced(event);
            }
        }
    }
    
    /**
     * Call the session lifecycle listeners in the order
     * they were added.
     *
     * @param session the session on which to call the lifecycle listeners
     */
    public void callSessionCreatedListeners(Session session)
    {
        if (session == null)
            return;

        if (_sessionListeners != null)
        {
            HttpSessionEvent event = new HttpSessionEvent(session.getWrapper());
            for (HttpSessionListener  l : _sessionListeners)
            {
                l.sessionCreated(event);
            }
        }
    }
 
    /**
     * Call the session lifecycle listeners in
     * the reverse order they were added.
     *
     * @param session the session on which to call the lifecycle listeners
     */
    public void callSessionDestroyedListeners(Session session)
    {
        if (session == null)
            return;

        if (_sessionListeners != null)
        {
            //We annoint the calling thread with
            //the webapp's classloader because the calling thread may
            //come from the scavenger, rather than a request thread
            Runnable r = new Runnable()
            {
                @Override
                public void run()
                {
                    HttpSessionEvent event = new HttpSessionEvent(session.getWrapper());
                    for (int i = _sessionListeners.size() - 1; i >= 0; i--)
                    {
                        _sessionListeners.get(i).sessionDestroyed(event);
                    }
                }
            };
            _sessionContext.run(r);
        }
    }

    public void callSessionIdListeners(Session session, String oldId)
    {
        //inform the listeners
        if (!_sessionIdListeners.isEmpty())
        {
            HttpSessionEvent event = new HttpSessionEvent(session.getWrapper());
            for (HttpSessionIdListener l : _sessionIdListeners)
            {
                l.sessionIdChanged(event, oldId);
            }
        }
    }
    
    /**
     * Look for a requested session ID in cookies and URI parameters
     *
     * @param baseRequest the request to check
     * @param request the request to check
     */
    protected void resolveRequestedSessionId(ServletScopedRequest.MutableHttpServletRequest request)
    {
        String requestedSessionId = request.getRequestedSessionId();

        if (requestedSessionId != null)
        {
            Session session = getSession(requestedSessionId);
            
            ServletAPISession apiSession = new ServletAPISession(session);

            if (session != null && session.isValid())
            {
                request.setBaseSession(session);
                request.setHttpSession(apiSession);
            }
            return;
        }
        else if (!DispatcherType.REQUEST.equals(request.getDispatcherType()))
            return;

        boolean requestedSessionIdFromCookie = false;
        Session session = null;

        //first try getting id from a cookie
        if (isUsingCookies())
        {
            Cookie[] cookies = request.getCookies();
            if (cookies != null && cookies.length > 0)
            {
                final String sessionCookie = getSessionCookieName(getSessionCookieConfig());
                for (Cookie cookie : cookies)
                {
                    if (sessionCookie.equalsIgnoreCase(cookie.getName()))
                    {
                        String id = cookie.getValue();
                        requestedSessionIdFromCookie = true;
                        if (LOG.isDebugEnabled())
                            LOG.debug("Got Session ID {} from cookie {}", id, sessionCookie);

                        if (session == null)
                        {
                            //we currently do not have a session selected, use this one if it is valid
                            Session s = getSession(id);
                            if (s != null && s.isValid())
                            {
                                //associate it with the request so its reference count is decremented as the
                                //request exits
                                requestedSessionId = id;
                                session = s;
                                request.setBaseSession(session);

                                if (LOG.isDebugEnabled())
                                    LOG.debug("Selected session {}", session);
                            }
                            else
                            {
                                if (LOG.isDebugEnabled())
                                    LOG.debug("No session found for session cookie id {}", id);

                                //if we don't have a valid session id yet, just choose the current id
                                if (requestedSessionId == null)
                                    requestedSessionId = id;
                            }
                        }
                        else
                        {
                            //we currently have a valid session selected. We will throw an error
                            //if there is a _different_ valid session id cookie. Duplicate ids, or
                            //invalid session ids are ignored
                            if (!session.getId().equals(getSessionIdManager().getId(id)))
                            {
                                if (LOG.isDebugEnabled())
                                    LOG.debug("Multiple different valid session ids: {}, {}", requestedSessionId, id);
                                
                                //load the session to see if it is valid or not
                                Session s = getSession(id);
                                if (s != null && s.isValid())
                                {
                                    //TODO release the session straight away??
                                    try
                                    {
                                        _sessionCache.release(id, s);
                                    }
                                    catch (Exception x)
                                    {
                                        if (LOG.isDebugEnabled())
                                            LOG.debug("Error releasing duplicate valid session: {}", id);
                                    }

                                    throw new BadMessageException("Duplicate valid session cookies: " + requestedSessionId + " ," + id);
                                }
                            }
                            else
                            {
                                if (LOG.isDebugEnabled())
                                    LOG.debug("Duplicate valid session cookie id: {}", id);
                            }
                        }
                    }
                }
            }
        }

        //try getting id from a url
        if (isUsingURLs() && (requestedSessionId == null))
        {
            String uri = request.getRequestURI();
            String prefix = getSessionIdPathParameterNamePrefix();
            if (prefix != null)
            {
                int s = uri.indexOf(prefix);
                if (s >= 0)
                {
                    s += prefix.length();
                    int i = s;
                    while (i < uri.length())
                    {
                        char c = uri.charAt(i);
                        if (c == ';' || c == '#' || c == '?' || c == '/')
                            break;
                        i++;
                    }

                    requestedSessionId = uri.substring(s, i);
                    requestedSessionIdFromCookie = false;

                    if (LOG.isDebugEnabled())
                        LOG.debug("Got Session ID {} from URL", requestedSessionId);

                    session = getSession(requestedSessionId);
                    if (session != null && session.isValid())
                    {
                        request.setBaseSession(session);  //associate the session with the request
                    }
                }
            }
        }

        request.setRequestedSessionId(requestedSessionId);
        request.setRequestedSessionIdFromCookie(requestedSessionId != null && requestedSessionIdFromCookie);
    }
    
    /**
     * Called when a request is finally leaving a session.
     *
     * @param session the session object
     */
    protected void complete(Session session)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Complete called with session {}", session);

        if (session == null)
            return;
        try
        {
            _sessionCache.release(session.getId(), session);
        }
        catch (Exception e)
        {
            LOG.warn("Unable to release Session {}", session, e);
        }
    }

    /**
     * Called when a response is about to be committed.
     * We might take this opportunity to persist the session
     * so that any subsequent requests to other servers
     * will see the modifications.
     */
    public void commit(Session session)
    {
        if (session == null)
            return;

        try
        {
            _sessionCache.commit(session);
        }
        catch (Exception e)
        {
            LOG.warn("Unable to commit Session {}", session, e);
        }
    }
    
    /**
     * Called by the {@link SessionHandler} when a session is first accessed by a request.
     *
     * Updates the last access time for the session and generates a fresh cookie if necessary.
     *
     * @param session the session object
     * @param secure whether the request is secure or not
     * @return the session cookie. If not null, this cookie should be set on the response to either migrate
     * the session or to refresh a session cookie that may expire.
     * @see #complete(HttpSession)
     */
    public HttpCookie access(Session session, boolean secure)
    {
        long now = System.currentTimeMillis();

        if (session.access(now))
        {
            // Do we need to refresh the cookie?
            if (isUsingCookies() &&
                (session.isIdChanged() ||
                    (getSessionCookieConfig().getMaxAge() > 0 && getRefreshCookieAge() > 0 &&
                        ((now - session.getCookieSetTime()) / 1000 > getRefreshCookieAge()))))
            {
                HttpCookie cookie = getSessionCookie(session, _context == null ? "/" : (_context.getContextPath()), secure);
                session.cookieSet();
                session.setIdChanged(false);
                return cookie;
            }
        }
        return null;
    }

    @Override
    public boolean handle(Request request, Response response)
    {
        ServletScopedRequest.MutableHttpServletRequest servletRequest =
            request.get(ServletScopedRequest.class, ServletScopedRequest::getMutableHttpServletRequest);
        
        if (servletRequest == null)
            return false;
        
       //TODO need a response that I can set a cookie on, and work out if it is secure or not

        // TODO servletRequest can be mutable, so we can add session stuff to it
        servletRequest.setSessionManager(this);
        servletRequest.setBaseSession(null);

        // find and set the session if one exists
        resolveRequestedSessionId(servletRequest);

        //TODO call access here, or from inside checkRequestedSessionId

        HttpCookie cookie = access(servletRequest.getBaseSession(), request.getConnectionMetaData().isSecure());

        // Handle changed ID or max-age refresh, but only if this is not a redispatched request
        if ((cookie != null) &&
            (request.getDispatcherType() == DispatcherType.ASYNC ||
                request.getDispatcherType() == DispatcherType.REQUEST))     
            servletRequest.getMutableHttpServletResponse().replaceCookie(cookie);


        request.getChannel().onStreamEvent(s ->
        new Stream.Wrapper(s)
        {
            @Override
            public void send(MetaData.Response response, boolean last, Callback callback, ByteBuffer... content)
            {
                if (response != null)
                {
                    // Write out session
                    Session session = servletRequest.getBaseSession();
                    if (session != null)
                        commit(session);
                }
                super.send(response, last, callback, content);
            }

            @Override
            public void succeeded()
            {
                super.succeeded();
                // Leave session
                Session session = servletRequest.getBaseSession(); 
                if (session != null)
                    complete(session);
            }

            @Override
            public void failed(Throwable x)
            {
                super.failed(x);
                //Leave session
                Session session = servletRequest.getBaseSession();
                if (session != null)
                    complete(session);

            }
        });

        return super.handle(request, response);
    }
}
