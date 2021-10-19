package org.eclipse.jetty12.server;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;

/**
 * Response is the absolute minimum to efficiently communicate a request.
 */
public interface Response
{
    int getCode();

    void setCode(int code);

    HttpFields.Mutable getHttpFields();

    HttpFields.Mutable getTrailers();

    void write(boolean last, Callback callback, ByteBuffer... content);

    void push(MetaData.Request request);

    void whenCommit(BiConsumer<Request, Response> onCommit);

    boolean isCommitted();

    void reset();

    default Response getWrapped()
    {
        return null;
    }
}
