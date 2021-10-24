package org.eclipse.jetty12.server.handler;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class SimpleHandler extends Handler.Abstract<Request>
{
    @Override
    public boolean handle(Request request, Response response)
    {
        response.setStatus(200);
        response.write(true, request, BufferUtil.toBuffer("Hello "), BufferUtil.toBuffer("world"));
        return true;
    }
}