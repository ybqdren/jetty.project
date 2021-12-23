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

public class DeferredContentHandler extends Handler.Wrapper
{
    @Override
    public boolean handle(Request request, Response response) throws Exception
    {
        // If no content or content available, then don't delay dispatch
        if (request.getContentLength() <= 0)
            return super.handle(request, response);

        // TODO if the content is a form, asynchronously read the a;; parameters before handling
        //      if the content is multi-part, asynchronous read all parts before handling

        // Otherwise just delay until some content arrives.
        request.demandContent(() ->
        {
            try
            {
                if (!super.handle(request, response))
                    request.failed(new IllegalStateException());
            }
            catch (Exception e)
            {
                request.failed(e);
            }
        });
        return true;
    }
}
