package org.eclipse.jetty12.server.servlet;

import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Response;

public class DeferredContentHandler extends Handler.Wrapper<ServletScopedRequest>
{
    @Override
    public boolean handle(ServletScopedRequest request, Response response)
    {

        // If no content or content available, then don't delay dispatch
        if (request.getMetaData().getContentLength() <= 0)
            return super.handle(request, response);

        // TODO if the content is a form, asynchronously read the a;; parameters before handling
        //      if the content is multi-part, asynchronous read all parts before handling

        // Otherwise just delay until some content arrives.
        request.demandContent(() ->
        {
            if (!super.handle(request, response))
                request.failed(new IllegalStateException());
        });
        return true;
    }
}
