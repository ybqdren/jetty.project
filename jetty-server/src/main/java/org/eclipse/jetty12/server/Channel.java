package org.eclipse.jetty12.server;

import org.eclipse.jetty.http.HttpFields;

/**
 * A Channel represents a sequence of request cycles from the same connection. However only a single
 * request cycle may be active at once for each channel.    This is some, but not all of the
 * behaviour of the current HttpChannel class, specifically it does not include the mutual exclusion
 * of handling required by the servlet spec and currently encapsulated in HttpChannelState.
 *
 * Note how Runnables are returned to indicate that further work is needed. These
 * can be given to an ExecutionStrategy instead of calling known methods like HttpChannel.handle().
 */
interface Channel
{
    Channel newHttpChannel(Transport transport);

    Transport getTransport();

    Runnable onRequest(Stream stream);

    Runnable onRequestComplete(HttpFields onTrailers);

    Runnable onTransportComplete(Throwable failure);
}
