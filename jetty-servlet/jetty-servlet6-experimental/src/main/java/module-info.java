module jetty.servlet6.experimental
{
    requires org.eclipse.jetty.server;
    requires jakarta.servlet;

    exports org.eclipse.jetty.servlet6.experimental;
    exports org.eclipse.jetty.servlet6.experimental.writer;
    exports org.eclipse.jetty.servlet6.experimental.util;
}