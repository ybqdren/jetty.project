package org.eclipse.jetty.servlet6.exerimental;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet6.experimental.ServletContextHandler;
import org.eclipse.jetty.util.IO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JettyServletTest
{
    private Server _server;
    private ServerConnector _connector;

    @BeforeEach
    public void before() throws Exception
    {
        _server = new Server();
        _connector = new ServerConnector(_server);
        _server.addConnector(_connector);

        ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.addServlet(MyServlet.class, "/");
        _server.setHandler(contextHandler);

        _server.start();
    }

    @AfterEach
    public void after() throws Exception
    {
        _server.stop();
    }

    public static class MyServlet extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
        {
            resp.setContentType("text/plain");
            //resp.getWriter().println("hello world");
            resp.getOutputStream().write("hello world".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void test() throws Exception
    {
        URL uri = new URL("http://localhost:" + _connector.getLocalPort());
        HttpURLConnection connection = (HttpURLConnection)uri.openConnection();

        System.err.println();
        System.err.println(connection.getHeaderField(null));
        connection.getHeaderFields().entrySet()
            .stream()
            .filter(e -> e.getKey() != null)
            .forEach(e -> System.err.printf("  %s: %s\n", e.getKey(), e.getValue()));

        if (connection.getContentLengthLong() != 0)
            System.err.println("\n" + IO.toString(connection.getInputStream()));
        System.err.println();
    }
}
