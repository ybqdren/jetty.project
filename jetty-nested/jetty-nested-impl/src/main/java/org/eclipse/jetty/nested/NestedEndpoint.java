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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadPendingException;
import java.nio.channels.WritePendingException;

import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Callback;

public class NestedEndpoint implements EndPoint
{
    private final long _creationTime = System.currentTimeMillis();
    private final NestedRequestResponse _nestedRequestResponse;
    private boolean _closed = false;

    public NestedEndpoint(NestedRequestResponse nestedRequestResponse)
    {
        _nestedRequestResponse = nestedRequestResponse;
    }

    public NestedRequestResponse getNestedRequestResponse()
    {
        return _nestedRequestResponse;
    }

    @Override
    public InetSocketAddress getLocalAddress()
    {
        return InetSocketAddress.createUnresolved("0.0.0.0", 0);
    }

    @Override
    public InetSocketAddress getRemoteAddress()
    {
        return null;
    }

    @Override
    public boolean isOpen()
    {
        return !_closed;
    }

    @Override
    public long getCreatedTimeStamp()
    {
        return _creationTime;
    }

    @Override
    public void shutdownOutput()
    {
        _closed = true;
    }

    @Override
    public boolean isOutputShutdown()
    {
        return _closed;
    }

    @Override
    public boolean isInputShutdown()
    {
        return _closed;
    }

    @Override
    public void close()
    {
        _closed = true;
    }

    @Override
    public void close(Throwable cause)
    {
    }

    @Override
    public int fill(ByteBuffer buffer) throws IOException
    {
        return 0;
    }

    @Override
    public boolean flush(ByteBuffer... buffer) throws IOException
    {
        return false;
    }

    @Override
    public Object getTransport()
    {
        return null;
    }

    @Override
    public long getIdleTimeout()
    {
        return 0;
    }

    @Override
    public void setIdleTimeout(long idleTimeout)
    {
    }

    @Override
    public void fillInterested(Callback callback) throws ReadPendingException
    {
    }

    @Override
    public boolean tryFillInterested(Callback callback)
    {
        return false;
    }

    @Override
    public boolean isFillInterested()
    {
        return false;
    }

    @Override
    public void write(Callback callback, ByteBuffer... buffers) throws WritePendingException
    {
    }

    @Override
    public Connection getConnection()
    {
        return null;
    }

    @Override
    public void setConnection(Connection connection)
    {
    }

    @Override
    public void onOpen()
    {
    }

    @Override
    public void onClose(Throwable cause)
    {
    }

    @Override
    public void upgrade(Connection newConnection)
    {
    }
}
