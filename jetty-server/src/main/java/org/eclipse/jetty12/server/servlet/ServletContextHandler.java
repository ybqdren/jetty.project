package org.eclipse.jetty12.server.servlet;

import jakarta.servlet.ServletContext;
import org.eclipse.jetty12.server.Channel;
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
    protected ServletScopedRequest wrap(Request request, Response response, String pathInContext)
    {
        ServletHandler.MappedServlet mappedServlet = _servletHandler.findMapping(pathInContext);
        if (mappedServlet == null)
            return null;

        Channel channel = request.getChannel();
        ServletScopedRequest servletScopedRequest = (ServletScopedRequest)channel.getAttribute(ServletScopedRequest.class.getName());
        if (servletScopedRequest == null)
            servletScopedRequest = new ServletScopedRequest(getContext(), _servletContext, request, pathInContext, mappedServlet);
        else
            servletScopedRequest.remap(mappedServlet);

        return servletScopedRequest;
    }
}
