package org.eclipse.jetty12.server.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.ServletPathMapping;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Response;

public class ServletHandler extends Handler.Abstract<ServletScopedRequest>
{
    public ServletPathMapping findMapping(String pathInContext)
    {
        // TODO find ServletHandler and let it do it's stuff
        return null;
    }

    @Override
    public boolean handle(ServletScopedRequest request, Response response)
    {
        ServletPathMapping mapping = request.getServletPathMapping();
        if (mapping == null)
            return false;
        handle(request.getServletPathMapping(), request, request.getHttpServletRequest(), request.getHttpServletResponse());
        return true;
    }

    void handle(ServletPathMapping servletPathMapping, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
    {
        ServletScopedRequest request = ServletScopedRequest.getRequest(httpServletRequest);
        handle(servletPathMapping, request, httpServletRequest, httpServletResponse);
    }

    private void handle(ServletPathMapping servletPathMapping, ServletScopedRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
    {
        request.handle(servletPathMapping, httpServletRequest, httpServletResponse);
    }
}
