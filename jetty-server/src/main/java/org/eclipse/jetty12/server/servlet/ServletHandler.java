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

package org.eclipse.jetty12.server.servlet;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.ServletPathMapping;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Response;

public class ServletHandler extends Handler.Abstract<ServletScopedRequest>
{
    public interface MappedServlet
    {
        ServletPathMapping getServletPathMapping();

        void handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
    }

    public MappedServlet findMapping(String pathInContext)
    {
        return null;
    }

    @Override
    public boolean handle(ServletScopedRequest request, Response response)
    {
        MappedServlet mappedServlet = request.getMappedServlet();
        if (mappedServlet == null)
            return false; // TODO or 404?

        request.handle();
        return true;
    }
}
