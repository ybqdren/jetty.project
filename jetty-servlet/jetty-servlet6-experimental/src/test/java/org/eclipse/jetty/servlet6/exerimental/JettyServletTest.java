package org.eclipse.jetty.servlet6.exerimental;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class JettyServletTest
{
    private Server _server;
    private ServerConnector _connector;
    ServletContextHandler _contextHandler = new ServletContextHandler();

    @BeforeEach
    public void before() throws Exception
    {
        _server = new Server();
        _connector = new ServerConnector(_server);
        _server.addConnector(_connector);
        _server.setHandler(_contextHandler);
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
            ServletOutputStream outputStream = resp.getOutputStream();
            outputStream.setWriteListener(new WriteListener()
            {
                private int i = 0;

                @Override
                public void onWritePossible() throws IOException
                {
                    if (i < 10)
                        outputStream.println("i: " + i++);
                    else
                        outputStream.close();
                }

                @Override
                public void onError(Throwable t)
                {
                    resp.setStatus(501);
                }
            });
        }
    }

    private void testResponse() throws Exception
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

        assertThat(connection.getResponseCode(), equalTo(200));
    }

    @Test
    public void testBlockingWrite() throws Exception
    {
        _contextHandler.addServlet(new HttpServlet()
        {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
            {
                resp.setContentType("text/plain");
                resp.getOutputStream().write("hello world".getBytes(StandardCharsets.UTF_8));
            }
        }, "/");

        testResponse();
    }

    @Test
    public void testBlockingWriter() throws Exception
    {
        _contextHandler.addServlet(new HttpServlet()
        {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
            {
                resp.setContentType("text/plain");
                resp.getWriter().write("hello world");
            }
        }, "/");

        testResponse();
    }

    @Test
    public void testAsyncWrite() throws Exception
    {
        _contextHandler.addServlet(new HttpServlet()
        {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
            {
                resp.setContentType("text/plain");

                AsyncContext asyncContext = req.startAsync();
                ServletOutputStream outputStream = resp.getOutputStream();
                outputStream.setWriteListener(new WriteListener()
                {
                    private int i = 0;

                    @Override
                    public void onWritePossible() throws IOException
                    {
                        while (outputStream.isReady())
                        {
                            // TODO: Fix reentry into onWritePossible.
                            // TODO: only call onWritePossible when isReady is called.
                            if (i < 10)
                            {
                                outputStream.println("i: " + i++);
                            }
                            else
                            {
                                outputStream.close();
                                asyncContext.complete();
                                return;
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t)
                    {
                        resp.setStatus(501);
                        asyncContext.complete();
                    }
                });
            }
        }, "/");


        testResponse();
    }
}
