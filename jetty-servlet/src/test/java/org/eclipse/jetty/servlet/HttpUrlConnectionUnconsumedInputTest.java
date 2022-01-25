//
//  ========================================================================
//  Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HttpUrlConnectionUnconsumedInputTest
{
    private Server server;

    @BeforeEach
    public void startServer() throws Exception
    {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.setContextPath("/");
        contextHandler.addServlet(GetPostPutServlet.class, "/demo");

        HandlerList handlers = new HandlerList();
        handlers.addHandler(contextHandler);
        handlers.addHandler(new DefaultHandler());

        server.setHandler(handlers);
        server.start();
    }

    @AfterEach
    public void stopServer()
    {
        LifeCycle.stop(server);
    }

    @Test
    public void testPostGetPutPutPut() throws IOException
    {
        URI destUri = server.getURI().resolve("/demo");

        // POST
        HttpURLConnection http = (HttpURLConnection)destUri.toURL().openConnection();
        http.setRequestMethod("POST");
        http.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        http.setRequestProperty("Accept", "text/plain");
        http.setDoOutput(true);
        try (OutputStream out = http.getOutputStream())
        {
            out.write("This is the POST content".getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream in = http.getInputStream())
        {
            String response = IO.toString(in, StandardCharsets.UTF_8);
            System.out.printf("POST response (%d): %s%n", http.getResponseCode(), response);
        }

        // GET
        http = (HttpURLConnection)destUri.toURL().openConnection();
        http.setRequestMethod("GET");
        http.setRequestProperty("Accept", "text/plain");

        try (InputStream in = http.getInputStream())
        {
            String response = IO.toString(in, StandardCharsets.UTF_8);
            System.out.printf("GET response (%d): %s%n", http.getResponseCode(), response);
        }

        // PUT
        for (int i = 0; i < 10; i++)
        {
            http = (HttpURLConnection)destUri.toURL().openConnection();
            http.setRequestMethod("PUT");
            http.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            http.setRequestProperty("Accept", "text/plain");
            http.setDoOutput(true);

            try (OutputStream out = http.getOutputStream())
            {
                out.write(("This is the PUT[" + i + "] content").getBytes(StandardCharsets.UTF_8));
            }

            try (InputStream in = http.getInputStream())
            {
                String response = IO.toString(in, StandardCharsets.UTF_8);
                System.out.printf("PUT[%d] response (%d): %s%n", i, http.getResponseCode(), response);
            }
        }
    }

    public static class GetPostPutServlet extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
        {
            resp.setCharacterEncoding("utf-8");
            resp.setContentType("text/plain");
            resp.getWriter().println("Some small content from GET");
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException
        {
            // Read the request body content entirely (a requirement to maintain connection persistence)
            String requestBody = IO.toString(req.getInputStream(), "utf-8");

            resp.setCharacterEncoding("utf-8");
            resp.setContentType("text/plain");
            resp.getWriter().println("Some small content from POST");
            resp.getWriter().printf("POST request body length: %d%n", requestBody.length());
        }

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException
        {
            // Read the request body content entirely (a requirement to maintain connection persistence)
            String requestBody = IO.toString(req.getInputStream(), "utf-8");

            resp.setCharacterEncoding("utf-8");
            resp.setContentType("text/plain");
            resp.getWriter().println("Some small content from PUT");
            resp.getWriter().printf("PUT request body length: %d%n", requestBody.length());
        }
    }
}
