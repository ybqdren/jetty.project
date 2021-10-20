package org.eclipse.jetty12.server.servlet;

import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Response;

public class SessionHandler extends Handler.Wrapper<ServletScopedRequest>
{
    @Override
    public boolean handle(ServletScopedRequest request, Response response)
    {
        ServletScopedRequest.MappedHttpServletRequest servletRequest = request.getHttpServletRequest();

        // TODO servletRequest can be mutable, so we can add session stuff to it
        // servletRequest.setSessionManager(this);

        return super.handle(request, response);
    }
}
