package org.eclipse.jetty.servlet6.experimental;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;

public class ErrorHandler extends Handler.Abstract
{
    public static final String ERROR_CONTEXT = "org.eclipse.jetty.server.error_context";

    @Override
    public void handle(Request request) throws Exception
    {

    }

    public void setServer(Server server)
    {

    }
} // TODO
