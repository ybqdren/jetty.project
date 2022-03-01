//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.ee10.handler;

import java.util.ArrayList;

import org.eclipse.jetty.server.RequestLog;

import static java.util.Arrays.asList;

public class RequestLogCollection
    implements RequestLog
{
    private final ArrayList<RequestLog> delegates;

    public RequestLogCollection(RequestLog... requestLogs)
    {
        delegates = new ArrayList<>(asList(requestLogs));
    }

    public void add(RequestLog requestLog)
    {
        delegates.add(requestLog);
    }

    @Override
    public void log(Request request, Response response)
    {
        for (RequestLog delegate : delegates)
        {
            delegate.log(request, response);
        }
    }
}
