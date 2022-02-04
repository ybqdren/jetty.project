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

package org.eclipse.jetty.servlet6.experimental;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dispatcher implements RequestDispatcher
{
    private static final Logger LOG = LoggerFactory.getLogger(Dispatcher.class);

    /**
     * Dispatch include attribute names
     */
    public static final String __INCLUDE_PREFIX = "jakarta.servlet.include.";

    /**
     * Dispatch include attribute names
     */
    public static final String __FORWARD_PREFIX = "jakarta.servlet.forward.";

    private final ContextHandler _contextHandler;
    private final HttpURI _uri;
    private final String _pathInContext;
    private final String _named;

    public Dispatcher(ContextHandler contextHandler, HttpURI uri, String pathInContext)
    {
        _contextHandler = contextHandler;
        _uri = uri.asImmutable();
        _pathInContext = pathInContext;
        _named = null;
    }

    public Dispatcher(ContextHandler contextHandler, String name) throws IllegalStateException
    {
        _contextHandler = contextHandler;
        _uri = null;
        _pathInContext = null;
        _named = name;
    }

    @Override
    public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        // TODO
    }

    @Override
    public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        // TODO
    }

    @Override
    public String toString()
    {
        return String.format("Dispatcher@0x%x{%s,%s}", hashCode(), _named, _uri);
    }
}
