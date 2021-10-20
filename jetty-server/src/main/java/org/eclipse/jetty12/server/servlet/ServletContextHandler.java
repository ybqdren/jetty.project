package org.eclipse.jetty12.server.servlet;

import jakarta.servlet.ServletContext;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;
import org.eclipse.jetty12.server.handler.ContextHandler;

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
        ServletHandler.MappedServlet mappedServlet = _servletHandler.findMapping(pathInContext);
        // TODO is a null mapping a 404?
        //      if so we should create the scoped request, call sendError(404) and let flow through to ServletHandler.
        //      or is a null an indication that we will not handle and the handler should return false?
        if (mappedServlet == null)
            return null;

        // TODO could we somehow reuse a wrapper for the next cycle?
        return new ServletScopedRequest(getContext(), request, pathInContext, mappedServlet);
    }
}
