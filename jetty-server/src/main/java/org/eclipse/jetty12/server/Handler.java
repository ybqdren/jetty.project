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

    class Wrapper<R extends Request> extends Convertor<R, R>
    {
        @Override
        public boolean handle(R request, Response response)
        {
            Handler<R> next = getNext();
            return next != null && next.handle(request, response);
        }
    }

    abstract class Convertor<R extends Request, N extends Request> extends Abstract<R>
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
}
