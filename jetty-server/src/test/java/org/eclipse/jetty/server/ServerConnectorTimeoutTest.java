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

package org.eclipse.jetty.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.logging.StacklessLogging;
import org.eclipse.jetty.server.handler.EchoHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class ServerConnectorTimeoutTest extends ConnectorTimeoutTest
{
    @BeforeEach
    public void init() throws Exception
    {
        ServerConnector connector = new ServerConnector(_server, 1, 1);
        connector.setIdleTimeout(MAX_IDLE_TIME);
        startServer(connector);
    }

    @Test
    public void testStartStopStart()
    {
        assertTimeoutPreemptively(ofSeconds(10), () ->
        {
            _server.stop();
            _server.start();
        });
    }

    @Test
    public void testHttpWriteIdleTimeout() throws Exception
    {
        _connector.setIdleTimeout(500);
        _httpConfiguration.setIdleTimeout(10000);
        configureServer(new EchoHandler());
        Socket client = newSocket(_serverURI.getHost(), _serverURI.getPort());
        client.setSoTimeout(10000);

        assertFalse(client.isClosed());

        final OutputStream os = client.getOutputStream();
        final InputStream is = client.getInputStream();
        final StringBuilder response = new StringBuilder();

        CompletableFuture<Void> responseFuture = CompletableFuture.runAsync(() ->
        {
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
            {
                int c;
                while ((c = reader.read()) != -1)
                {
                    response.append((char)c);
                }
            }
            catch (IOException e)
            {
                // Valid path (as connection is forcibly closed)
                // t.printStackTrace(System.err);
            }
        });

        CompletableFuture<Void> requestFuture = CompletableFuture.runAsync(() ->
        {
            try
            {
                os.write((
                    "POST /echo HTTP/1.0\r\n" +
                        "host: " + _serverURI.getHost() + ":" + _serverURI.getPort() + "\r\n" +
                        "content-type: text/plain; charset=utf-8\r\n" +
                        "content-length: 20\r\n" +
                        "\r\n").getBytes(StandardCharsets.UTF_8));
                os.flush();

                os.write("123456789\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                TimeUnit.SECONDS.sleep(1);
                os.write("=========\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            catch (InterruptedException | IOException e)
            {
                // Valid path, as write of second half of content can fail
                // e.printStackTrace(System.err);
            }
        });

        try (StacklessLogging ignore = new StacklessLogging(HttpChannel.class))
        {
            requestFuture.get(5, TimeUnit.SECONDS);
            responseFuture.get(6, TimeUnit.SECONDS);

            assertThat(response.toString(), containsString(" 200 "));
            assertThat(response.toString(), containsString("Content-Length: 20"));
            assertThat(response.toString(), not(containsString("=========")));
        }
    }
}
