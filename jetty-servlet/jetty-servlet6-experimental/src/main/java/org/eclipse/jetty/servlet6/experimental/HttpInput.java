package org.eclipse.jetty.servlet6.experimental;

import java.io.IOException;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.eclipse.jetty.server.Content;

public class HttpInput extends ServletInputStream implements Runnable
{
    private final ServletScopedRequest _request;
    private Content _content;
    ReadListener _readListener;

    public HttpInput(ServletScopedRequest request)
    {
        _request = request;
    }

    @Override
    public void run()
    {

    }

    @Override
    public boolean isFinished()
    {
        Content content = _content;
        return content != null && content.isLast();
    }

    @Override
    public boolean isReady()
    {
        if (_content == null)
        {
            _content = _request.readContent();
            if (_content == null)
            {
                if (_readListener != null)
                    _request.demandContent(_request::onContentAvailable);
                return false;
            }
        }

        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener)
    {
        _readListener = readListener;
    }

    @Override
    public int read() throws IOException
    {
        // TODO this is just the async version
        if (_content == null)
            _content = _request.readContent();
        if (_content != null & _content.hasRemaining())
        {
            // TODO if last byte release the _content
            return _content.getByteBuffer().get();
        }
        throw new IOException();
    }
}
