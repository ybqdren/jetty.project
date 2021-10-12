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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class NestedHandler extends AbstractHandler
{
    private final Server _server;
    private final NestedConnector _connector;

    public NestedHandler()
    {
        _server = new Server();
        _connector = new NestedConnector(_server);
        _server.addConnector(_connector);
    }

    public Server getNestedServer()
    {
        return _server;
    }

    @Override
    protected void doStart() throws Exception
    {
        // Manage LifeCycle manually as eventually this will be shaded and won't implement the same LifeCycle as the Handler.
        _server.start();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception
    {
        _server.stop();
        super.doStop();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        baseRequest.setHandled(true);
        _connector.service(new JakartaHandlerRequestResponse(baseRequest));
    }
}
