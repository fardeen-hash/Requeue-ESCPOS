package com.reque.printer.transport;

import com.reque.printer.printer.PrinterConfig;

public class TransportFactory {

    public static PrinterTransport getTransport(PrinterConfig config) {
        if (PrinterConfig.TYPE_NETWORK.equals(config.getConnectionType())) {
            return new TcpTransport(config.getNetworkHost(), config.getNetworkPort());
        } else {
            return new BluetoothTransport(config.getBluetoothMac());
        }
    }
}

