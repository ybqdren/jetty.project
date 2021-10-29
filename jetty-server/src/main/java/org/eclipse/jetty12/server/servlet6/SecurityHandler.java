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

package org.eclipse.jetty12.server.servlet6;

import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class SecurityHandler extends Handler.Nested
{
    @Override
    public boolean handle(Request request, Response response)
    {
        ServletScopedRequest.MutableHttpServletRequest servletRequest =
            request.get(ServletScopedRequest.class, ServletScopedRequest::getMutableHttpServletRequest);
        if (servletRequest == null)
            return false;

        // if we match some security constraint, we can respond here
        if (servletRequest.getServletPath().startsWith("/secret"))
        {
            try
            {
                servletRequest.getHttpServletResponse().sendError(403);
            }
            catch (Exception e)
            {
                request.failed(e);
                return true;
            }
            // Fall through to super.handle, so ServletHandler will be called and will see the sendError and act on it
        }

        return super.handle(request, response);
    }
}
