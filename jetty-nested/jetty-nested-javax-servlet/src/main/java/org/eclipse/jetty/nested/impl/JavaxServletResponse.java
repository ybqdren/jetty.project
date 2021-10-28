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

package org.eclipse.jetty.nested.impl;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.nested.api.Request;
import org.eclipse.jetty.nested.api.Response;
import org.eclipse.jetty.util.Callback;

public class JavaxServletResponse implements Response
{

    @Override
    public int getStatus()
    {
        return 0;
    }

    @Override
    public void setStatus(int code)
    {

    }

    @Override
    public HttpFields.Mutable getHeaders()
    {
        return null;
    }

    @Override
    public HttpFields.Mutable getTrailers()
    {
        return null;
    }

    @Override
    public void write(boolean last, Callback callback, ByteBuffer... content)
    {

    }

    @Override
    public void push(MetaData.Request request)
    {

    }

    @Override
    public void whenCommitting(BiConsumer<Request, Response> onCommit)
    {

    }

    @Override
    public boolean isCommitted()
    {
        return false;
    }

    @Override
    public void reset()
    {

    }
}
