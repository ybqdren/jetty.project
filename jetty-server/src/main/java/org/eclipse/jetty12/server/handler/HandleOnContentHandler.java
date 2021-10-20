package org.eclipse.jetty12.server.handler;

import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class HandleOnContentHandler extends Handler.Wrapper<Request>
{
    @Override
    public boolean handle(Request request, Response response)
    {
        // If no content or content available, then don't delay dispatch
        if (request.getMetaData().getContentLength() <= 0)
            return super.handle(request, response);

        request.demandContent(() ->
        {
            if (!super.handle(request, response))
                request.failed(new IllegalStateException());
        });
        return true;
    }
}
