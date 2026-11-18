package com.reque.printer.transport;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothTransport implements PrinterTransport {

    // Standard Serial Port Profile (SPP) UUID used by thermal printers
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final String macAddress;
    private BluetoothSocket socket;
    private OutputStream outputStream;

    // shortcut: connects only to pre-paired Bluetooth devices, upgrade to in-app discovery if needed.
    public BluetoothTransport(String macAddress) {
        this.macAddress = macAddress;
    }

    @Override
    public void connect() throws IOException {
        close();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            throw new IOException("Bluetooth adapter is unavailable or disabled");
        }

        BluetoothDevice device = adapter.getRemoteDevice(macAddress);
        if (device == null) {
            throw new IOException("Bluetooth device not found for MAC: " + macAddress);
        }

        // Cancel discovery to avoid slow connection
        adapter.cancelDiscovery();

        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
        } catch (IOException secureFailed) {
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
            // Fallback for thermal printers requiring insecure RFCOMM channel
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
        }
        outputStream = socket.getOutputStream();
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (outputStream == null) {
            throw new IOException("Not connected to printer");
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
        return socket != null && socket.isConnected();
    }
}

