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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;

public class JettyNestedJavaxServlet extends HttpServlet
{
    private final Server _server;
    private final NestedConnector _connector;

    public JettyNestedJavaxServlet()
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
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        _connector.service(new JavaxServletRequestResponse(req, resp));
    }
}
