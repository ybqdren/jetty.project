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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedOperation;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.Destroyable;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 * The handler API is now asynchronous. If it returns true, then some handler has taken
 * responsibility for calling request succeeded
 */
public interface Handler extends LifeCycle, Destroyable
{
    boolean handle(Request request, Response response);

    @ManagedAttribute(value = "the jetty server for this handler", readonly = true)
    Server getServer();

    @ManagedOperation(value = "destroy associated resources", impact = "ACTION")
    @Override
    default void destroy()
    {
    }

    abstract class Abstract extends ContainerLifeCycle implements Handler
    {
        private Server _server;

        @Override
        public Server getServer()
        {
            return _server;
        }

        void setServer(Server server)
        {
            if (_server == server)
                return;
            if (isStarted())
                throw new IllegalStateException(getState());
            _server = server;
        }
    }

    interface Container extends Handler
    {
        /**
         * @return immutable collection of handlers directly contained by this handler.
         */
        @ManagedAttribute("handlers in this container")
        List<Handler> getHandlers();

        /**
         * @param byclass the child handler class to get
         * @return collection of all handlers contained by this handler and it's children of the passed type.
         */
        <T extends Handler> List<T> getChildHandlersByClass(Class<T> byclass);

        /**
         * @param byclass the child handler class to get
         * @param <T> the type of handler
         * @return first handler of all handlers contained by this handler and it's children of the passed type.
         */
        <T extends Handler> T getChildHandlerByClass(Class<T> byclass);
    }

    abstract class AbstractContainer extends Abstract implements Container
    {
        @Override
        public <T extends Handler> List<T> getChildHandlersByClass(Class<T> byclass)
        {
            List<T> list = new ArrayList<>();
            expandHandler(this, list, byclass);
            return list;
        }

        @SuppressWarnings("unchecked")
        protected <H extends Handler> void expandHandler(Handler handler, List<H> list, Class<H> byClass)
        {
            if (!(handler instanceof Container))
                return;

            for (Handler h : ((Container)handler).getHandlers())
            {
                if (byClass == null || byClass.isInstance(h))
                    list.add((H)h);
                expandHandler(h, list, byClass);
            }
        }

        @Override
        public <T extends Handler> T getChildHandlerByClass(Class<T> byclass)
        {
            return findHandler(this, byclass);
        }

        @SuppressWarnings("unchecked")
        protected <H extends Handler> H findHandler(Handler handler, Class<H> byClass)
        {
            if (!(handler instanceof Container))
                return null;

            for (Handler h : ((Container)handler).getHandlers())
            {
                if (byClass == null || byClass.isInstance(h))
                    return ((H)h);
                H c = findHandler(h, byClass);
                if (c != null)
                    return c;
            }
            return null;
        }

        @Override
        void setServer(Server server)
        {
            super.setServer(server);
            for (Handler h : getHandlers())
            {
                if (h instanceof Abstract)
                    ((Abstract)h).setServer(server);
            }
        }

        @Override
        public void destroy()
        {
            if (isRunning())
                throw new IllegalStateException(getState());
            for (Handler h : getHandlers())
                h.destroy();
            super.destroy();
        }
    }

    class Nested extends AbstractContainer
    {
        private Handler _next;

        public Handler getNext()
        {
            return _next;
        }

        public void setNext(Handler next)
        {
            if (next instanceof Abstract)
                ((Abstract)next).setServer(getServer());
            updateBean(_next, next);
            _next = next;
        }

        @Override
        public List<Handler> getHandlers()
        {
            if (_next == null)
                return Collections.emptyList();
            return Collections.singletonList(_next);
        }

        @Override
        public void setServer(Server server)
        {
            super.setServer(server);
            if (_next instanceof Abstract)
                ((Abstract)_next).setServer(getServer());
        }

        @Override
        public boolean handle(Request request, Response response)
        {
            Handler next = getNext();
            return next != null && next.handle(request, response);
        }
    }

    class Collection extends AbstractContainer
    {
        private volatile List<Handler> _handlers = new ArrayList<>();

        @Override
        public boolean handle(Request request, Response response)
        {
            for (Handler h : _handlers)
                if (h.handle(request, response))
                    return true;
            return false;
        }

        @Override
        public List<Handler> getHandlers()
        {
            return _handlers;
        }

        public void setHandlers(Handler... handlers)
        {
            setHandlers(handlers.length == 0 ? null : Arrays.asList(handlers));
        }

        public void setHandlers(List<Handler> handlers)
        {
            List<Handler> list = handlers == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(handlers));

            for (Handler h : list)
                if (h instanceof Abstract)
                    ((Abstract)h).setServer(getServer());

            updateBeans(_handlers, handlers);
            _handlers = list;
        }

        public void addHandler(Handler handler)
        {
            List<Handler> list = new ArrayList<>(getHandlers());
            list.add(handler);
            setHandlers(list);
        }

        public void removeHandler(Handler handler)
        {
            List<Handler> list = new ArrayList<>(getHandlers());
            if (list.remove(handler))
                setHandlers(list);
        }
    }
}
