package org.eclipse.jetty12.server.handler;

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class RewriteHandler extends Handler.Wrapper<Request>
{
    @Override
    public boolean handle(Request request, Response response)
    {
        Request rewritten = rewrite(request, response);
        if (response.isCommitted())
            return true;
        return super.handle(request, response);
    }

    protected Request rewrite(Request request, Response response)
    {
        // TODO run the rules, but ultimately wrap for any changes:
        return new Request.Wrapper(request)
        {
            @Override
            public MetaData.Request getMetaData()
            {
                // return alternative meta data
                return super.getMetaData();
            }
        };
    }
}
