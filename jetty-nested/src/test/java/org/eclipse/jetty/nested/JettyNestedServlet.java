package org.eclipse.jetty.nested;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;

public class JettyNestedServlet extends HttpServlet
{
    private final Server _server;
    private final NestedConnector _connector;

    public JettyNestedServlet()
    {
        _server = new Server();
        _connector = new NestedConnector(_server);
        _server.addConnector(_connector);
    }

    public Server getServer()
    {
        return _server;
    }

    @Override
    public void init() throws ServletException
    {
        try
        {
            _server.start();
        }
        catch (Exception e)
        {
            throw new ServletException(e);
        }
    }

    @Override
    public void destroy()
    {
        try
        {
            _server.stop();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        _connector.service(req, resp);
    }
}
