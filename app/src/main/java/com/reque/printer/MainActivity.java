package com.reque.printer;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.Settings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.reque.printer.printer.EscPosRasterizer;
import com.reque.printer.printer.PrinterConfig;
import com.reque.printer.transport.PrinterTransport;
import com.reque.printer.transport.TransportFactory;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_CODE = 101;

    private TextView tvPrinterName;
    private TextView tvPrinterMac;
    private SwitchCompat switchEnabled;
    private SwitchCompat switchDefault;
    private SwitchCompat switchKeepAlive;
    private Button btnDeletePrinter;

    private Spinner spDriver;
    private Spinner spMediaType;
    private Spinner spDithering;
    private EditText etDpi;
    private EditText etPaperWidth;
    private EditText etPaperHeight;
    private EditText etMarginLeft;
    private EditText etMarginTop;
    private EditText etMarginRight;
    private EditText etMarginBottom;
    private EditText etSpeedLimit;
    private EditText etDelayLabel;
    private SwitchCompat switchSkipWhitespace;

    private Button btnPrintTestPage;
    private Button btnSave;
    private Button btnSystemPrintSettings;
    private TextView tvServiceStatus;

    private PrinterConfig config;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final String[] DRIVER_OPTIONS = {"ESC / POS", "Star Line Mode", "TSPL"};
    private final String[] MEDIA_OPTIONS = {"Receipt", "Continuous", "Label", "Page"};
    private final String[] DITHER_OPTIONS = {"Gradient", "Threshold", "None"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupSpinners();
        loadConfigToUi();
        checkPermissions();
        handleIncomingPrintIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingPrintIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
    }

    private void initViews() {
        tvPrinterName = findViewById(R.id.tv_printer_name);
        tvPrinterMac = findViewById(R.id.tv_printer_mac);
        switchEnabled = findViewById(R.id.switch_enabled);
        switchDefault = findViewById(R.id.switch_default);
        switchKeepAlive = findViewById(R.id.switch_keep_alive);
        btnDeletePrinter = findViewById(R.id.btn_delete_printer);

        spDriver = findViewById(R.id.sp_driver);
        spMediaType = findViewById(R.id.sp_media_type);
        spDithering = findViewById(R.id.sp_dithering);
        etDpi = findViewById(R.id.et_dpi);
        etPaperWidth = findViewById(R.id.et_paper_width);
        etPaperHeight = findViewById(R.id.et_paper_height);
        etMarginLeft = findViewById(R.id.et_margin_left);
        etMarginTop = findViewById(R.id.et_margin_top);
        etMarginRight = findViewById(R.id.et_margin_right);
        etMarginBottom = findViewById(R.id.et_margin_bottom);
        etSpeedLimit = findViewById(R.id.et_speed_limit);
        etDelayLabel = findViewById(R.id.et_delay_label);
        switchSkipWhitespace = findViewById(R.id.switch_skip_whitespace);

        btnPrintTestPage = findViewById(R.id.btn_print_test_page);
        btnSave = findViewById(R.id.btn_save);
        btnSystemPrintSettings = findViewById(R.id.btn_system_print_settings);
        tvServiceStatus = findViewById(R.id.tv_service_status);

        btnDeletePrinter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPairedDevicePicker();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfigFromUi();
                Toast.makeText(MainActivity.this, "Settings saved", Toast.LENGTH_SHORT).show();
            }
        });

        btnPrintTestPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfigFromUi();
                performTestPrint();
            }
        });

        btnSystemPrintSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptEnablePrintService();
            }
        });

        tvServiceStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptEnablePrintService();
            }
        });
    }

    private void setupSpinners() {
        ArrayAdapter<String> driverAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, DRIVER_OPTIONS);
        spDriver.setAdapter(driverAdapter);

        ArrayAdapter<String> mediaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MEDIA_OPTIONS);
        spMediaType.setAdapter(mediaAdapter);

        ArrayAdapter<String> ditherAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, DITHER_OPTIONS);
        spDithering.setAdapter(ditherAdapter);
    }

    private void loadConfigToUi() {
        config = PrinterConfig.load(this);

        if (config.getBluetoothName() != null && !config.getBluetoothName().isEmpty() && !"Not Configured".equals(config.getBluetoothName())) {
            tvPrinterName.setText(config.getBluetoothName());
        } else {
            tvPrinterName.setText("Thermal POS Printer");
        }

        if (config.getBluetoothMac() != null && !config.getBluetoothMac().isEmpty()) {
            tvPrinterMac.setText(config.getBluetoothMac());
        } else {
            tvPrinterMac.setText("Tap 'Change / Select Printer' below");
        }

        switchEnabled.setChecked(config.isEnabled());
        switchDefault.setChecked(config.isDefaultPrinter());
        switchKeepAlive.setChecked(config.isKeepAlive());

        setSpinnerSelection(spDriver, DRIVER_OPTIONS, config.getDriver());
        setSpinnerSelection(spMediaType, MEDIA_OPTIONS, config.getMediaType());
        setSpinnerSelection(spDithering, DITHER_OPTIONS, config.getDithering());

        etDpi.setText(String.valueOf(config.getDpi()));
        etPaperWidth.setText(String.format(java.util.Locale.US, "%.1f", config.getPaperWidthCm()));
        etPaperHeight.setText(String.format(java.util.Locale.US, "%.1f", config.getPaperHeightCm()));
        etMarginLeft.setText(String.format(java.util.Locale.US, "%.1f", config.getMarginLeftCm()));
        etMarginTop.setText(String.format(java.util.Locale.US, "%.1f", config.getMarginTopCm()));
        etMarginRight.setText(String.format(java.util.Locale.US, "%.1f", config.getMarginRightCm()));
        etMarginBottom.setText(String.format(java.util.Locale.US, "%.1f", config.getMarginBottomCm()));
        etSpeedLimit.setText(String.format(java.util.Locale.US, "%.1f", config.getSpeedLimit()));
        etDelayLabel.setText(String.format(java.util.Locale.US, "%.1f", config.getDelayPerLabel()));
        switchSkipWhitespace.setChecked(config.isSkipWhitespace());
    }

    private void setSpinnerSelection(Spinner spinner, String[] options, String value) {
        if (value == null) return;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(value.trim())) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void saveConfigFromUi() {
        config.setEnabled(switchEnabled.isChecked());
        config.setDefaultPrinter(switchDefault.isChecked());
        config.setKeepAlive(switchKeepAlive.isChecked());

        config.setDriver((String) spDriver.getSelectedItem());
        config.setMediaType((String) spMediaType.getSelectedItem());
        config.setDithering((String) spDithering.getSelectedItem());

        try {
            config.setDpi(Integer.parseInt(etDpi.getText().toString().trim()));
        } catch (Exception e) {
            config.setDpi(203);
        }

        try {
            config.setPaperWidthCm(Float.parseFloat(etPaperWidth.getText().toString().trim()));
        } catch (Exception e) {
            config.setPaperWidthCm(8.0f);
        }

        try {
            config.setPaperHeightCm(Float.parseFloat(etPaperHeight.getText().toString().trim()));
        } catch (Exception e) {
            config.setPaperHeightCm(0.0f);
        }

        try {
            config.setMarginLeftCm(Float.parseFloat(etMarginLeft.getText().toString().trim()));
            config.setMarginTopCm(Float.parseFloat(etMarginTop.getText().toString().trim()));
            config.setMarginRightCm(Float.parseFloat(etMarginRight.getText().toString().trim()));
            config.setMarginBottomCm(Float.parseFloat(etMarginBottom.getText().toString().trim()));
            config.setSpeedLimit(Float.parseFloat(etSpeedLimit.getText().toString().trim()));
            config.setDelayPerLabel(Float.parseFloat(etDelayLabel.getText().toString().trim()));
        } catch (Exception ignored) {}

        config.setSkipWhitespace(switchSkipWhitespace.isChecked());
        config.setAutoCut(!PrinterConfig.MEDIA_CONTINUOUS.equals(config.getMediaType()));
        config.save(this);
    }

    private void showPairedDevicePicker() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!adapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        if (pairedDevices == null || pairedDevices.isEmpty()) {
            Toast.makeText(this, "No paired devices found. Pair printer in Bluetooth settings first.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            return;
        }

        final List<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
        String[] deviceNames = new String[deviceList.size()];
        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice dev = deviceList.get(i);
            deviceNames[i] = (dev.getName() != null ? dev.getName() : "Unknown") + "\n" + dev.getAddress();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Paired Thermal Printer");
        builder.setItems(deviceNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BluetoothDevice selected = deviceList.get(which);
                config.setBluetoothName(selected.getName() != null ? selected.getName() : "Unknown");
                config.setBluetoothMac(selected.getAddress());
                config.setConnectionType(PrinterConfig.TYPE_BLUETOOTH);
                config.save(MainActivity.this);

                tvPrinterName.setText(config.getBluetoothName());
                tvPrinterMac.setText(config.getBluetoothMac());
                Toast.makeText(MainActivity.this, "Selected: " + config.getBluetoothName(), Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private boolean isPrintServiceEnabled() {
        try {
            String enabledServices = Settings.Secure.getString(getContentResolver(), "enabled_print_services");
            if (enabledServices != null && enabledServices.contains(getPackageName())) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void updateServiceStatus() {
        if (tvServiceStatus == null) return;
        boolean enabled = isPrintServiceEnabled();
        if (enabled) {
            tvServiceStatus.setText("âœ” Android Print Service: Active & Ready");
            tvServiceStatus.setTextColor(Color.parseColor("#34D399"));
        } else {
            tvServiceStatus.setText("âš ï¸ Android Print Service: OFF (Tap to enable)");
            tvServiceStatus.setTextColor(Color.parseColor("#F87171"));
        }
    }

    private void promptEnablePrintService() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Print Service")
                .setMessage("To print directly from web apps and Chrome:\n\n1. On the next screen, tap \"Thermal Print Driver\"\n2. Switch the toggle at top from OFF to ON\n3. Tap Allow/OK")
                .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS));
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "Open Settings -> Printing", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performTestPrint() {
        if (!config.isConfigured()) {
            Toast.makeText(this, "Please select a paired printer first", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Printing test page...", Toast.LENGTH_SHORT).show();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                PrinterTransport transport = null;
                try {
                    byte[] receiptBytes = EscPosRasterizer.generateTestReceipt(config);
                    transport = TransportFactory.getTransport(config);
                    transport.connect();
                    transport.write(receiptBytes);
                    transport.close();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Test page printed successfully!", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Print error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    if (transport != null) transport.close();
                }
            }
        });
    }

    /**
     * Intercepts rawbt:base64,... intents for drop-in RawBT replacement.
     */
    private void handleIncomingPrintIntent(Intent intent) {
        if (intent == null) return;
        Uri dataUri = intent.getData();
        if (dataUri == null) return;

        String scheme = dataUri.getScheme();
        String uriStr = dataUri.toString();

        if ("rawbt".equalsIgnoreCase(scheme) || uriStr.startsWith("rawbt:base64,")) {
            String base64Payload = uriStr.substring(uriStr.indexOf(",") + 1);
            try {
                final byte[] rawBytes = Base64.decode(base64Payload, Base64.DEFAULT);
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        PrinterTransport transport = null;
                        try {
                            PrinterConfig currentConfig = PrinterConfig.load(MainActivity.this);
                            transport = TransportFactory.getTransport(currentConfig);
                            transport.connect();
                            transport.write(rawBytes);
                            transport.close();
                        } catch (Exception ignored) {
                        } finally {
                            if (transport != null) transport.close();
                        }
                    }
                });
                Toast.makeText(this, "Printing received ESC/POS data...", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        }
    }

    private void checkPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQ_CODE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

