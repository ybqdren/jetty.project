package org.eclipse.jetty.servlet6.experimental;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.servlet6.experimental.writer.EncodingHttpWriter;
import org.eclipse.jetty.servlet6.experimental.writer.Iso88591HttpWriter;
import org.eclipse.jetty.servlet6.experimental.writer.ResponseWriter;
import org.eclipse.jetty.servlet6.experimental.writer.Utf8HttpWriter;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.SharedBlockingCallback;
import org.eclipse.jetty.util.StringUtil;

public class ServletScopedResponse extends Response.Wrapper
{
    public enum OutputType
    {
        NONE, STREAM, WRITER
    }

    private final Response _response;
    private final HttpOutput _httpOutput;
    private final ServletChannel _servletChannel;
    private final ServletRequestState _state;
    private final MutableHttpServletResponse _httpServletResponse;

    private OutputType _outputType = OutputType.NONE;
    private ResponseWriter _writer;

    public ServletScopedResponse(ServletChannel servletChannel, Response response, HttpOutput httpOutput)
    {
        super(response.getRequest(), response);
        _response = response;
        _httpOutput = httpOutput;
        _servletChannel = servletChannel;
        _state = servletChannel.getState();
        _httpServletResponse = new MutableHttpServletResponse(response);
    }

    public HttpServletResponse getHttpServletResponse()
    {
        return _httpServletResponse;
    }

    public void completeOutput(Callback callback)
    {
        if (_outputType == OutputType.WRITER)
            _writer.complete(callback);
        else
            _httpOutput.complete(callback);
    }

    public boolean isContentComplete(long written)
    {
        // TODO: add content length field here?
        String contentLengthHeader = getHeaders().get(HttpHeader.CONTENT_LENGTH);
        if (contentLengthHeader == null)
            return true;

        long contentLength = Long.parseLong(contentLengthHeader);
        return (contentLength < 0 || written >= contentLength);
    }

    private String getCharacterEncoding(boolean setContentType)
    {
        // TODO
        return StringUtil.__ISO_8859_1;
    }

    public class MutableHttpServletResponse implements HttpServletResponse
    {
        private final SharedBlockingCallback _blocker = new SharedBlockingCallback();
        private final Response _response;

        MutableHttpServletResponse(Response response)
        {
            _response = response;
        }

        @Override
        public void addCookie(Cookie cookie)
        {
            // TODO
        }

        @Override
        public boolean containsHeader(String name)
        {
            return _response.getHeaders().contains(name);
        }

        @Override
        public String encodeURL(String url)
        {
            return null;
        }

        @Override
        public String encodeRedirectURL(String url)
        {
            return null;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException
        {
            switch (sc)
            {
                case -1:
                    _servletChannel.getRequest().getResponse().getCallback().failed(new IOException(msg));
                    break;

                case HttpStatus.PROCESSING_102:
                    try (SharedBlockingCallback.Blocker blocker = _blocker.acquire())
                    {
                        // TODO static MetaData
                        _servletChannel.getRequest().getHttpChannel().getHttpStream()
                            .send(new MetaData.Response(null, 102, null), false, blocker);
                    }
                    break;

                default:
                    // This is just a state change
                    _state.sendError(sc, msg);
                    break;
            }
        }

        @Override
        public void sendError(int sc) throws IOException
        {
            sendError(sc, null);
        }

        @Override
        public void sendRedirect(String location) throws IOException
        {
            // TODO
        }

        @Override
        public void setDateHeader(String name, long date)
        {
            _response.getHeaders().putDateField(name, date);
        }

        @Override
        public void addDateHeader(String name, long date)
        {
        }

        @Override
        public void setHeader(String name, String value)
        {
            _response.getHeaders().put(name, value);
        }

        @Override
        public void addHeader(String name, String value)
        {
            _response.getHeaders().add(name, value);
        }

        @Override
        public void setIntHeader(String name, int value)
        {
            // TODO do we need int versions?
            _response.getHeaders().putLongField(name, value);
        }

        @Override
        public void addIntHeader(String name, int value)
        {
            // TODO do we need a native version?
            _response.getHeaders().add(name, Integer.toString(value));
        }

        @Override
        public void setStatus(int sc)
        {
            _response.setStatus(sc);
        }

        @Override
        public int getStatus()
        {
            return _response.getStatus();
        }

        @Override
        public String getHeader(String name)
        {
            return _response.getHeaders().get(name);
        }

        @Override
        public Collection<String> getHeaders(String name)
        {
            return null;
        }

        @Override
        public Collection<String> getHeaderNames()
        {
            return null;
        }

        @Override
        public String getCharacterEncoding()
        {
            return ServletScopedResponse.this.getCharacterEncoding(false);
        }

        @Override
        public String getContentType()
        {
            return null;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException
        {
            return _httpOutput;
        }

        @Override
        public PrintWriter getWriter() throws IOException
        {
            if (_outputType == OutputType.STREAM)
                throw new IllegalStateException("STREAM");

            if (_outputType == OutputType.NONE)
            {
                String encoding = ServletScopedResponse.this.getCharacterEncoding(true);
                Locale locale = getLocale();
                if (_writer != null && _writer.isFor(locale, encoding))
                    _writer.reopen();
                else
                {
                    if (StringUtil.__ISO_8859_1.equalsIgnoreCase(encoding))
                        _writer = new ResponseWriter(new Iso88591HttpWriter(_httpOutput), locale, encoding);
                    else if (StringUtil.__UTF8.equalsIgnoreCase(encoding))
                        _writer = new ResponseWriter(new Utf8HttpWriter(_httpOutput), locale, encoding);
                    else
                        _writer = new ResponseWriter(new EncodingHttpWriter(_httpOutput, encoding), locale, encoding);
                }

                // Set the output type at the end, because setCharacterEncoding() checks for it.
                _outputType = OutputType.WRITER;
            }
            return _writer;
        }

        @Override
        public void setCharacterEncoding(String charset)
        {

        }

        @Override
        public void setContentLength(int len)
        {

        }

        @Override
        public void setContentLengthLong(long len)
        {

        }

        @Override
        public void setContentType(String type)
        {

        }

        @Override
        public void setBufferSize(int size)
        {

        }

        @Override
        public int getBufferSize()
        {
            return 0;
        }

        @Override
        public void flushBuffer() throws IOException
        {
            try (SharedBlockingCallback.Blocker blocker = _blocker.acquire())
            {
                _response.write(false, blocker);
            }
        }

        @Override
        public void resetBuffer()
        {
            // TODO I don't think this is right... maybe just a HttpWriter reset
            if (!_response.isCommitted())
                _response.reset();
        }

        @Override
        public boolean isCommitted()
        {
            return _response.isCommitted();
        }

        @Override
        public void reset()
        {
            if (!_response.isCommitted())
                _response.reset();
        }

        @Override
        public void setLocale(Locale loc)
        {
        }

        @Override
        public Locale getLocale()
        {
            return null;
        }
    }
}
