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

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
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
    private final Server _server;
    private final ConnectionMetaData _connectionMetaData;
    private final AtomicInteger _requests = new AtomicInteger();
    private final AtomicReference<Consumer<Throwable>> _onConnectionClose = new AtomicReference<>();
    private final AtomicReference<Stream> _stream = new AtomicReference<>();
    private ChannelRequest _request;
    private ChannelResponse _response;

    public Channel(Server server, ConnectionMetaData connectionMetaData)
    {
        _server = server;
        _connectionMetaData = connectionMetaData;
    }

    public Handler<Request> getServer()
    {
        return _server;
    }

    public ConnectionMetaData getMetaConnection()
    {
        return _connectionMetaData;
    }

    public Stream getStream()
    {
        return _request.stream();
    }

    public Runnable onRequest(MetaData.Request request, Stream stream)
    {
        if (!_stream.compareAndSet(null, stream))
            throw new IllegalStateException("Stream pending");

        _requests.incrementAndGet();

        // TODO wrapping behaviour makes recycling requests kind of pointless, as much of the things that benefit
        //      from reuse are in the wrappers.   So for example, now in ServletContextHandler, we make the effort
        //      to recycle the ServletRequestState object and add that to the new request. Likewise, at this level
        //      we need to determine if some expensive resources are best moved to the channel and referenced by the
        //      request - eg perhaps AttributeMap?     But then should that reference be volatile and breakable?
        _request = new ChannelRequest(request);
        _response = new ChannelResponse();

        // Mock request log
        RequestLog requestLog = _server.getRequestLog();
        if (requestLog != null)
        {
            onStreamEvent(s ->
                new Stream.Wrapper(s)
                {
                    MetaData.Response _responseMeta;

                    @Override
                    public void send(MetaData.Response response, boolean last, Callback callback, ByteBuffer... content)
                    {
                        if (response != null)
                            _responseMeta = response;
                        super.send(response, last, callback, content);
                    }

                    @Override
                    public void succeeded()
                    {
                        requestLog.log(_request.getWrapper(), request, _responseMeta);
                        super.succeeded();
                    }
                });
        }

        return this::handle;
    }

    public Runnable onContentAvailable()
    {
        return _request._onContent.getAndSet(null);
    }

    // TODO would trailers be better delivered via a special Content?
    public Runnable onRequestComplete(HttpFields trailers)
    {
        Object consumer = _request._onTrailers.getAndSet(trailers);
        if (consumer == null || trailers == null)
            return null;
        return () -> ((Consumer<HttpFields>)consumer).accept(trailers);
    }

    public Runnable onConnectionClose(Throwable failed)
    {
        notifyConnectionClose(_onConnectionClose.getAndSet(null), failed);
        return null;
    }

    public void whenStreamComplete(Consumer<Throwable> onComplete)
    {
        // TODO would a dedicated listener interface be better than this wrapping
        onStreamEvent(s ->
            new Stream.Wrapper(s)
            {
                @Override
                public void succeeded()
                {
                    super.succeeded();
                    onComplete.accept(null);
                }

                @Override
                public void failed(Throwable x)
                {
                    super.failed(x);
                    onComplete.accept(x);
                }
            });
    }

    public void onStreamEvent(UnaryOperator<Stream> onStreamEvent)
    {
        // TODO we can intercept stream events with this wrapper approach.
        //      The alternative would be to have a listener mechanism and for the channel to explicitly call all
        //      listeners prior to calling the stream... however, this will not see any direct calls made to the
        //      stream (eg sendProcessing).
        _stream.getAndUpdate(s ->
        {
            if (s == null)
                throw new IllegalStateException("No active stream");
            s = onStreamEvent.apply(s);
            if (s == null)
                throw new IllegalArgumentException("Cannot remove stream");
            return s;
        });
    }

    public void whenConnectionComplete(Consumer<Throwable> onComplete)
    {
        if (!_onConnectionClose.compareAndSet(null, onComplete))
        {
            _onConnectionClose.getAndUpdate(l -> (failed) ->
            {
                notifyConnectionClose(l, failed);
                notifyConnectionClose(onComplete, failed);
            });
        }
    }

    private void handle()
    {
        if (!_server.handle(_request, _response))
        {
            if (_response.isCommitted())
            {
                _request.failed(new IllegalStateException("Not Completed"));
            }
            else
            {
                _response.reset();
                _response.setStatus(404);
                _request.succeeded();
            }
        }
    }

    private void notifyConnectionClose(Consumer<Throwable> onConnectionComplete, Throwable failed)
    {
        if (onConnectionComplete != null)
        {
            try
            {
                onConnectionComplete.accept(failed);
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }
        }
    }

    private class ChannelRequest extends AttributesMap implements Request.Base
    {
        private final MetaData.Request _metaData;
        private final AtomicReference<Runnable> _onContent = new AtomicReference<>();
        private final AtomicReference<Object> _onTrailers = new AtomicReference<>();

        private Request _wrapper = this;

        private ChannelRequest(MetaData.Request metaData)
        {
            _metaData = metaData;
        }

        @Override
        public void setWrapper(Request wrapper)
        {
            _wrapper = wrapper;
        }

        @Override
        public Request getWrapper()
        {
            return _wrapper;
        }

        Stream stream()
        {
            Stream s = _stream.get();
            if (s == null)
                throw new IllegalStateException();
            return s;
        }
        
        @Override
        public String getId()
        {
            return Integer.toString(_requests.get());
        }

        @Override
        public ConnectionMetaData getConnectionMetaData()
        {
            return _connectionMetaData;
        }

        @Override
        public Channel getChannel()
        {
            return Channel.this;
        }

        @Override
        public String getMethod()
        {
            return _metaData.getMethod();
        }

        @Override
        public HttpURI getURI()
        {
            return _metaData.getURI();
        }

        @Override
        public HttpFields getHeaders()
        {
            return _metaData.getFields();
        }

        @Override
        public long getContentLength()
        {
            return _metaData.getContentLength();
        }

        @Override
        public Content readContent()
        {
            return stream().readContent();
        }

        @Override
        public void demandContent(Runnable onContentAvailable)
        {
            Runnable task = _onContent.getAndSet(onContentAvailable);
            if (task != null && task != onContentAvailable)
                throw new IllegalStateException();
            stream().demandContent();
        }

        @Override
        public void onTrailers(Consumer<HttpFields> onTrailers)
        {
            Object trailers = _onTrailers.getAndSet(onTrailers);
            if (trailers instanceof Consumer)
                throw new IllegalStateException("Trailer consumer already set");
            if (trailers != null)
                onTrailers.accept((HttpFields)trailers);
        }

        @Override
        public void succeeded()
        {
            Stream s = _stream.getAndSet(null);
            if (s == null)
                throw new IllegalStateException("stream completed");

            // Cannot handle trailers after succeeded
            _onTrailers.set(null);

            // Commit and complete the response
            // TODO do we need to be able to ask the response if it is complete? or is it just simpler and less racy
            //      to do an empty last send like below?
            s.send(_response.commitResponse(), true, Callback.from(() ->
            {
                // then ensure the request is complete
                Throwable failed = s.consumeAll();
                // input must be complete so succeed the stream and notify
                if (failed == null)
                    s.succeeded();
                else
                    s.failed(failed);
            }));
        }

        @Override
        public void failed(Throwable x)
        {
            // TODO should we send a 500 if we are not committed?
            // This is equivalent to the previous HttpTransport.abort(Throwable), so we don't need to do much clean up
            // as channel will be shutdown and thrown away.
            Stream s = _stream.getAndSet(null);
            if (s == null)
                throw new IllegalStateException("completed");
            s.failed(x);
        }

        @Override
        public InvocationType getInvocationType()
        {
            return stream().getInvocationType();
        }
    }

    private static final BiConsumer<Request, Response> UNCOMMITTED = (req, res) -> {};
    private static final BiConsumer<Request, Response> COMMITTED = (req, res) -> {};

    private class ChannelResponse implements Response
    {
        private final AtomicReference<BiConsumer<Request, Response>> _onCommit = new AtomicReference<>(UNCOMMITTED);
        private int _status;
        private HttpFields.Immutable _committed;
        private HttpFields.Mutable _headers;
        private HttpFields.Mutable _trailers;

        @Override
        public int getStatus()
        {
            return _status;
        }

        @Override
        public void setStatus(int code)
        {
            _status = code;
        }

        @Override
        public HttpFields.Mutable getHeaders()
        {
            return _headers;
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
            _request.stream().send(commitResponse(), last, callback, content);
        }

        private HttpFields takeTrailers()
        {
            return _trailers == null ? null : _trailers.asImmutable();
        }

        @Override
        public void push(MetaData.Request request)
        {
            _request.stream().push(request);
        }

        @Override
        public void whenCommitting(BiConsumer<Request, Response> onCommit)
        {
            _onCommit.getAndUpdate(l ->
            {
                if (l == COMMITTED)
                    throw new IllegalStateException("Committed");

                if (l == UNCOMMITTED)
                    return onCommit;

                return (request, response) ->
                {
                    notifyCommit(l);
                    notifyCommit(onCommit);
                };
            });
        }

        @Override
        public boolean isCommitted()
        {
            return _onCommit.get() == COMMITTED;
        }

        @Override
        public void reset()
        {
            if (isCommitted())
                throw new IllegalStateException("Committed");
            // TODO re-add or don't delete default fields
            _headers.clear();
            _status = 0;
        }

        private MetaData.Response commitResponse()
        {
            BiConsumer<Request, Response> committed = _onCommit.getAndSet(COMMITTED);
            if (committed == COMMITTED)
                return null;

            if (committed != UNCOMMITTED)
                notifyCommit(committed);

            return new MetaData.Response(
                _request._metaData.getHttpVersion(),
                _status,
                null,
                _headers.asImmutable(),
                -1,
                _response::takeTrailers);
        }

        private void notifyCommit(BiConsumer<Request, Response> onCommit)
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
