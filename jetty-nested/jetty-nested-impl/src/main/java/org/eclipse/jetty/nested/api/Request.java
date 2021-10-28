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

package org.eclipse.jetty.nested.api;

import java.util.function.Consumer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.Callback;

public interface Request extends Attributes, Callback
{
    String getId();

//    Channel getChannel();
//
//    ConnectionMetaData getConnectionMetaData();

    String getMethod();

    HttpURI getURI();

    HttpFields getHeaders();

    long getContentLength();

    Content readContent();

    void demandContent(Runnable onContentAvailable);

    void onTrailers(Consumer<HttpFields> onTrailers);

    default Request getWrapped()
    {
        return null;
    }

    Request getWrapper();

    default Request unwrap()
    {
        Request r = this;
        while (true)
        {
            Request w = r.getWrapper();
            if (w == null)
                return r;
            r = w;
        }
    }

    class Wrapper extends Attributes.Wrapper implements Request
    {
        private final Request _wrapped;

        protected Wrapper(Request wrapped)
        {
            super(wrapped);
            this._wrapped = wrapped;
            Request base = wrapped.unwrap();
            ((Base)base).setWrapper(this);
        }

        @Override
        public String getId()
        {
            return _wrapped.getId();
        }

        @Override
        public String getMethod()
        {
            return _wrapped.getMethod();
        }

        @Override
        public HttpURI getURI()
        {
            return _wrapped.getURI();
        }

        @Override
        public HttpFields getHeaders()
        {
            return _wrapped.getHeaders();
        }

        @Override
        public long getContentLength()
        {
            return _wrapped.getContentLength();
        }

        @Override
        public Content readContent()
        {
            return _wrapped.readContent();
        }

        @Override
        public void demandContent(Runnable onContentAvailable)
        {
            _wrapped.demandContent(onContentAvailable);
        }

        @Override
        public void onTrailers(Consumer<HttpFields> onTrailers)
        {
            _wrapped.onTrailers(onTrailers);
        }

        @Override
        public Request getWrapped()
        {
            return _wrapped;
        }

        @Override
        public Request getWrapper()
        {
            return _wrapped.getWrapper();
        }

        @Override
        public void succeeded()
        {
            _wrapped.succeeded();
        }

        @Override
        public void failed(Throwable x)
        {
            _wrapped.failed(x);
        }

        @Override
        public InvocationType getInvocationType()
        {
            return _wrapped.getInvocationType();
        }

    }

    interface Base extends Request
    {
        void setWrapper(Request wrapper);
    }
}
