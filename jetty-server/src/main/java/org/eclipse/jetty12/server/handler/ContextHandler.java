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

package org.eclipse.jetty12.server.handler;

import java.nio.file.Path;
import java.util.Set;

import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class ContextHandler extends Handler.Nested implements Attributes
{
    private static final ThreadLocal<Context> __context = new ThreadLocal<>();
    private final Attributes _persistentAttributes = new Mapped();
    private final Context _context = new Context();

    private String _contextPath;
    private Path _resourceBase;
    private ClassLoader _contextLoader;

    public Context getContext()
    {
        return _context;
    }

    public String getContextPath()
    {
        return _contextPath;
    }

    public void setContextPath(String contextPath)
    {
        if (isStarted())
            throw new IllegalStateException(getState());
        _contextPath = contextPath;
    }

    public Path getResourceBase()
    {
        return _resourceBase;
    }

    public void setResourceBase(Path resourceBase)
    {
        if (isStarted())
            throw new IllegalStateException(getState());
        _resourceBase = resourceBase;
    }

    public ClassLoader getContextLoader()
    {
        return _contextLoader;
    }

    public void setContextLoader(ClassLoader contextLoader)
    {
        if (isStarted())
            throw new IllegalStateException(getState());
        _contextLoader = contextLoader;
    }

    protected String getPathInContext(Request request)
    {
        String path = request.getURI().getPath();
        if (!path.startsWith(_context.getContextPath()))
            return null;
        if ("/".equals(_context.getContextPath()))
            return path;
        if (path.length() == _context.getContextPath().length())
            return "/";
        if (path.charAt(_context.getContextPath().length()) != '/')
            return null;
        return path.substring(_context.getContextPath().length());
    }

    @Override
    public boolean handle(Request request, Response response)
    {
        Handler next = getNext();
        if (next == null)
            return false;

        String pathInContext = getPathInContext(request);
        if (pathInContext == null)
            return false;

        ScopedRequest scoped = wrap(request, response, pathInContext);
        if (scoped == null)
            return false; // TODO 404? 500? Error dispatch ???

        // TODO make the lambda part of the scope request to save allocation
        _context.run(() -> next.handle(scoped, response));
        return true;
    }

    protected ScopedRequest wrap(Request request, Response response, String pathInContext)
    {
        return new ScopedRequest(_context, request, pathInContext);
    }

    @Override
    public void setAttribute(String name, Object attribute)
    {
        _persistentAttributes.setAttribute(name, attribute);
    }

    @Override
    public Object getAttribute(String name)
    {
        return _persistentAttributes.getAttribute(name);
    }

    @Override
    public Set<String> getAttributeNameSet()
    {
        return _persistentAttributes.getAttributeNameSet();
    }

    @Override
    public void removeAttribute(String name)
    {
        _persistentAttributes.removeAttribute(name);
    }

    @Override
    public void clearAttributes()
    {
        _persistentAttributes.clearAttributes();
    }

    public class Context extends Attributes.Layer
    {
        public Context()
        {
            super(_persistentAttributes);
        }

        @SuppressWarnings("unchecked")
        public <H extends ContextHandler> H getContextHandler()
        {
            return (H)ContextHandler.this;
        }

        public String getContextPath()
        {
            return _contextPath;
        }

        public ClassLoader getClassLoader()
        {
            return _contextLoader;
        }

        public Path getResourceBase()
        {
            return _resourceBase;
        }

        public void run(Runnable task)
        {
            ClassLoader loader = getClassLoader();
            if (loader == null)
                task.run();
            else
            {
                ClassLoader lastLoader = Thread.currentThread().getContextClassLoader();
                Context lastContext = __context.get();
                try
                {
                    __context.set(this);
                    Thread.currentThread().setContextClassLoader(loader);
                    task.run();
                }
                finally
                {
                    Thread.currentThread().setContextClassLoader(lastLoader);
                    __context.set(lastContext);
                }
            }
        }
    }
}
