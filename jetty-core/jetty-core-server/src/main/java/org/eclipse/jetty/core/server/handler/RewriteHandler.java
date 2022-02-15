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
import org.eclipse.jetty.core.server.Response;

public class RewriteHandler extends Handler.Wrapper
{
    // TODO
//    private final RuleContainer _rules;

    @Override
    public void accept(Request request) throws Exception
    {
        super.accept(new RewriteRequest(request));
    }

    private class RewriteRequest extends Request.Wrapper
    {
        private boolean _accepted;

        private RewriteRequest(Request delegate)
        {
            super(delegate);
        }

        @Override
        public void accept(Processor processor) throws Exception
        {
            _accepted = true;

            getWrapped().accept((rq, rs) ->
            {
                matchAndApply(rq, rs);
                if (!rq.isComplete())
                {
                    // TODO: probably request needs wrapping for URI and headers.
                    processor.process(rq, rs);
                }
            });
        }

        @Override
        public boolean isAccepted()
        {
            return _accepted;
        }

        private void matchAndApply(Request request, Response response)
        {
            // TODO: use _rules field here.
        }
    }
}
