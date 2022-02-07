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

package org.eclipse.jetty.server.handler;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.ByteBufferAccumulator;
import org.eclipse.jetty.server.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.StringUtil;

/**
 * Dump request handler.
 * Dumps GET and POST requests.
 * Useful for testing and debugging.
 */
public class EchoBufferedHandler extends Handler.Abstract
{
    @Override
    public void handle(Request request) throws Exception
    {
        Response response = request.accept();
        response.setStatus(200);
        String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
        if (StringUtil.isNotBlank(contentType))
            response.setContentType(contentType);
        long contentLength = request.getHeaders().getLongField(HttpHeader.CONTENT_LENGTH);
        if (contentLength >= 0)
            response.setContentLength(contentLength);
        if (request.getHeaders().contains(HttpHeader.TRAILER))
            response.getTrailers();

        Runnable echo = new Runnable()
        {
            ByteBufferAccumulator accumulator = new ByteBufferAccumulator();

            @Override
            public void run()
            {
                while (true)
                {
                    Content content = request.readContent();
                    if (content == null)
                    {
                        request.demandContent(this);
                        break;
                    }

                    if (content instanceof Content.Trailers)
                        response.getTrailers()
                            .add("Echo", "Trailers")
                            .add(((Content.Trailers)content).getTrailers());

                    if (content.hasRemaining())
                        accumulator.copyBuffer(content.getByteBuffer());

                    content.release();

                    if (content.isLast())
                    {
                        response.write(true, response.getCallback(), accumulator.takeByteBuffer());
                        break;
                    }
                }
            }
        };

        echo.run();
    }
}
