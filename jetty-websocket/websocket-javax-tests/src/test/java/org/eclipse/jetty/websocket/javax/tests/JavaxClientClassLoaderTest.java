//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.javax.tests;

import java.net.URI;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpoint;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.Configurations;
import org.eclipse.jetty.websocket.core.WebSocketComponents;
import org.eclipse.jetty.websocket.core.client.CoreClientUpgradeRequest;
import org.eclipse.jetty.websocket.javax.client.JavaxWebSocketClientContainerProvider;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketContainer;
import org.eclipse.jetty.websocket.javax.server.config.JavaxWebSocketConfiguration;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavaxClientClassLoaderTest
{
    private WSServer server;
    private HttpClient httpClient;

    @FunctionalInterface
    interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    public void start(ThrowingRunnable configuration) throws Exception
    {
        server = new WSServer();
        configuration.run();
        server.start();
        httpClient = new HttpClient();
        httpClient.start();
    }

    @AfterEach
    public void after() throws Exception
    {
        httpClient.stop();
        server.stop();
    }

    @ClientEndpoint()
    public static class ClientSocket
    {
        LinkedBlockingQueue<String> textMessages = new LinkedBlockingQueue<>();

        @OnOpen
        public void onOpen(Session session)
        {
            session.getAsyncRemote().sendText("ContextClassLoader: " + Thread.currentThread().getContextClassLoader());
        }

        @OnMessage
        public void onMessage(String message)
        {
            textMessages.add(message);
        }
    }

    @WebServlet("/servlet")
    public static class WebSocketClientServlet extends HttpServlet
    {
        private final WebSocketContainer clientContainer = ContainerProvider.getWebSocketContainer();

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        {
            URI wsEchoUri = URI.create("ws://localhost:" + req.getServerPort() + "/echo/");
            ClientSocket clientSocket = new ClientSocket();

            try (Session ignored = clientContainer.connectToServer(clientSocket, wsEchoUri))
            {
                String recv = clientSocket.textMessages.poll(5, TimeUnit.SECONDS);
                assertNotNull(recv);
                resp.setStatus(HttpStatus.OK_200);
                resp.getWriter().println(recv);
                resp.getWriter().println("ClientClassLoader: " + clientContainer.getClass().getClassLoader());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @ServerEndpoint("/")
    public static class EchoSocket
    {
        @OnMessage
        public void onMessage(Session session, String message) throws Exception
        {
            session.getBasicRemote().sendText(message);
        }
    }

    public WSServer.WebApp createWebSocketWebapp(String contextName) throws Exception
    {
        WSServer.WebApp app = server.createWebApp(contextName);

        // Exclude the Javax WebSocket configuration from the webapp (so we use versions from the webapp).
        Configuration[] configurations = Configurations.getKnown().stream()
            .filter(configuration -> !(configuration instanceof JavaxWebSocketConfiguration))
            .toArray(Configuration[]::new);
        app.getWebAppContext().setConfigurations(configurations);

        // Copy over the individual jars required for Javax WebSocket.
        app.createWebInf();
        app.copyLib(JavaxWebSocketClientContainerProvider.class, "websocket-javax-client.jar");
        app.copyLib(JavaxWebSocketContainer.class, "websocket-javax-common.jar");
        app.copyLib(ContainerLifeCycle.class, "jetty-util.jar");
        app.copyLib(CoreClientUpgradeRequest.class, "websocket-core-client.jar");
        app.copyLib(WebSocketComponents.class, "websocket-core-common.jar");
        app.copyLib(Response.class, "jetty-client.jar");
        app.copyLib(ByteBufferPool.class, "jetty-io.jar");
        app.copyLib(BadMessageException.class, "jetty-http.jar");
        app.copyLib(XmlConfiguration.class, "jetty-xml.jar");

        return app;
    }

    @Test
    public void websocketProvidedByServer() throws Exception
    {
        start(() ->
        {
            WSServer.WebApp app1 = server.createWebApp("app");
            app1.createWebInf();
            app1.copyClass(WebSocketClientServlet.class);
            app1.copyClass(ClientSocket.class);
            app1.deploy();

            WSServer.WebApp app2 = server.createWebApp("echo");
            app2.createWebInf();
            app2.copyClass(EchoSocket.class);
            app2.deploy();
        });

        // After hitting each WebApp we will get 200 response if test succeeds.
        ContentResponse response = httpClient.GET(server.getServerUri().resolve("/app/servlet"));
        assertThat(response.getStatus(), is(HttpStatus.OK_200));

        // The ContextClassLoader in the WebSocketClients onOpen was the WebAppClassloader.
        assertThat(response.getContentAsString(), containsString("ContextClassLoader: WebAppClassLoader"));

        // Verify that we used Servers version of WebSocketClient.
        ClassLoader serverClassLoader = server.getServer().getClass().getClassLoader();
        assertThat(response.getContentAsString(), containsString("ClientClassLoader: " + serverClassLoader));    }

    @Test
    public void websocketProvidedByWebApp() throws Exception
    {
        start(() ->
        {
            WSServer.WebApp app1 = createWebSocketWebapp("app");
            app1.createWebInf();
            app1.copyClass(WebSocketClientServlet.class);
            app1.copyClass(ClientSocket.class);
            app1.copyClass(EchoSocket.class);
            app1.deploy();

            // Do not exclude JavaxWebSocketConfiguration for this webapp (we need the websocket server classes).
            WSServer.WebApp app2 = server.createWebApp("echo");
            app2.createWebInf();
            app2.copyClass(EchoSocket.class);
            app2.deploy();
        });

        // After hitting each WebApp we will get 200 response if test succeeds.
        ContentResponse response = httpClient.GET(server.getServerUri().resolve("/app/servlet"));
        assertThat(response.getStatus(), is(HttpStatus.OK_200));

        // The ContextClassLoader in the WebSocketClients onOpen was the WebAppClassloader.
        assertThat(response.getContentAsString(), containsString("ContextClassLoader: WebAppClassLoader"));

        // Verify that we used WebApps version of WebSocketClient.
        assertThat(response.getContentAsString(), containsString("ClientClassLoader: WebAppClassLoader"));
    }
}
