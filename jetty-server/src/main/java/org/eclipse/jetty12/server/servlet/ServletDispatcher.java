package org.eclipse.jetty12.server.servlet;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.ServletPathMapping;
import org.eclipse.jetty12.server.handler.ContextHandler;

public class ServletDispatcher implements RequestDispatcher
{
    private final ContextHandler.Context _context;
    private final ServletHandler _servletHandler;
    private final ServletPathMapping _mapping;

    public ServletDispatcher(ContextHandler.Context context, ServletHandler servletHandler, ServletPathMapping mapping)
    {
        _context = context;
        _servletHandler = servletHandler;
        _mapping = mapping;
    }

    @Override
    public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        // TODO stuff about parameters

        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;

        _servletHandler.handle(_mapping, new ForwardServletRequestWrapper(httpRequest), httpResponse);

    }

    @Override
    public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {

    }

    private class ForwardServletRequestWrapper extends HttpServletRequestWrapper
    {
        private final HttpServletRequest _httpServletRequest;

        public ForwardServletRequestWrapper(HttpServletRequest httpRequest)
        {
            super(httpRequest);
            _httpServletRequest = httpRequest;
        }

        @Override
        public String getPathInfo()
        {
            return _mapping.getPathInfo();
        }

        @Override
        public String getServletPath()
        {
            return _mapping.getServletPath();
        }

        @Override
        public Object getAttribute(String name)
        {
            switch (name)
            {
                case RequestDispatcher.FORWARD_REQUEST_URI:
                    return _httpServletRequest.getRequestURI();
                case RequestDispatcher.FORWARD_SERVLET_PATH:
                    return _httpServletRequest.getServletPath();
                case RequestDispatcher.FORWARD_PATH_INFO:
                    return _httpServletRequest.getPathInfo();

                // TODO etc.
                default:
                    break;
            }
            return super.getAttribute(name);
        }
    }
}
