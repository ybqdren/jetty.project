package org.eclipse.jetty.servlet6.experimental;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.AutoLock;

public class HttpOutput extends ServletOutputStream
{
    private final Response _response;
    private final AutoLock _lock = new AutoLock();
    private WriteListener _writeListener;
    private int _outstandingWrites;
    private boolean _closed = false;

    public HttpOutput(Response response)
    {
        _response = response;
    }

    public void completed(Throwable failure)
    {

    }

    public Response getResponse()
    {
        return _response;
    }

    private class WriteListenerCallback implements Callback
    {
        private CompletableFuture<Void> _blocker = new CompletableFuture<>();

        @Override
        public void succeeded()
        {
            try(AutoLock l = lock())
            {
                if (_closed)
                    return;

                if (_writeListener != null)
                {
                    try
                    {
                        // TODO: this can be reentrant.
                        _writeListener.onWritePossible();
                    }
                    catch (Throwable t)
                    {
                        _writeListener.onError(t);
                        _response.getRequest().failed(t);
                    }
                }

                _outstandingWrites--;
                _blocker.complete(null);
            }
        }

        @Override
        public void failed(Throwable x)
        {
            try(AutoLock l = lock())
            {
                if (_writeListener != null)
                {
                    try
                    {
                        _writeListener.onError(x);
                    }
                    catch (Throwable t)
                    {
                        // TODO: Do we need to fail request if the callback was already failed?
                        x.addSuppressed(t);
                        _response.getRequest().failed(x);
                    }
                }

                _outstandingWrites--;
                _blocker.completeExceptionally(x);
            }
        }

        public void block() throws IOException
        {
            try
            {
                _blocker.get();
            }
            catch (InterruptedException e)
            {
                InterruptedIOException exception = new InterruptedIOException();
                exception.initCause(e);
                throw exception;
            }
            catch (ExecutionException e)
            {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException)
                    throw new RuntimeException(cause);
                else
                    throw new IOException(cause);
            }
        }

        public void reset()
        {
            _blocker = new CompletableFuture<>();
        }
    }

    AutoLock lock()
    {
        return _lock.lock();
    }

    @Override
    public boolean isReady()
    {
        try(AutoLock l = lock())
        {
            return true;
        }
    }

    @Override
    public void setWriteListener(WriteListener writeListener)
    {
        try(AutoLock l = lock())
        {
            if (writeListener == null)
                throw new NullPointerException();
            if (_writeListener != null)
                throw new IllegalStateException();
            _writeListener = writeListener;

            // Notify onWritePossible if there are no outstanding blocking writes.
            if (_outstandingWrites <= 0)
            {
                try
                {
                    _writeListener.onWritePossible();
                }
                catch (Throwable t)
                {
                    _writeListener.onError(t);
                    _response.getRequest().failed(t);
                }
            }
        }
    }

    @Override
    public void flush() throws IOException
    {
        write(false);
    }

    @Override
    public void close() throws IOException
    {
        write(true);
    }

    @Override
    public void write(int b) throws IOException
    {
        write(false, BufferUtil.toBuffer(new byte[]{(byte)b}));
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        write(false, BufferUtil.toBuffer(b, off, len));
    }

    private void write(boolean last, ByteBuffer... content) throws IOException
    {
        boolean blocking;
        WriteListenerCallback callback = new WriteListenerCallback();
        try(AutoLock l = lock())
        {
            _outstandingWrites++;
            _response.write(last, callback, content);
            blocking = (_writeListener == null);
        }

        if (blocking)
            callback.block();
    }
}
