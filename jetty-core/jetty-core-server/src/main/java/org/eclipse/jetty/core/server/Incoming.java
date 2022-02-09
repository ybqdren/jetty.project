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

package org.eclipse.jetty.core.server;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.util.Attributes;

public interface Incoming extends Attributes
{
    String getId();

    ConnectionMetaData getConnectionMetaData();

    String getMethod();

    String getTarget();

    HttpURI getHttpURI();

    HttpFields getHttpFields();

    long getContentLength();

    void accept(Processor processor) throws Exception;

    boolean isAccepted();

    class Wrapper extends Attributes.Wrapper implements Incoming
    {
        private final Incoming delegate;

        public Wrapper(Incoming delegate)
        {
            super(delegate);
            this.delegate = delegate;
        }

        public Incoming getWrapped()
        {
            return delegate;
        }

        @Override
        public String getId()
        {
            return delegate.getId();
        }

        @Override
        public ConnectionMetaData getConnectionMetaData()
        {
            return delegate.getConnectionMetaData();
        }

        @Override
        public String getMethod()
        {
            return delegate.getMethod();
        }

        @Override
        public String getTarget()
        {
            return delegate.getTarget();
        }

        @Override
        public HttpURI getHttpURI()
        {
            return delegate.getHttpURI();
        }

        @Override
        public HttpFields getHttpFields()
        {
            return delegate.getHttpFields();
        }

        @Override
        public long getContentLength()
        {
            return delegate.getContentLength();
        }

        @Override
        public void accept(Processor processor) throws Exception
        {
            delegate.accept(processor);
        }

        @Override
        public boolean isAccepted()
        {
            return delegate.isAccepted();
        }
    }
}
