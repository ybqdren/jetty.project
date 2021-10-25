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
public interface Handler<R extends Request>
{
    boolean handle(R request, Response response);

    abstract class Abstract<R extends Request> extends ContainerLifeCycle implements Handler<R>
    {
    }

    abstract class Processor<R extends Request, N extends Request> extends Abstract<R>
    {
        private Handler<N> _next;

        public Handler<N> getNext()
        {
            return _next;
        }

        public void setNext(Handler<N> next)
        {
            updateBean(_next, next);
            _next = next;
        }
    }

    class Wrapper<R extends Request> extends Processor<R, R>
    {
        @Override
        public boolean handle(R request, Response response)
        {
            Handler<R> next = getNext();
            return next != null && next.handle(request, response);
        }
    }
}
