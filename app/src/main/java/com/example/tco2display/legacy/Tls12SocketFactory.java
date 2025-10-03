package com.example.tco2display.legacy;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/** Forces TLS 1.2 on pre-Lollipop sockets. For OkHttp 3.12.x. */
public final class Tls12SocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;

    public Tls12SocketFactory(SSLSocketFactory base) {
        this.delegate = base;
    }

    @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

    @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        Socket socket = delegate.createSocket(s, host, port, autoClose);
        patch(socket);
        return socket;
    }
    @Override public Socket createSocket(String host, int port) throws IOException {
        Socket socket = delegate.createSocket(host, port);
        patch(socket);
        return socket;
    }
    @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        Socket socket = delegate.createSocket(host, port, localHost, localPort);
        patch(socket);
        return socket;
    }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        Socket socket = delegate.createSocket(host, port);
        patch(socket);
        return socket;
    }
    @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        Socket socket = delegate.createSocket(address, port, localAddress, localPort);
        patch(socket);
        return socket;
    }
    @Override public Socket createSocket() throws IOException {
        Socket socket = delegate.createSocket();
        patch(socket);
        return socket;
    }

    private void patch(Socket s) {
        if (s instanceof SSLSocket) {
            ((SSLSocket) s).setEnabledProtocols(new String[] { "TLSv1.2" });
        }
    }
}
