package org.eclipse.jetty.servlet6.exerimental;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet6.experimental.ServletContextHandler;
import org.eclipse.jetty.util.BufferUtil;
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
        _connector.setPort(8080);
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

    @Test
    public void testRead() throws Exception
    {
        _contextHandler.addServlet(new HttpServlet()
        {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
            {
                resp.setContentType("text/plain");

                ServletInputStream inputStream = req.getInputStream();
                String body = IO.toString(inputStream);
                System.err.println(body);

                resp.setStatus(200);
                resp.getWriter().write("success");
            }
        }, "/");

        testResponse();
    }

    @Test
    public void testAsyncRead() throws Exception
    {
        _contextHandler.addServlet(new HttpServlet()
        {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
            {
                resp.setContentType("text/plain");

                AsyncContext asyncContext = req.startAsync();
                ServletInputStream inputStream = req.getInputStream();
                ServletOutputStream outputStream = resp.getOutputStream();

                byte[] bytes = new byte[100];
                inputStream.setReadListener(new ReadListener()
                {
                    @Override
                    public void onDataAvailable() throws IOException
                    {
                        while (inputStream.isReady())
                        {
                            int read = inputStream.read(bytes);
                            if (read < 0)
                                return;

                            ByteBuffer content = BufferUtil.toBuffer(bytes, 0, read);
                            System.err.println("read data: " + BufferUtil.toDetailString(content));
                        }
                    }

                    @Override
                    public void onAllDataRead() throws IOException
                    {
                        System.err.println("all received");
                        outputStream.close();
                        asyncContext.complete();
                    }

                    @Override
                    public void onError(Throwable t)
                    {
                        t.printStackTrace();
                    }
                });
            }
        }, "/");

        testSlowRequest();
    }

    private void testSlowRequest() throws Exception
    {
        URL uri = new URL("http://localhost:" + _connector.getLocalPort());
        HttpURLConnection connection = (HttpURLConnection)uri.openConnection();

        connection.setDoOutput(true);
        connection.setRequestMethod(HttpMethod.POST.asString());
        connection.setFixedLengthStreamingMode("message1".length() * 2);

        OutputStream outputStream = connection.getOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        writer.write("message1");
        writer.flush();
        outputStream.flush();
        Thread.sleep(1000);
        writer.write("message2");
        writer.flush();
        outputStream.flush();

        writer.close();
        outputStream.close();
        connection.connect();

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
}
