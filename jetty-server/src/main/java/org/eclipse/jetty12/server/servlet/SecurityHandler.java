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
                request.getHttpServletResponse().sendError(403);
            }
            catch (Exception e)
            {
                request.failed(e);
                return true;
            }
            // Fall through to super.handle, so ServletHandler will be called and will see the sendError and act on it
        }

        return super.handle(request, response);
    }
}
