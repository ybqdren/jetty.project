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

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

public class SecurityHandler extends Handler.Wrapper
{
    @Override
    public void handle(Request request) throws Exception
    {
        ServletScopedRequest.MutableHttpServletRequest servletRequest =
            request.get(ServletScopedRequest.class, ServletScopedRequest::getMutableHttpServletRequest);
        if (servletRequest == null)
            return;

        // if we match some security constraint, we can respond here
        if (servletRequest.getServletPath().startsWith("/secret"))
        {
            Response response = request.getResponse();
            response.writeError(403, response.getCallback());
            return;
        }

        super.handle(request);
    }
}
