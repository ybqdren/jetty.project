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

package org.eclipse.jetty.core.server.handler;

import org.eclipse.jetty.core.server.Handler;
import org.eclipse.jetty.core.server.Processor;
import org.eclipse.jetty.core.server.Request;
import org.eclipse.jetty.http.HttpHeader;

public class DelayUntilContentHandler extends Handler.Wrapper
{
    @Override
    public void accept(Request request) throws Exception
    {
        // If no content or content available, then don't delay dispatch.
        if (request.getContentLength() <= 0 && !request.getHttpFields().contains(HttpHeader.CONTENT_TYPE))
        {
            super.accept(request);
        }
        else
        {
            super.accept(new DelayUntilContentRequest(request));
        }
    }

    private static class DelayUntilContentRequest extends Request.Wrapper
    {
        private boolean _accepted;

        private DelayUntilContentRequest(Request delegate)
        {
            super(delegate);
        }

        @Override
        public void accept(Processor processor) throws Exception
        {
            // The nested Handler is accepting the exchange.

            // Mark as accepted.
            _accepted = true;

            // Accept the original request.
            getWrapped().accept((rq, rs) ->
            {
                // Implicitly demand for content.
                rq.read((req, res) ->
                {
                    // When the content is available, process the nested exchange.
                    processor.process(req, res);
                });
            });
        }

        @Override
        public boolean isAccepted()
        {
            return _accepted;
        }
    }
}
