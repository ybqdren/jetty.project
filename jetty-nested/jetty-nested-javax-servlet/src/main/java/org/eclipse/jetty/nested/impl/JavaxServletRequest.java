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

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

import javax.servlet.AsyncContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.nested.api.Content;
import org.eclipse.jetty.nested.api.Request;

public class JavaxServletRequest implements Request
{
    private static final int BUFFER_SIZE = 1024;

    private final HttpServletRequest _httpServletRequest;
    private final ServletInputStream _inputStream;
    private AsyncContext _asyncContext;

    public JavaxServletRequest(HttpServletRequest httpServletRequest) throws IOException
    {
        _httpServletRequest = httpServletRequest;
        _inputStream = httpServletRequest.getInputStream();
    }

    @Override
    public String getId()
    {
        return null; // TODO: ??
    }

    @Override
    public String getMethod()
    {
        return _httpServletRequest.getMethod();
    }

    // TODO: this had a Jetty Specific type in it
    @Override
    public URI getURI()
    {
        return URI.create(_httpServletRequest.getRequestURI());
    }

    @Override
    public HttpFields getHeaders()
    {
        return null;
    }

    @Override
    public long getContentLength()
    {
        return 0;
    }

    @Override
    public Content readContent()
    {
        return null;
    }

    @Override
    public void demandContent(Runnable onContentAvailable)
    {

    }

    @Override
    public void onTrailers(Consumer<HttpFields> onTrailers)
    {

    }

    @Override
    public Request getWrapper()
    {
        return null;
    }

    @Override
    public void removeAttribute(String name)
    {

    }

    @Override
    public void setAttribute(String name, Object attribute)
    {

    }

    @Override
    public Object getAttribute(String name)
    {
        return null;
    }

    @Override
    public Set<String> getAttributeNameSet()
    {
        return Set.copyOf(Collections.list(_httpServletRequest.getAttributeNames()));
    }

    @Override
    public void clearAttributes()
    {

    }
}
