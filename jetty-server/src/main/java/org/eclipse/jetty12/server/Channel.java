package org.eclipse.jetty12.server;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.AttributesMap;
import org.eclipse.jetty.util.Callback;

/**
 * A Channel represents a sequence of request cycles from the same connection. However only a single
 * request cycle may be active at once for each channel.    This is some, but not all of the
 * behaviour of the current HttpChannel class, specifically it does not include the mutual exclusion
 * of handling required by the servlet spec and currently encapsulated in HttpChannelState.
 *
 * Note how Runnables are returned to indicate that further work is needed. These
 * can be given to an ExecutionStrategy instead of calling known methods like HttpChannel.handle().
 */
public class Channel extends AttributesMap
{
    private final Handler<Request> _server;
    private final Transport _transport;
    private final ChannelRequest _request;
    private final ChannelResponse _response;
    private Exchange _exchange;

    public Channel(Handler<Request> server, Transport transport)
    {
        _server = server;
        _transport = transport;
        _request = new ChannelRequest();
        _response = new ChannelResponse();
    }

    public Transport getTransport()
    {
        return _transport;
    }

    public Runnable onRequest(Exchange exchange)
    {
        _exchange = exchange;
        return this::handle;
    }

    private void handle()
    {
        if (!_server.handle(_request, _response))
        {
            _response.reset();
            _response.setCode(404);
            _request.succeeded();
        }
    }

    public Runnable onContentAvailable()
    {
        return _request._onContent.getAndSet(null);
    }

    public Runnable onRequestComplete(HttpFields trailers)
    {
        Object consumer = _request._onTrailers.getAndSet(trailers);
        if (consumer != null)
            ((Consumer<HttpFields>)consumer).accept(trailers);
        return null;
    }

    private class ChannelRequest extends AttributesMap implements Request
    {
        final AtomicReference<Runnable> _onContent = new AtomicReference<>();
        final AtomicReference<Object> _onTrailers = new AtomicReference<>();
        final AtomicReference<BiConsumer<Request, Throwable>> _onComplete = new AtomicReference<>();

        @Override
        public String getId()
        {
            return null;
        }

        @Override
        public Channel getChannel()
        {
            return Channel.this;
        }

        @Override
        public MetaData.Request getMetaData()
        {
            return _exchange.getMetaData();
        }

        @Override
        public Content readContent()
        {
            return _exchange.readContent();
        }

        @Override
        public void demandContent(Runnable onContentAvailable)
        {
            Runnable task = _onContent.getAndSet(onContentAvailable);
            if (task != null && task != onContentAvailable)
                throw new IllegalStateException();
            _exchange.demandContent();
        }

        @Override
        public void onTrailers(Consumer<HttpFields> onTrailers)
        {
            Object trailers = _onTrailers.getAndSet(onTrailers);
            if (trailers != null)
                onTrailers.accept((HttpFields)trailers);
        }

        @Override
        public void whenComplete(BiConsumer<Request, Throwable> onComplete)
        {
            if (!_onComplete.compareAndSet(null, onComplete))
            {
                _onComplete.getAndUpdate(l -> (request, failed) ->
                {
                    onComplete(l, failed);
                    onComplete(onComplete, failed);
                });
            }
        }

        @Override
        public void succeeded()
        {
            onComplete(_onComplete.getAndSet(null), null);
            _exchange.succeeded();
        }

        @Override
        public void failed(Throwable x)
        {
            onComplete(_onComplete.getAndSet(null), x == null ? new Throwable() : x);
            _exchange.failed(x);
        }

        private void onComplete(BiConsumer<Request, Throwable> onComplete, Throwable failed)
        {
            if (onComplete != null)
            {
                try
                {
                    onComplete.accept(_request, failed);
                }
                catch (Throwable t)
                {
                    t.printStackTrace();
                }
            }
        }

        @Override
        public InvocationType getInvocationType()
        {
            return _exchange.getInvocationType();
        }
    }

    private class ChannelResponse implements Response
    {
        private final AtomicReference<BiConsumer<Request, Response>> _onCommit = new AtomicReference<>();
        int _code;
        HttpFields.Mutable _fields = HttpFields.build();
        HttpFields.Mutable _trailers;
        boolean _committed;

        @Override
        public int getCode()
        {
            return _code;
        }

        @Override
        public void setCode(int code)
        {
            _code = code;
        }

        @Override
        public HttpFields.Mutable getHttpFields()
        {
            return _fields;
        }

        @Override
        public HttpFields.Mutable getTrailers()
        {
            if (_trailers == null)
                _trailers = HttpFields.build();
            return _trailers;
        }

        @Override
        public void write(boolean last, Callback callback, ByteBuffer... content)
        {
            MetaData.Response metaData = null;
            if (!_committed)
            {
                onCommit(_onCommit.getAndSet(null));
                _committed = true;
                metaData = new MetaData.Response(
                    Channel.this._exchange.getMetaData().getHttpVersion(),
                    _code,
                    null,
                    _fields.asImmutable(),
                    -1,
                    this::takeTrailers);
            }
            _exchange.send(metaData, last, callback, content);
        }

        private HttpFields takeTrailers()
        {
            return _trailers == null ? null : _trailers.asImmutable();
        }

        @Override
        public void push(MetaData.Request request)
        {
            _exchange.push(request);
        }

        @Override
        public void whenCommit(BiConsumer<Request, Response> onCommit)
        {
            if (!_onCommit.compareAndSet(null, onCommit))
            {
                _onCommit.getAndUpdate(l -> (request, response) ->
                {
                    onCommit(l);
                    onCommit(onCommit);
                });
            }
        }

        @Override
        public boolean isCommitted()
        {
            return _committed;
        }

        @Override
        public void reset()
        {
            // TODO re-add or don't delete default fields
            _fields.clear();
            _code = 0;
        }

        private void onCommit(BiConsumer<Request, Response> onCommit)
        {
            if (onCommit != null)
            {
                try
                {
                    onCommit.accept(_request, _response);
                }
                catch (Throwable t)
                {
                    t.printStackTrace();
                }
            }
        }
    }
}
