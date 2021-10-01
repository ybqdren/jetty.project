//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.nested;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringRequestContent;
import org.eclipse.jetty.nested.NestedHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.IO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class NestedConnectorTest
{
    private static Server _server;
    private static ServerConnector _connector;
    private static HttpClient _httpClient;

    @BeforeAll
    public static void before() throws Exception
    {
        _server = new Server();
        _connector = new ServerConnector(_server);
        _server.addConnector(_connector);

        // Create a servlet which nests a Jetty server.
        NestedHandler nestedHandler = new NestedHandler();
        nestedHandler.getNestedServer().setHandler(new TestHandler());
        _server.setHandler(nestedHandler);

        // Start server and client.
        _server.start();
        _httpClient = new HttpClient();
        _httpClient.start();
    }

    @AfterAll
    public static void after() throws Exception
    {
        _httpClient.stop();
        _server.stop();
    }

    public static class TestHandler extends AbstractHandler
    {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
        {
            baseRequest.setHandled(true);
            ServletInputStream inputStream = request.getInputStream();
            String requestContent = IO.toString(inputStream);
            PrintWriter writer = response.getWriter();
            writer.println("we got the request content: ");
            writer.println(requestContent);
        }
    }

    @Test
    public void testPost() throws Exception
    {
        URI uri = URI.create("http://localhost:" + _connector.getLocalPort());
        ContentResponse response = _httpClient.POST(uri).body(new StringRequestContent("this is the request content")).send();
        System.err.println(response.getHeaders());
        System.err.println(response.getContentAsString());
    }
}
