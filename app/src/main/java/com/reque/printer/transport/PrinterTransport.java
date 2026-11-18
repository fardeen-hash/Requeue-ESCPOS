package com.reque.printer.transport;

import java.io.IOException;

public interface PrinterTransport {
    void connect() throws IOException;
    void write(byte[] data) throws IOException;
    void close();
    boolean isConnected();
}

