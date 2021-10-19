package org.eclipse.jetty12.server.servlet;

import jakarta.servlet.ServletContext;
import org.eclipse.jetty.server.ServletPathMapping;
import org.eclipse.jetty12.server.ContextHandler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class ServletContextHandler extends ContextHandler<ServletScopedRequest>
{
    private ServletHandler _servletHandler;
    private ServletContext _servletContext;

    @Override
    protected void doStart() throws Exception
    {
        super.doStart();
        getContainedBeans(ServletHandler.class).stream().findFirst().ifPresent(sh -> _servletHandler = sh);
        _servletContext = new ServletContextContext(getContext(), _servletHandler);
    }

    @Override
    protected void doStop() throws Exception
    {
        _servletHandler = null;
    }

    public ServletHandler getServletHandler()
    {
        return _servletHandler;
    }

    @Override
    protected ServletScopedRequest scope(Request request, Response response, String pathInContext)
    {
        ServletPathMapping mapping = _servletHandler.findMapping(pathInContext);
        if (mapping == null)
            return null;

        // TODO security scope?
        // TODO session scope?

        // TODO could we somehow reuse a wrapper for the next cycle?
        return new ServletScopedRequest(getContext(), request, pathInContext, mapping);
    }
}
