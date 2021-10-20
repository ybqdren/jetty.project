package org.eclipse.jetty12.server.handler;

import java.nio.file.Path;

import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class ContextHandler<R extends ScopedRequest> extends Handler.Convertor<Request, R>
{
    private static final ThreadLocal<Context> __context = new ThreadLocal<>();
    private Context _context;

    public Context getContext()
    {
        return _context;
    }

    protected String getPathInContext(Request request)
    {
        String path = request.getMetaData().getURI().getPath();
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

        R scoped = scope(request, response, pathInContext);
        if (scoped == null)
            return false; // TODO 404? 500? Error dispatch ???

        _context.run(() -> next.handle(scoped, response));
        return true;
    }

    protected R scope(Request request, Response response, String pathInContext)
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
