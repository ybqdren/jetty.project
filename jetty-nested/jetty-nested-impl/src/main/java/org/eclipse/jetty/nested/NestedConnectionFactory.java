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

import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;

public class NestedConnectionFactory implements ConnectionFactory
{
    private static final String PROTOCOL = "nested-jetty";

    @Override
    public String getProtocol()
    {
        return PROTOCOL;
    }

    @Override
    public List<String> getProtocols()
    {
        return Collections.singletonList(PROTOCOL);
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint)
    {
        return new NestedConnection((NestedConnector)connector, (NestedEndpoint)endPoint);
    }
}
