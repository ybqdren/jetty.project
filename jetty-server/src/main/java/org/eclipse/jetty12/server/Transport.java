package org.eclipse.jetty12.server;

import java.net.SocketAddress;
import java.util.function.BiConsumer;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;

/**
 * The existing HttpTransport interface has been augmented with more metadata
 * about the Connection (rather than a direct dependence on the Connection class).
 * However, some methods have been spun out into the new Stream interface that
 * more correctly relates to the multiple request cycles a transport may be used for.
 */
interface Transport
{
    String getId();

    HttpVersion getVersion();

    String getProtocol();

    Connection getConnection();

    boolean isSecure();

    SocketAddress getRemote();

    SocketAddress getLocal();

    void upgrade(org.eclipse.jetty.io.Connection connection);

    void complete(Throwable failure);

    boolean isComplete();

    void whenComplete(BiConsumer<Transport, Throwable> onComplete);
}
