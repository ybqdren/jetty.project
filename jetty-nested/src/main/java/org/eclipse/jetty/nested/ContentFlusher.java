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
import java.nio.channels.WritePendingException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IteratingCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContentFlusher extends IteratingCallback
{
    private static final Logger log = LoggerFactory.getLogger(ContentFlusher.class);
    private static final int BUFFER_SIZE = 1024 * 4;

    private final byte[] array;
    private final ServletOutputStream outputStream;
    private Entry current;
    private Throwable failure;

    public ContentFlusher(ServletOutputStream outputStream)
    {
        this.outputStream = outputStream;
        this.array = new byte[BUFFER_SIZE];
        outputStream.setWriteListener(new WriteListener()
        {
            @Override
            public void onWritePossible()
            {
                iterate();
            }

            @Override
            public void onError(Throwable t)
            {
                fail(t);
            }
        });
    }

    public final void write(ByteBuffer buffer, boolean last, Callback callback)
    {
        Entry entry = new Entry(buffer, last, callback);
        if (log.isDebugEnabled())
            log.debug("Queuing {}", entry);

        Throwable error = null;
        synchronized (this)
        {
            if (failure != null)
                error = failure;
            else if (current != null)
                error = new WritePendingException();
            else
                current = new Entry(buffer, last, callback);
        }

        if (error != null)
            notifyCallbackFailure(callback, failure);
        else
            iterate();
    }

    public void fail(Throwable t)
    {
        synchronized (this)
        {
            if (failure == null)
                failure = t;
        }

        iterate();
    }

    @Override
    protected Action process() throws Throwable
    {
        while (true)
        {
            Entry entry;
            synchronized (this)
            {
                if (failure != null)
                    throw failure;
                entry = current;
            }

            // The initial onWritePossible callback may be notified before a write.
            if (entry == null)
                return Action.IDLE;

            // We will get called back by the WriteListener when ready to write.
            if (!outputStream.isReady())
                return Action.IDLE;

            if (BufferUtil.isEmpty(entry.buffer))
            {
                current = null;
                if (entry.last)
                {
                    outputStream.close();
                    notifyCallbackSuccess(entry.callback);
                    return Action.SUCCEEDED;
                }

                notifyCallbackSuccess(entry.callback);
                return Action.IDLE;
            }

            if (entry.buffer.hasArray())
            {
                byte[] array = entry.buffer.array();
                int offset = entry.buffer.arrayOffset() + entry.buffer.position();
                int length = entry.buffer.remaining();
                outputStream.write(array, offset, length);
            }
            else
            {
                int len = Math.min(entry.buffer.remaining(), array.length);
                entry.buffer.get(array, 0, len);
                outputStream.write(array, 0, len);
            }
        }
    }

    @Override
    protected void onCompleteFailure(Throwable t)
    {
        if (log.isDebugEnabled())
            log.debug("onCompleteFailure {}", t.toString());

        synchronized (this)
        {
            if (failure == null)
                failure = t;
        }

        if (current != null)
        {
            notifyCallbackFailure(current.callback, t);
            current = null;
        }
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
