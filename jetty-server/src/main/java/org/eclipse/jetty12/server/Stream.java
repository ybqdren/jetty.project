package org.eclipse.jetty12.server;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;

/**
 * This is a new API that has been factored out of the HttpTransport API.
 * It represents the calls the Channel and above) make on the Transport for a
 * single request/response cycle.   The Stream (perhaps not best nam) is itself a
 * call back that is used to indicate when the request cycle is complete (rather than
 * returning from a Handler)
 */
interface Stream extends Callback
{
    MetaData.Request getMetaData();

    Content read();

    void needContent(Supplier<Runnable> onContentAvailable);

    void send(MetaData.Response response, Callback callback, Content... content);

    boolean isPushSupported();

    void push(MetaData.Request request);

    boolean isComplete();

    void whenComplete(BiConsumer<Stream, Throwable> onComplete);
}
