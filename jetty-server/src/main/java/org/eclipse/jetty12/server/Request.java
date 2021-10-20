package org.eclipse.jetty12.server;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.Callback;

public interface Request extends Attributes, Callback
{
    String getId();

    Channel getChannel();

    MetaData.Request getMetaData();

    Content readContent();

    void demandContent(Runnable onContentAvailable);

    void onTrailers(Consumer<HttpFields> onTrailers);

    void whenComplete(BiConsumer<Request, Throwable> onComplete);

    default Request getWrapped()
    {
        return null;
    }

    default <R> R as(Class<R> type)
    {
        return (type.isInstance(this) ? (R)this : null);
    }

    class Wrapper extends Attributes.Wrapper implements Request
    {
        private final Request wrapped;

        public Wrapper(Request wrapped)
        {
            super(wrapped);
            this.wrapped = wrapped;
        }

        @Override
        public String getId()
        {
            return wrapped.getId();
        }

        @Override
        public Channel getChannel()
        {
            return wrapped.getChannel();
        }

        @Override
        public MetaData.Request getMetaData()
        {
            return wrapped.getMetaData();
        }

        @Override
        public Content readContent()
        {
            return wrapped.readContent();
        }

        @Override
        public void demandContent(Runnable onContentAvailable)
        {
            wrapped.demandContent(onContentAvailable);
        }

        @Override
        public void onTrailers(Consumer<HttpFields> onTrailers)
        {
            wrapped.onTrailers(onTrailers);
        }

        @Override
        public void whenComplete(BiConsumer<Request, Throwable> onComplete)
        {
            wrapped.whenComplete(onComplete);
        }

        @Override
        public Request getWrapped()
        {
            return wrapped;
        }

        @Override
        public void succeeded()
        {
            wrapped.succeeded();
        }

        @Override
        public void failed(Throwable x)
        {
            wrapped.failed(x);
        }

        @Override
        public InvocationType getInvocationType()
        {
            return wrapped.getInvocationType();
        }
    }
}
