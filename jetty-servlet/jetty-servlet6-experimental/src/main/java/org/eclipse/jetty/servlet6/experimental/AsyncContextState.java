package org.eclipse.jetty.servlet6.experimental;

import java.io.IOException;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

public class AsyncContextState implements AsyncContext
{
    private final HttpChannel _channel;
    volatile ServletChannel _state;

    public AsyncContextState(ServletChannel servletChannel)
    {
        _state = servletChannel;
        _channel = _state.getHttpChannel();
    }

    public HttpChannel getHttpChannel()
    {
        return _channel;
    }

    ServletChannel state()
    {
        ServletChannel state = _state;
        if (state == null)
            throw new IllegalStateException("AsyncContext completed and/or Request lifecycle recycled");
        return state;
    }

    @Override
    public void addListener(final AsyncListener listener, final ServletRequest request, final ServletResponse response)
    {
        AsyncListener wrap = new WrappedAsyncListener(listener, request, response);
        state().addListener(wrap);
    }

    @Override
    public void addListener(AsyncListener listener)
    {
        state().addListener(listener);
    }

    @Override
    public void complete()
    {
        state().complete();
    }

    @Override
    public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException
    {
        // TODO: Use ServletContextHandler createInstance use DecoratedObjectFactory.
        try
        {
            return clazz.getDeclaredConstructor().newInstance();
        }
        catch (Exception e)
        {
            throw new ServletException(e);
        }
    }

    @Override
    public void dispatch()
    {
        state().dispatch(null, null);
    }

    @Override
    public void dispatch(String path)
    {
        state().dispatch(null, path);
    }

    @Override
    public void dispatch(ServletContext context, String path)
    {
        state().dispatch(context, path);
    }

    @Override
    public ServletRequest getRequest()
    {
        return state().getAsyncContextEvent().getSuppliedRequest();
    }

    @Override
    public ServletResponse getResponse()
    {
        return state().getAsyncContextEvent().getSuppliedResponse();
    }

    @Override
    public long getTimeout()
    {
        return state().getTimeout();
    }

    @Override
    public boolean hasOriginalRequestAndResponse()
    {
        HttpChannel channel = state().getHttpChannel();
        ServletRequest servletRequest = getRequest();
        ServletResponse servletResponse = getResponse();

        if (!(servletRequest instanceof Request) || ((Request)servletRequest).getWrapped() != channel.getRequest())
                return false;
        return servletResponse instanceof Response && ((Response)servletResponse).getWrapped() == channel.getResponse();
    }

    @Override
    public void setTimeout(long timeout)
    {
        state().setTimeout(timeout);
    }

    @Override
    public void start(final Runnable task)
    {
        _channel.getRequest().execute(() ->
        {
            AsyncEvent asyncContextEvent = state().getAsyncContextEvent();
            if (asyncContextEvent instanceof AsyncContextEvent && ((AsyncContextEvent)asyncContextEvent).getContext() != null)
            {
                ((AsyncContextEvent)asyncContextEvent).getContext().run(task);
            }
            else
            {
                task.run();
            }
        });
    }

    public void reset()
    {
        _state = null;
    }

    public ServletChannel getHttpChannelState()
    {
        return state();
    }

    public static class WrappedAsyncListener implements AsyncListener
    {
        private final AsyncListener _listener;
        private final ServletRequest _request;
        private final ServletResponse _response;

        public WrappedAsyncListener(AsyncListener listener, ServletRequest request, ServletResponse response)
        {
            _listener = listener;
            _request = request;
            _response = response;
        }

        public AsyncListener getListener()
        {
            return _listener;
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException
        {
            _listener.onTimeout(new AsyncEvent(event.getAsyncContext(), _request, _response, event.getThrowable()));
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException
        {
            _listener.onStartAsync(new AsyncEvent(event.getAsyncContext(), _request, _response, event.getThrowable()));
        }

        @Override
        public void onError(AsyncEvent event) throws IOException
        {
            _listener.onError(new AsyncEvent(event.getAsyncContext(), _request, _response, event.getThrowable()));
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException
        {
            _listener.onComplete(new AsyncEvent(event.getAsyncContext(), _request, _response, event.getThrowable()));
        }
    }
}
