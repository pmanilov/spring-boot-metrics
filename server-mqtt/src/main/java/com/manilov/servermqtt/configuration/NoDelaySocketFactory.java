package com.manilov.servermqtt.configuration;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class NoDelaySocketFactory extends SocketFactory {
    private final SocketFactory defaultFactory = SocketFactory.getDefault();

    @Override
    public Socket createSocket() throws IOException {
        Socket socket = defaultFactory.createSocket();
        socket.setTcpNoDelay(true);
        return socket;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        Socket socket = defaultFactory.createSocket(host, port);
        socket.setTcpNoDelay(true);
        return socket;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        Socket socket = defaultFactory.createSocket(host, port, localHost, localPort);
        socket.setTcpNoDelay(true);
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        Socket socket = defaultFactory.createSocket(host, port);
        socket.setTcpNoDelay(true);
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        Socket socket = defaultFactory.createSocket(address, port, localAddress, localPort);
        socket.setTcpNoDelay(true);
        return socket;
    }
}
