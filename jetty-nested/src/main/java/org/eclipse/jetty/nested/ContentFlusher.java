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

package org.eclipse.jetty.nested;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ContentFlusher extends IteratingCallback
{
    private static final Logger log = Log.getLogger(ContentFlusher.class);

    private final Queue<Entry> entries = new ArrayDeque<>();
    private final ServletOutputStream outputStream;
    private final WriteListener writeListener;
    private boolean finished;
    private Throwable failure;
    private Entry current;

    public ContentFlusher(ServletOutputStream outputStream)
    {
        this.outputStream = outputStream;
        this.writeListener = new WriteListener()
        {
            @Override
            public void onWritePossible()
            {
                iterate();
            }

            @Override
            public void onError(Throwable t)
            {
                onFailure(t);
            }
        };
    }

    public final void enqueue(ByteBuffer buffer, boolean last, Callback callback)
    {
        Entry entry = new Entry(buffer, last, callback);
        if (log.isDebugEnabled())
            log.debug("Queuing {}", entry);

        boolean enqueued = false;
        synchronized (this)
        {
            if (failure == null)
                enqueued = entries.add(entry);
        }

        if (enqueued)
            iterate();
        else
            notifyCallbackFailure(callback, failure);
    }

    public WriteListener getListener()
    {
        return writeListener;
    }

    public void onFailure(Throwable t)
    {
        synchronized (this)
        {
            if (failure == null)
                failure = t;
        }

        for (Entry entry : entries)
            notifyCallbackFailure(entry.callback, t);
        entries.clear();
    }

    private Entry pollEntry()
    {
        // TODO: We don't need queue, only one single send at once.
        synchronized (this)
        {
            return entries.poll();
        }
    }

    @Override
    protected Action process() throws Throwable
    {
        while (true)
        {
            if (finished)
            {
                if (outputStream.isReady())
                {
                    // We're done and ready to close.
                    outputStream.close();
                    return Action.SUCCEEDED;
                }
                else
                {
                    // We will get called back by the WriteListener.
                    return Action.IDLE;
                }
            }

            if (current == null)
                current = pollEntry();

            // We will get called back by the Transport when data is available to send.
            if (current == null)
                return Action.IDLE;

            // We will get called back by the WriteListener when ready to write.
            if (!outputStream.isReady())
                return Action.IDLE;

            // TODO: We cannot succeed the callback until isReady() return true.
            // OutputStream is ready so make a write.

            // TODO: Either we copy the whole buffer and an iterating callback is not needed or we copy the buffer in chunks and an iterating callback is needed.
            byte[] content = BufferUtil.toArray(current.buffer);
            outputStream.write(content);
            notifyCallbackSuccess(current.callback);
            finished = current.last;
            current = null;
        }
    }

    @Override
    protected void onCompleteFailure(Throwable t)
    {
        if (log.isDebugEnabled())
            log.debug("onCompleteFailure {}", t.toString());

        if (current != null)
        {
            notifyCallbackFailure(current.callback, t);
            current = null;
        }

        onFailure(t);
    }

    private void notifyCallbackSuccess(Callback callback)
    {
        if (log.isDebugEnabled())
            log.debug("notifyCallbackSuccess {}", callback);

        try
        {
            if (callback != null)
                callback.succeeded();
        }
        catch (Throwable x)
        {
            log.warn("Exception while notifying success of callback {}", callback, x);
        }
    }

    private void notifyCallbackFailure(Callback callback, Throwable failure)
    {
        if (log.isDebugEnabled())
            log.debug("notifyCallbackFailure {} {}", callback, failure.toString());

        try
        {
            if (callback != null)
                callback.failed(failure);
        }
        catch (Throwable x)
        {
            log.warn("Exception while notifying failure of callback {}", callback, x);
        }
    }

    private static class Entry
    {
        private final ByteBuffer buffer;
        private final Callback callback;
        private final boolean last;

        public Entry(ByteBuffer buffer, boolean last, Callback callback)
        {
            this.buffer = buffer;
            this.callback = callback;
            this.last = last;
        }
    }
}
