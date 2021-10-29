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

package org.eclipse.jetty12.server;

import org.eclipse.jetty.util.component.ContainerLifeCycle;

/**
 * The handler API is now asynchronous. If it returns true, then some handler has taken
 * responsibility for calling request succeeded
 */
public interface Handler
{
    boolean handle(Request request, Response response);

    abstract class Abstract extends ContainerLifeCycle implements Handler
    {
    }

    class Nested extends Abstract
    {
        private Handler _next;

        public Handler getNext()
        {
            return _next;
        }

        public void setNext(Handler next)
        {
            updateBean(_next, next);
            _next = next;
        }

        @Override
        public boolean handle(Request request, Response response)
        {
            Handler next = getNext();
            return next != null && next.handle(request, response);
        }
    }
}
