package org.eclipse.jetty12.server;

import java.nio.ByteBuffer;

/**
 * The Content abstract is based on what is already used in several places.
 * It allows EOF and Error flows to be unified with content data. This allows
 * the semantics of multiple methods like flush, close, onError, etc. to be
 * included in the read/write APIs.
 */
interface Content
{
    ByteBuffer getByteBuffer();

    void release();

    boolean isLast();

    interface Error extends Content
    {
        Throwable getReason();
    }
}
