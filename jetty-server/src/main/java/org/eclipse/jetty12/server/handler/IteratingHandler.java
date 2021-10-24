package org.eclipse.jetty12.server.handler;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty12.server.Handler;
import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.Response;

public class IteratingHandler extends Handler.Abstract<Request>
{
    @Override
    public boolean handle(Request request, Response response)
    {
        response.setStatus(200);

        new IteratingCallback()
        {
            final AtomicInteger _state = new AtomicInteger(2);
            @Override
            protected Action process()
            {
                switch (_state.getAndDecrement())
                {
                    case 2:
                        response.write(false, this, BufferUtil.toBuffer("hello "));
                        return Action.SCHEDULED;
                    case 1:
                        response.write(true, this, BufferUtil.toBuffer("world"));
                        return Action.SCHEDULED;
                    default:
                        return Action.SUCCEEDED;
                }
            }

            @Override
            protected void onCompleteSuccess()
            {
                request.succeeded();
            }

            @Override
            protected void onCompleteFailure(Throwable cause)
            {
                request.failed(cause);
            }
        }.iterate();

        return true;
    }
}
