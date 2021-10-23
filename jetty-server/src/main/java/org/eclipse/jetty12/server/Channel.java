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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
    private final Handler<Request> _server;
    private final MetaConnection _metaConnection;
    private final AtomicInteger _requests = new AtomicInteger();
    private final AtomicReference<BiConsumer<Channel, Throwable>> _onConnectionComplete = new AtomicReference<>();
    private ChannelRequest _request;
    private ChannelResponse _response;

    public Channel(Handler<Request> server, MetaConnection metaConnection)
    {
        _server = server;
        _metaConnection = metaConnection;
    }

    public Handler<Request> getServer()
    {
        return _server;
    }

    public MetaConnection getMetaConnection()
    {
        return _metaConnection;
    }

    public Stream getStream()
    {
        return _request.stream();
    }

    public Runnable onRequest(MetaData.Request request, Stream stream)
    {
        _requests.incrementAndGet();

        // TODO wrapping behaviour makes recycling requests kind of pointless, as much of the things that benefit
        //      from reuse are in the wrappers.   So for example, now in ServletContextHandler, we make the effort
        //      to recycle the ServletRequestState object and add that to the new request. Likewise, at this level
        //      we need to determine if some expensive resources are best moved to the channel and referenced by the
        //      request - eg perhaps AttributeMap?     But then should that reference be volatile and breakable?
        _request = new ChannelRequest(request, stream);
        _response = new ChannelResponse();
        if (_response._headers == null)
            _response._headers = HttpFields.build();
        else
            _response._headers.clear();

        return this::handle;
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

    public Runnable onConnectionComplete(Throwable failed)
    {
        notifyConnectionComplete(_onConnectionComplete.getAndSet(null), failed);
        return null;
    }

    public void whenConnectionComplete(BiConsumer<Channel, Throwable> onComplete)
    {
        if (!_onConnectionComplete.compareAndSet(null, onComplete))
        {
            _onConnectionComplete.getAndUpdate(l -> (channel, failed) ->
            {
                notifyConnectionComplete(l, failed);
                notifyConnectionComplete(onComplete, failed);
            });
        }
    }

    private void handle()
    {
        if (!_server.handle(_request, _response))
        {
            _response.reset();
            _response.setStatus(404);
            _request.succeeded();
        }
    }

    private void notifyConnectionComplete(BiConsumer<Channel, Throwable> onConnectionComplete, Throwable failed)
    {
        if (onConnectionComplete != null)
        {
            try
            {
                onConnectionComplete.accept(this, failed);
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }
        }
    }

    private class ChannelRequest extends AttributesMap implements Request
    {
        private final AtomicReference<Stream> _stream;
        private final MetaData.Request _metaData;
        private final AtomicReference<Runnable> _onContent = new AtomicReference<>();
        private final AtomicReference<Object> _onTrailers = new AtomicReference<>();
        private final AtomicReference<BiConsumer<Request, Throwable>> _onStreamComplete = new AtomicReference<>();

        private ChannelRequest(MetaData.Request metaData, Stream stream)
        {
            _stream = new AtomicReference<>(stream);
            _metaData = metaData;
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
        public MetaConnection getMetaConnection()
        {
            return _metaConnection;
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
            if (trailers != null)
                onTrailers.accept((HttpFields)trailers);
        }

        @Override
        public void whenComplete(BiConsumer<Request, Throwable> onComplete)
        {
            if (!_onStreamComplete.compareAndSet(null, onComplete))
            {
                _onStreamComplete.getAndUpdate(l -> (request, failed) ->
                {
                    notifyStreamComplete(l, failed);
                    notifyStreamComplete(onComplete, failed);
                });
            }
        }

        @Override
        public void succeeded()
        {
            Stream s = _stream.getAndSet(null);
            if (s == null)
                throw new IllegalStateException("completed");

            // Cannot handle trailers after succeeded
            _onTrailers.set(null);

            // Commit and complete the response
            // TODO do we need to be able to ask the response if it is complete? or is it just simpler and less racy
            //      to do an empty last send like below?
            s.send(_response.commitResponse(), true, Callback.from(() ->
            {
                // then ensure the request is complete
                Throwable failed = s.consumeAll();
                if (failed != null)
                {
                    s.failed(failed);
                    notifyStreamComplete(_onStreamComplete.getAndSet(null), failed);
                }

                // input must be complete so succeed the stream and notify
                s.succeeded();
                notifyStreamComplete(_onStreamComplete.getAndSet(null), null);
            }));
        }

        @Override
        public void failed(Throwable x)
        {
            // This is equivalent to the previous HttpTransport.abort(Throwable), so we don't need to do much clean up
            // as channel will be shutdown and thrown away.
            Stream s = _stream.getAndSet(null);
            if (s == null)
                throw new IllegalStateException("completed");
            s.failed(x);
            notifyStreamComplete(_onStreamComplete.getAndSet(null), x == null ? new Throwable() : x);
        }

        private void notifyStreamComplete(BiConsumer<Request, Throwable> onStreamComplete, Throwable failed)
        {
            if (onStreamComplete != null)
            {
                try
                {
                    onStreamComplete.accept(_request, failed);
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
            return stream().getInvocationType();
        }
    }

    private class ChannelResponse implements Response
    {
        private final AtomicReference<BiConsumer<Request, Response>> _onCommit = new AtomicReference<>();
        private final AtomicBoolean _Committed = new AtomicBoolean();
        private int _code;
        private HttpFields.Mutable _headers;
        private HttpFields.Mutable _trailers;

        @Override
        public int getCode()
        {
            return _code;
        }

        @Override
        public void setStatus(int code)
        {
            _code = code;
        }

        @Override
        public HttpFields.Mutable getHttpFields()
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
            MetaData.Response metaData = commitResponse();
            _request.stream().send(metaData, last, callback, content);
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
        public void whenCommit(BiConsumer<Request, Response> onCommit)
        {
            if (!_onCommit.compareAndSet(null, onCommit))
            {
                _onCommit.getAndUpdate(l -> (request, response) ->
                {
                    notifyCommit(l);
                    notifyCommit(onCommit);
                });
            }
        }

        @Override
        public boolean isCommitted()
        {
            return _Committed.get();
        }

        @Override
        public void reset()
        {
            // TODO re-add or don't delete default fields
            _headers.clear();
            _code = 0;
        }

        private MetaData.Response commitResponse()
        {
            if (_Committed.compareAndSet(false, true))
            {
                notifyCommit(_onCommit.getAndSet(null));
                return new MetaData.Response(
                    _request._metaData.getHttpVersion(),
                    _code,
                    null,
                    _headers.asImmutable(),
                    -1,
                    _response::takeTrailers);
            }
            return null;
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
