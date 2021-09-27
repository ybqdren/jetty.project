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
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.EventListener;

public interface NestedRequestResponse
{
    void startAsync();

    void stopAsync();

    // === Read Methods ===

    String getRequestURI();

    String getProtocol();

    String getMethod();

    Enumeration<String> getHeaderNames();

    Enumeration<String> getHeaders(String headerName);

    boolean isSecure();

    long getContentLengthLong();

    boolean isReadReady();

    boolean isReadClosed();

    void closeInput() throws IOException;

    Content read() throws IOException;

    void setReadListener(ReadListener writeListener);

    interface ReadListener extends EventListener
    {
        void onDataAvailable() throws IOException;

        void onAllDataRead() throws IOException;

        void onError(Throwable t);
    }

    interface Content
    {
        ByteBuffer getByteBuffer();

        void release();
    }

    // === Write Methods ===

    void setStatus(int status);

    void addHeader(String name, String value);

    boolean isWriteReady();

    boolean isWriteClosed();

    void write(ByteBuffer buffer) throws IOException;

    void closeOutput() throws IOException;

    void setWriteListener(WriteListener writeListener);

    interface WriteListener
    {
        void onWritePossible() throws IOException;

        void onError(final Throwable t);
    }
}
