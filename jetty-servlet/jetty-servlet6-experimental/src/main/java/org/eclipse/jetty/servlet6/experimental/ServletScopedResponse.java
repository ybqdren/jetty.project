package org.eclipse.jetty.servlet6.experimental;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

public class ServletScopedResponse implements Response
{
    private final Response _response;

    public ServletScopedResponse(Response response)
    {
        _response = response;
    }

    @Override
    public Request getRequest()
    {
        return _response.getRequest();
    }

    @Override
    public Callback getCallback()
    {
        return _response.getCallback();
    }

    @Override
    public int getStatus()
    {
        return _response.getStatus();
    }

    @Override
    public void setStatus(int code)
    {
        _response.setStatus(code);
    }

    @Override
    public HttpFields.Mutable getHeaders()
    {
        return _response.getHeaders();
    }

    @Override
    public HttpFields.Mutable getTrailers()
    {
        return _response.getTrailers();
    }

    @Override
    public void write(boolean last, Callback callback, ByteBuffer... content)
    {
        _response.write(last, callback, content);
    }

    @Override
    public void write(boolean last, Callback callback, String utf8Content)
    {
        _response.write(last, callback, utf8Content);
    }

    @Override
    public void push(MetaData.Request request)
    {
        _response.push(request);
    }

    @Override
    public boolean isCommitted()
    {
        return _response.isCommitted();
    }

    @Override
    public void reset()
    {
        _response.reset();
    }

    @Override
    public Response getWrapped()
    {
        return _response.getWrapped();
    }

    @Override
    public void addHeader(String name, String value)
    {
        _response.addHeader(name, value);
    }

    @Override
    public void addHeader(HttpHeader header, String value)
    {
        _response.addHeader(header, value);
    }

    @Override
    public void setHeader(String name, String value)
    {
        _response.setHeader(name, value);
    }

    @Override
    public void setHeader(HttpHeader header, String value)
    {
        _response.setHeader(header, value);
    }

    @Override
    public void setContentType(String mimeType)
    {
        _response.setContentType(mimeType);
    }

    @Override
    public void setContentLength(long length)
    {
        _response.setContentLength(length);
    }

    @Override
    public void sendRedirect(int code, String location, boolean consumeAll) throws IOException
    {
        _response.sendRedirect(code, location, consumeAll);
    }

    @Override
    public void writeError(Throwable cause, Callback callback)
    {
        _response.writeError(cause, callback);
    }

    @Override
    public void writeError(int status, Callback callback)
    {
        _response.writeError(status, callback);
    }

    @Override
    public void writeError(int status, String message, Callback callback)
    {
        _response.writeError(status, message, callback);
    }

    @Override
    public void writeError(int status, String message, Throwable cause, Callback callback)
    {
        _response.writeError(status, message, cause, callback);
    }
}
