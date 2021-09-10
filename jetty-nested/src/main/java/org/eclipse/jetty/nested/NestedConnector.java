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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;

public class NestedConnector extends AbstractConnector
{
    HttpConfiguration _httpConfiguration = new HttpConfiguration();

    public NestedConnector(Server server)
    {
        super(server, null, null, null, 0, new NestedConnectionFactory());
        _httpConfiguration.setSendDateHeader(false);
        _httpConfiguration.setSendServerVersion(false);
        _httpConfiguration.setSendXPoweredBy(false);
    }

    public HttpConfiguration getHttpConfiguration()
    {
        return _httpConfiguration;
    }

    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        // TODO: recover existing endpoint and connection from WeakReferenceMap with request as key, or some other way of doing persistent connection.
        //  There is a proposal in the servlet spec to have connection IDs.
        NestedEndpoint endPoint = new NestedEndpoint(request, response);
        NestedConnection connection = (NestedConnection)getDefaultConnectionFactory().newConnection(this, endPoint);
        endPoint.setConnection(connection);
        connection.handle();
    }

    @Override
    public Object getTransport()
    {
        return null;
    }

    @Override
    protected void accept(int acceptorID) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("Accept not supported by this Connector");
    }
}
