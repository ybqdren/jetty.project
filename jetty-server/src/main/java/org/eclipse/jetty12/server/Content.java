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

package org.eclipse.jetty12.server;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The Content abstract is based on what is already used in several places.
 * It allows EOF and Error flows to be unified with content data. This allows
 * the semantics of multiple methods like flush, close, onError, etc. to be
 * included in the read/write APIs.
 */
public interface Content
{
    ByteBuffer getByteBuffer();

    void release();

    boolean isLast();

    Content EOF = new Content()
    {
        @Override
        public ByteBuffer getByteBuffer()
        {
            return null;
        }

        @Override
        public void release()
        {
        }

        @Override
        public boolean isLast()
        {
            return true;
        }
    };

    class Error implements Content
    {
        private final Throwable _cause;

        public Error(Throwable cause)
        {
            _cause = cause == null ? new IOException("unknown") : cause;
        }

        Throwable getCause()
        {
            return _cause;
        }

        @Override
        public ByteBuffer getByteBuffer()
        {
            return null;
        }

        @Override
        public void release()
        {
        }

        @Override
        public boolean isLast()
        {
            return true;
        }
    }
}
