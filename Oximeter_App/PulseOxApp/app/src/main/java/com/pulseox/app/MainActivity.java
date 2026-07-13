package com.pulseox.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import android.annotation.SuppressLint;

@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT  = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    // UI
    private Spinner  spinnerDevices;
    private Button   btnConnect, btnDisconnect;
    private TextView tvBpm, tvSpo2, tvStatus, tvLog;
    private ScrollView scrollLog;

    // BT
    private BluetoothAdapter  btAdapter;
    private BluetoothService  btService;
    private List<BluetoothDevice> pairedDevices = new ArrayList<>();

    // Handler runs on Main thread — safe to touch UI
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull android.os.Message msg) {
            switch (msg.what) {
                case BluetoothService.MSG_CONNECTED:
                    setStatus("Connected ✓", "#27AE60");
                    btnConnect.setEnabled(false);
                    btnDisconnect.setEnabled(true);
                    appendLog("Connected to device.");
                    break;

                case BluetoothService.MSG_CONNECTION_FAIL:
                    setStatus("Connection failed ✗", "#E74C3C");
                    btnConnect.setEnabled(true);
                    btnDisconnect.setEnabled(false);
                    appendLog("Connection failed: " + msg.obj);
                    break;

                case BluetoothService.MSG_DATA_RECEIVED:
                    String raw = (String) msg.obj;
                    appendLog("RX: " + raw);
                    DataParser.Reading r = DataParser.parse(raw);
                    if (r.valid) {
                        tvBpm.setText(String.valueOf(r.bpm));
                        tvSpo2.setText(r.spo2 + "%");
                        colorSpo2(r.spo2);
                    } else {
                        appendLog("⚠ Could not parse: " + raw);
                    }
                    break;

                case BluetoothService.MSG_DISCONNECTED:
                    setStatus("Disconnected", "#7F8C8D");
                    btnConnect.setEnabled(true);
                    btnDisconnect.setEnabled(false);
                    tvBpm.setText("--");
                    tvSpo2.setText("--%");
                    appendLog("Disconnected.");
                    break;
            }
        }
    };

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind views
        spinnerDevices  = findViewById(R.id.spinnerDevices);
        btnConnect      = findViewById(R.id.btnConnect);
        btnDisconnect   = findViewById(R.id.btnDisconnect);
        tvBpm           = findViewById(R.id.tvBpm);
        tvSpo2          = findViewById(R.id.tvSpo2);
        tvStatus        = findViewById(R.id.tvStatus);
        tvLog           = findViewById(R.id.tvLog);
        scrollLog       = findViewById(R.id.scrollLog);

        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btService = new BluetoothService(handler);

        btnConnect.setOnClickListener(v -> connectToSelected());
        btnDisconnect.setOnClickListener(v -> {
            btService.disconnect();
        });

        requestPermissionsIfNeeded();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btService != null) btService.disconnect();
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private void requestPermissionsIfNeeded() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            // Android 6–11
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), REQUEST_PERMISSIONS);
        } else {
            initBluetooth();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) {
                initBluetooth();
            } else {
                Toast.makeText(this,
                        "Bluetooth permissions are required for this app.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ── Bluetooth Init ────────────────────────────────────────────────────────

    private void initBluetooth() {
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            populateDeviceSpinner();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                populateDeviceSpinner();
            } else {
                Toast.makeText(this, "Bluetooth must be enabled.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void populateDeviceSpinner() {
        Set<BluetoothDevice> bonded = btAdapter.getBondedDevices();
        pairedDevices.clear();
        List<String> names = new ArrayList<>();

        for (BluetoothDevice d : bonded) {
            pairedDevices.add(d);
            names.add(d.getName() + "\n" + d.getAddress());
        }

        if (names.isEmpty()) {
            Toast.makeText(this,
                    "No paired devices found. Pair HC-05 in Android Bluetooth settings first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDevices.setAdapter(adapter);
        appendLog("Found " + names.size() + " paired device(s).");
    }

    // ── Connect ───────────────────────────────────────────────────────────────

    private void connectToSelected() {
        int idx = spinnerDevices.getSelectedItemPosition();
        if (idx < 0 || idx >= pairedDevices.size()) {
            Toast.makeText(this, "Select a device first.", Toast.LENGTH_SHORT).show();
            return;
        }
        BluetoothDevice device = pairedDevices.get(idx);
        setStatus("Connecting…", "#F39C12");
        btnConnect.setEnabled(false);
        appendLog("Connecting to " + device.getName() + "…");
        btService.connect(device);
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private void setStatus(String text, String hexColor) {
        tvStatus.setText(text);
        tvStatus.setTextColor(android.graphics.Color.parseColor(hexColor));
    }

    private void colorSpo2(int spo2) {
        if (spo2 >= 95) {
            tvSpo2.setTextColor(android.graphics.Color.parseColor("#27AE60")); // Green
        } else if (spo2 >= 90) {
            tvSpo2.setTextColor(android.graphics.Color.parseColor("#F39C12")); // Orange
        } else {
            tvSpo2.setTextColor(android.graphics.Color.parseColor("#E74C3C")); // Red — hypoxia warning
        }
    }

    private void appendLog(String msg) {
        tvLog.append(msg + "\n");
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }
}