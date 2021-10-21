package org.eclipse.jetty12.server.handler;

import java.net.SocketAddress;

import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.MetaConnection;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class ProxiedRequestHandler extends Handler.Wrapper<Request>
{
    @Override
    public boolean handle(Request request, Response response)
    {
        MetaConnection proxiedFor = new MetaConnection.Wrapper(request.getMetaConnection())
        {
            @Override
            public boolean isSecure()
            {
                // TODO replace with value determined from headers
                return super.isSecure();
            }

            @Override
            public SocketAddress getRemote()
            {
                // TODO replace with value determined from headers
                return super.getRemote();
            }

            @Override
            public SocketAddress getLocal()
            {
                // TODO replace with value determined from headers
                return super.getLocal();
            }
        };

        return super.handle(new Request.Wrapper(request)
        {
            @Override
            public HttpURI getURI()
            {
                // TODO replace with any change in authority
                return super.getURI();
            }

            @Override
            public MetaConnection getMetaConnection()
            {
                return proxiedFor;
            }
        }, response);
    }
}
