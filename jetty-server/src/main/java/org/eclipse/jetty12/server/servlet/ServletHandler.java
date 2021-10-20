package org.eclipse.jetty12.server.servlet;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.ServletPathMapping;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Response;

public class ServletHandler extends Handler.Abstract<ServletScopedRequest>
{
    public interface MappedServlet
    {
        ServletPathMapping getServletPathMapping();

        void handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
    }

    public MappedServlet findMapping(String pathInContext)
    {
        return null;
    }

    @Override
    public boolean handle(ServletScopedRequest request, Response response)
    {
        MappedServlet mappedServlet = request.getMappedServlet();
        if (mappedServlet == null)
            return false; // TODO or 404?

        request.handle();
        return true;
    }
}
