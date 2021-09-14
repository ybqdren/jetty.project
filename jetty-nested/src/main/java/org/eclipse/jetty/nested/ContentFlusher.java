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
import java.util.ArrayDeque;
import java.util.Queue;
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
    private static final int BUFFER_SIZE = 1024;

    private final Queue<Entry> entries = new ArrayDeque<>();
    private final ServletOutputStream outputStream;
    private Throwable failure;
    private Entry current;
    private boolean completed;

    public ContentFlusher(ServletOutputStream outputStream)
    {
        this.outputStream = outputStream;
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

    public final void flush(ByteBuffer buffer, boolean last, Callback callback)
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
            if (failure != null)
                throw failure;

            // We will get called back by the Transport when data is available to send.
            if (current == null)
                return Action.IDLE;

            // We will get called back by the WriteListener when ready to write.
            if (!outputStream.isReady())
                return Action.IDLE;

            if (completed)
            {
                notifyCallbackSuccess(current.callback);
                boolean last = current.last;
                completed = false;
                current = null;

                if (last)
                {
                    outputStream.close();
                    return Action.SUCCEEDED;
                }

                return Action.IDLE;
            }

            byte[] array = new byte[BUFFER_SIZE];
            int len = Math.min(current.buffer.remaining(), BUFFER_SIZE);
            current.buffer.get(array, 0, len);
            outputStream.write(array, 0, len);
            if (BufferUtil.isEmpty(current.buffer))
                completed = true;
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

        for (Entry entry : entries)
            notifyCallbackFailure(entry.callback, t);
        entries.clear();
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
