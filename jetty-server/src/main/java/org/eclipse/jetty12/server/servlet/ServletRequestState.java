package org.eclipse.jetty12.server.servlet;

import java.net.CookieStore;

import jakarta.servlet.ServletContext;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.server.HttpWriter;

class ServletRequestState extends HttpChannelState
{
    private final ServletContextContext _servletContextContext;
    private ServletScopedRequest _servletScopedRequest;
    private HttpInput _httpInput; // TODO
    private HttpWriter _httpWriter; // TODO
    private CookieStore _cookieCache; // TODO

    ServletRequestState(ServletContextContext servletContextContext)
    {
        super(null); // TODO
        _servletContextContext = servletContextContext;
    }

    void setServletScopedRequest(ServletScopedRequest servletScopedRequest)
    {
        _servletScopedRequest = servletScopedRequest;
    }

    ServletScopedRequest getServletScopedRequest()
    {
        return _servletScopedRequest;
    }

    public ServletContextContext getServletContextContext()
    {
        return _servletContextContext;
    }

    public ServletContext getServletContext()
    {
        return _servletContextContext;
    }

    public void handle()
    {
        // implement the state machine from HttpChannelState and HttpChannel
        // Note that sendError may already have been called before we are handling for the first time.

        HttpChannelState.Action action = handling();
        loop: while (true)
        {
            try
            {
                switch (action)
                {
                    case COMPLETE:
                        _servletScopedRequest.succeeded();
                        break loop;

                    case WAIT:
                        break;

                    case DISPATCH:
                        _servletScopedRequest._mappedServlet.handle(_servletScopedRequest.getHttpServletRequest(), _servletScopedRequest.getHttpServletResponse());
                        break;

                    case READ_CALLBACK:
                        // TODO need to actually do a readContent here so that we can run interceptors and check there really
                        //      is data... but ultimately we do:
                        _servletScopedRequest._readListener.onDataAvailable();
                        break;

                    case SEND_ERROR:
                        // TODO send error
                        break;

                    // TODO etc.
                    default:
                        break;
                }
            }
            catch (Throwable failure)
            {
                // TODO
            }

            action = unhandle();
        }
    }
}
