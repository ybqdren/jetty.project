package org.eclipse.jetty.servlet6.experimental;

import java.net.CookieStore;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.thread.AutoLock;

public class ServletRequestState
{

    /*
     * The state of the HttpChannel,used to control the overall lifecycle.
     * <pre>
     *     IDLE <-----> HANDLING ----> WAITING
     *       |                 ^       /
     *       |                  \     /
     *       v                   \   v
     *    UPGRADED               WOKEN
     * </pre>
     */
    public enum State
    {
        IDLE,        // Idle request
        HANDLING,    // Request dispatched to filter/servlet or Async IO callback
        WAITING,     // Suspended and waiting
        WOKEN,       // Dispatch to handle from ASYNC_WAIT
        UPGRADED     // Request upgraded the connection
    }

    /*
     * The state of the request processing lifecycle.
     * <pre>
     *       BLOCKING <----> COMPLETING ---> COMPLETED
     *       ^  |  ^            ^
     *      /   |   \           |
     *     |    |    DISPATCH   |
     *     |    |    ^  ^       |
     *     |    v   /   |       |
     *     |  ASYNC -------> COMPLETE
     *     |    |       |       ^
     *     |    v       |       |
     *     |  EXPIRE    |       |
     *      \   |      /        |
     *       \  v     /         |
     *       EXPIRING ----------+
     * </pre>
     */
    private enum RequestState
    {
        BLOCKING,    // Blocking request dispatched
        ASYNC,       // AsyncContext.startAsync() has been called
        DISPATCH,    // AsyncContext.dispatch() has been called
        EXPIRE,      // AsyncContext timeout has happened
        EXPIRING,    // AsyncListeners are being called
        COMPLETE,    // AsyncContext.complete() has been called
        COMPLETING,  // Request is being closed (maybe asynchronously)
        COMPLETED    // Response is completed
    }

    public enum Action
    {
        COMPLETE,
        WAIT,
        DISPATCH,
        READ_CALLBACK,
        SEND_ERROR,
        TERMINATED
    }

    private State _state = State.IDLE;
    private RequestState _requestState = RequestState.BLOCKING;

    private final AutoLock _lock = new AutoLock();
    private final ServletContextContext _servletContextContext;
    private ServletScopedRequest _servletScopedRequest;
//    private HttpInput _httpInput; // TODO
//    private HttpWriter _httpWriter; // TODO
    private CookieStore _cookieCache; // TODO
    private boolean _initial;

    ServletRequestState(ServletContextContext servletContextContext)
    {
        _servletContextContext = servletContextContext;
    }

    AutoLock lock()
    {
        return _lock.lock();
    }

    public boolean isAsyncStarted()
    {
        return false;
    }

    public boolean onReadReady()
    {
        return false;
    }

    public void sendError(int sc, String msg)
    {

    }

    void setServletScopedRequest(ServletScopedRequest servletScopedRequest)
    {
        _servletScopedRequest = servletScopedRequest;
    }

    ServletScopedRequest getServletScopedRequest()
    {
        return _servletScopedRequest;
    }

    public ServletContextContext getServletContextContext()
    {
        return _servletContextContext;
    }

    public ServletContext getServletContext()
    {
        return _servletContextContext;
    }

    public HttpChannel getHttpChannel()
    {
        return _servletScopedRequest == null ? null : _servletScopedRequest.getChannel();
    }

    public void handle()
    {
        // implement the state machine from HttpChannelState and HttpChannel
        // Note that sendError may already have been called before we are handling for the first time.

        Action action =  handling();
        loop: while (true)
        {
            try
            {
                switch (action)
                {
                    case COMPLETE:
                        _servletScopedRequest.succeeded();
                        break loop;

                    case WAIT:
                        break;

                    case DISPATCH:
                        // TODO: filters and customizers.
                        _servletScopedRequest._mappedServlet.handle(_servletScopedRequest.getHttpServletRequest(), _servletScopedRequest.getHttpServletResponse());
                        break;

                    case READ_CALLBACK:
                        // TODO need to actually do a readContent here so that we can run interceptors and check there really
                        //      is data... but ultimately we do:
                        _servletScopedRequest._readListener.onDataAvailable();
                        break;

                    case SEND_ERROR:
                        // TODO send error
                        break;

                    case TERMINATED:
                        break loop;

                    // TODO etc.
                    default:
                        throw new IllegalStateException(action.name());
                }
            }
            catch (Throwable failure)
            {
                // TODO
                failure.printStackTrace();
            }

            action = unhandle();
        }
    }

    private Action handling()
    {
        try (AutoLock l = lock())
        {
            switch (_state)
            {
                case IDLE:
                    _initial = true;
                    return nextAction(true);
                default:
                    throw new IllegalStateException();
            }
        }
    }

    private Action unhandle()
    {
        try (AutoLock l = lock())
        {
            if (_state != State.HANDLING)
                throw new IllegalStateException();

            _initial = false;
            return nextAction(false);
        }
    }

    private Action nextAction(boolean handling)
    {
        _state = State.HANDLING;

        if (_initial)
            return Action.DISPATCH;

        switch (_requestState)
        {
            case BLOCKING:
                if (handling)
                    throw new IllegalStateException();
                _requestState = RequestState.COMPLETING;
                return Action.COMPLETE;

            case COMPLETING:
                _state = State.IDLE;
                return Action.COMPLETE;

            default:
                throw new IllegalStateException(_requestState.name());
        }
    }

    public void addListener(AsyncListener listener)
    {

    }

    public void complete()
    {

    }

    // This is for async dispatch.
    public void dispatch(ServletContext context, String path)
    {

    }

    public AsyncEvent getAsyncContextEvent()
    {
        return null;
    }

    public ServletContext getContext()
    {
        return null;
    }

    public ContextHandler getContextHandler()
    {
        return null;
    }

    public long getTimeout()
    {
        return 0;
    }

    public void setTimeout(long arg0)
    {

    }

    public void startAsync(AsyncContextEvent event)
    {

    }

    public void timeout()
    {

    }
}
