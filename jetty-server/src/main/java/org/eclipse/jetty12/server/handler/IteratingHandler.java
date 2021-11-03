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

package org.eclipse.jetty12.server.handler;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class IteratingHandler extends Handler.Abstract
{
    @Override
    public boolean handle(Request request, Response response)
    {
        response.setStatus(200);

        new IteratingCallback()
        {
            final AtomicInteger _state = new AtomicInteger(2);
            @Override
            protected Action process()
            {
                switch (_state.getAndDecrement())
                {
                    case 2:
                        response.write(false, this, BufferUtil.toBuffer("hello "));
                        return Action.SCHEDULED;
                    case 1:
                        response.write(true, this, BufferUtil.toBuffer("world"));
                        return Action.SCHEDULED;
                    default:
                        return Action.SUCCEEDED;
                }
            }

            @Override
            protected void onCompleteSuccess()
            {
                request.succeeded();
            }

            @Override
            protected void onCompleteFailure(Throwable cause)
            {
                request.failed(cause);
            }
        }.iterate();

        return true;
    }
}
