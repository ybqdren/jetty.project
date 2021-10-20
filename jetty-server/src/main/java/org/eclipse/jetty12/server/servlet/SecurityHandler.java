package org.eclipse.jetty12.server.servlet;

import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Response;

public class SecurityHandler extends Handler.Wrapper<ServletScopedRequest>
{
    @Override
    public boolean handle(ServletScopedRequest request, Response response)
    {
        ServletScopedRequest.MappedHttpServletRequest servletRequest = request.getHttpServletRequest();

        // if we match some security constraint, we can respond here
        if (servletRequest.getServletPath().startsWith("/secret"))
        {
            try
            {
                // TODO how to do error pages?
                //      the issue here is that the dispatch state machine would need to be handled by ServletContextHandler
                //      but that would mess up things like DeferredContentHandler
                //      other option is to somehow invoke the ServletHandler here, perhaps with a Dispatcher?
                request.getHttpServletResponse().sendError(403);
            }
            catch (Exception e)
            {
                request.failed(e);
            }
            return true;
        }

        return super.handle(request, response);
    }
}
