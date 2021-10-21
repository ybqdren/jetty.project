package org.eclipse.jetty12.server.handler;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty12.server.Content;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class GzipHandler extends Handler.Wrapper<Request>
{
    @Override
    public boolean handle(Request request, Response response)
    {
        // TODO this all conditionally
        return super.handle(
            new Request.Wrapper(request)
            {
                @Override
                public HttpFields getHeaders()
                {
                    // TODO update headers
                    return super.getHeaders();
                }

                @Override
                public long getContentLength()
                {
                    // TODO hide the content length
                    return -1;
                }

                @Override
                public Content readContent()
                {
                    // TODO inflate data
                    return super.readContent();
                }
            },
            new Response.Wrapper(response)
            {
                @Override
                public void write(boolean last, Callback callback, ByteBuffer... content)
                {
                    // TODO deflate data
                    super.write(last, callback, content);
                }
            });
    }
}
