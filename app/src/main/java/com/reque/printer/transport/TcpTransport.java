package com.reque.printer.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TcpTransport implements PrinterTransport {

    private final String host;
    private final int port;
    private Socket socket;
    private OutputStream outputStream;

    // shortcut: fixed connection timeout of 4000ms, upgrade to configurable timeout for slow Wi-Fi.
    public TcpTransport(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void connect() throws IOException {
        close();
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 4000);
        outputStream = socket.getOutputStream();
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (outputStream == null) {
            throw new IOException("Not connected to network printer at " + host + ":" + port);
        }
        outputStream.write(data);
        outputStream.flush();
    }

    @Override
    public void close() {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {}
            outputStream = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {}
            socket = null;
        }
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}

