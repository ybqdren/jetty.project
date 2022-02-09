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

public interface Incoming extends IncomingRequest
{
    void accept(Processor processor) throws Exception;

    boolean isAccepted();

    class Wrapper extends IncomingRequest.Wrapper implements Incoming
    {
        public Wrapper(Incoming delegate)
        {
            super(delegate);
        }

        public Incoming getWrapped()
        {
            return (Incoming)super.getWrapped();
        }

        @Override
        public void accept(Processor processor) throws Exception
        {
            getWrapped().accept(processor);
        }

        @Override
        public boolean isAccepted()
        {
            return getWrapped().isAccepted();
        }
    }
}
