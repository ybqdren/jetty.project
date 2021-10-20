package org.eclipse.jetty12.server;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;

/**
 * This is a new API that has been factored out of the HttpTransport API.
 * It represents the calls the Channel (and above) make on the Transport for a
 * single request/response cycle.   It is equivalent to a h2/h3  Stream (perhaps a better name).
 * It is itself a call back that is used to indicate when the request cycle is complete (rather than
 * returning from a Handler)
 */
interface Exchange extends Callback
{
    MetaData.Request getMetaData();

    Content readContent();

    void demandContent();

    void send(MetaData.Response response, boolean last, Callback callback, ByteBuffer... content);

    boolean isPushSupported();

    void push(MetaData.Request request);

    boolean isComplete();

    void whenComplete(BiConsumer<Exchange, Throwable> onComplete);
}
