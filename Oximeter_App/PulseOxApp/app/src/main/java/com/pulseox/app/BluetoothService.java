package com.pulseox.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Manages the Bluetooth SPP (Serial Port Profile) connection to HC-05.
 * HC-05 always uses this standard SPP UUID.
 */
import android.annotation.SuppressLint;
@SuppressLint("MissingPermission")
public class BluetoothService {

    // Standard SPP UUID — works with every HC-05 module
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static final int MSG_CONNECTED       = 1;
    public static final int MSG_CONNECTION_FAIL = 2;
    public static final int MSG_DATA_RECEIVED   = 3;
    public static final int MSG_DISCONNECTED    = 4;

    private final Handler    handler;
    private BluetoothSocket  socket;
    private ConnectThread    connectThread;
    private ReadThread       readThread;

    public BluetoothService(Handler handler) {
        this.handler = handler;
    }

    /** Call this to initiate connection to a paired HC-05 device. */
    public void connect(BluetoothDevice device) {
        if (connectThread != null) {
            connectThread.cancel();
        }
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    /** Disconnect cleanly. */
    public void disconnect() {
        if (readThread != null) readThread.cancel();
        if (connectThread != null) connectThread.cancel();
        closeSocket();
        handler.obtainMessage(MSG_DISCONNECTED).sendToTarget();
    }

    private void closeSocket() {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
            socket = null;
        }
    }

    // ── Connect Thread ────────────────────────────────────────────────────────

    private class ConnectThread extends Thread {
        private final BluetoothDevice device;

        ConnectThread(BluetoothDevice device) {
            this.device = device;
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            } catch (IOException e) {
                socket = null;
            }
        }

        @Override
        public void run() {
            // Always cancel discovery before connecting — saves battery & speeds connection
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();

            if (socket == null) {
                handler.obtainMessage(MSG_CONNECTION_FAIL).sendToTarget();
                return;
            }

            try {
                socket.connect();  // Blocks until connected or throws
                handler.obtainMessage(MSG_CONNECTED).sendToTarget();

                // Start reading immediately after connection
                readThread = new ReadThread(socket);
                readThread.start();

            } catch (IOException e) {
                closeSocket();
                handler.obtainMessage(MSG_CONNECTION_FAIL,
                        e.getMessage()).sendToTarget();
            }
        }

        void cancel() {
            closeSocket();
        }
    }

    // ── Read Thread ───────────────────────────────────────────────────────────

    private class ReadThread extends Thread {
        private final InputStream inputStream;
        private volatile boolean running = true;

        ReadThread(BluetoothSocket socket) throws IOException {
            this.inputStream = socket.getInputStream();
        }

        @Override
        public void run() {
            StringBuilder buffer = new StringBuilder();
            byte[] byteBuffer = new byte[256];
            int bytesRead;

            while (running) {
                try {
                    bytesRead = inputStream.read(byteBuffer);
                    if (bytesRead > 0) {
                        String chunk = new String(byteBuffer, 0, bytesRead);
                        buffer.append(chunk);

                        // Parse complete lines (STM32 sends: "BPM:72,SPO2:98\n")
                        int newlineIdx;
                        while ((newlineIdx = buffer.indexOf("\n")) != -1) {
                            String line = buffer.substring(0, newlineIdx).trim();
                            buffer.delete(0, newlineIdx + 1);

                            if (!line.isEmpty()) {
                                handler.obtainMessage(MSG_DATA_RECEIVED, line)
                                        .sendToTarget();
                            }
                        }
                    }
                } catch (IOException e) {
                    if (running) {
                        handler.obtainMessage(MSG_DISCONNECTED).sendToTarget();
                    }
                    break;
                }
            }
        }

        void cancel() {
            running = false;
            try { inputStream.close(); } catch (IOException ignored) {}
        }
    }
}