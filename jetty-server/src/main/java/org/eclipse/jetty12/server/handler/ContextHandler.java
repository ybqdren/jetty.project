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

import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class ContextHandler<R extends ScopedRequest> extends Handler.Processor<Request, R>
{
    private static final ThreadLocal<Context> __context = new ThreadLocal<>();
    private Context _context;

    // TODO need 2 level classloaders for API and app
    //      Probably will need support for loaders in XmlConfiguration also
    //      is this a job for JPMS modules?  Or shall we go OSGi :)
    private ClassLoader _apiLoader;
    private ClassLoader _contextLoader;

    public Context getContext()
    {
        return _context;
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
        Handler<R> next = getNext();
        if (next == null)
            return false;

        String pathInContext = getPathInContext(request);
        if (pathInContext == null)
            return false;

        R scoped = wrap(request, response, pathInContext);
        if (scoped == null)
            return false; // TODO 404? 500? Error dispatch ???

        // TODO make the lambda part of the scope request to save allocation
        _context.run(() -> next.handle(scoped, response));
        return true;
    }

    protected R wrap(Request request, Response response, String pathInContext)
    {
        return (R)new ScopedRequest(_context, request, pathInContext);
    }

    public interface Context
    {
        String getContextPath();

        ClassLoader getClassLoader();

        Path getResourceBase();

        default void run(Runnable task)
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
