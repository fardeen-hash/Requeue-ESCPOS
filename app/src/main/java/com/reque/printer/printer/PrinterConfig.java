package com.reque.printer.printer;

import android.content.Context;
import android.content.SharedPreferences;

public class PrinterConfig {

    public static final String PREF_NAME = "rawbt_replacement_printer_config";

    public static final String TYPE_BLUETOOTH = "BLUETOOTH";
    public static final String TYPE_NETWORK = "NETWORK";

    public static final int WIDTH_80MM_DOTS = 576;
    public static final int WIDTH_58MM_DOTS = 384;

    public static final String DRIVER_ESCPOS = "ESC / POS";
    public static final String MEDIA_RECEIPT = "Receipt";
    public static final String MEDIA_CONTINUOUS = "Continuous";
    public static final String MEDIA_LABEL = "Label";
    public static final String MEDIA_PAGE = "Page";

    public static final String DITHER_GRADIENT = "Gradient";
    public static final String DITHER_THRESHOLD = "Threshold";
    public static final String DITHER_NONE = "None";

    private String connectionType = TYPE_BLUETOOTH;
    private String bluetoothMac = "";
    private String bluetoothName = "Not Configured";
    private String networkHost = "192.168.1.100";
    private int networkPort = 9100;
    private int paperWidthDots = WIDTH_80MM_DOTS;
    private boolean autoCut = true;

    // RawBT Parity Settings
    private boolean enabled = true;
    private boolean defaultPrinter = true;
    private boolean keepAlive = true;
    private String driver = DRIVER_ESCPOS;
    private String mediaType = MEDIA_RECEIPT;
    private String dithering = DITHER_GRADIENT;
    private int dpi = 203;
    private float paperWidthCm = 8.0f;
    private float paperHeightCm = 0.0f; // 0.0 = continuous roll
    // shortcut: Margins, motor speed limits, and label delays are stored for RawBT profile compatibility;
    // thermal POS receipt layouts are managed directly by web CSS and printer hardware firmware.
    private float marginLeftCm = 0.0f;
    private float marginTopCm = 0.0f;
    private float marginRightCm = 0.0f;
    private float marginBottomCm = 0.0f;
    private float speedLimit = 2.0f;
    private float delayPerLabel = 0.0f;
    private boolean skipWhitespace = true;

    public static PrinterConfig load(Context context) {
        PrinterConfig config = new PrinterConfig();
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        config.connectionType = sp.getString("connection_type", TYPE_BLUETOOTH);
        config.bluetoothMac = sp.getString("bluetooth_mac", "");
        config.bluetoothName = sp.getString("bluetooth_name", "Not Configured");
        config.networkHost = sp.getString("network_host", "192.168.1.100");
        config.networkPort = sp.getInt("network_port", 9100);
        config.paperWidthDots = sp.getInt("paper_width_dots", WIDTH_80MM_DOTS);
        config.autoCut = sp.getBoolean("auto_cut", true);

        // Load RawBT Parity options
        config.enabled = sp.getBoolean("enabled", true);
        config.defaultPrinter = sp.getBoolean("default_printer", true);
        config.keepAlive = sp.getBoolean("keep_alive", true);
        config.driver = sp.getString("driver", DRIVER_ESCPOS);
        config.mediaType = sp.getString("media_type", MEDIA_RECEIPT);
        config.dithering = sp.getString("dithering", DITHER_GRADIENT);
        config.dpi = sp.getInt("dpi", 203);
        config.paperWidthCm = sp.getFloat("paper_width_cm", 8.0f);
        config.paperHeightCm = sp.getFloat("paper_height_cm", 0.0f);
        config.marginLeftCm = sp.getFloat("margin_left_cm", 0.0f);
        config.marginTopCm = sp.getFloat("margin_top_cm", 0.0f);
        config.marginRightCm = sp.getFloat("margin_right_cm", 0.0f);
        config.marginBottomCm = sp.getFloat("margin_bottom_cm", 0.0f);
        config.speedLimit = sp.getFloat("speed_limit", 2.0f);
        config.delayPerLabel = sp.getFloat("delay_per_label", 0.0f);
        config.skipWhitespace = sp.getBoolean("skip_whitespace", true);

        // Keep dots in sync with cm width
        if (config.paperWidthCm <= 6.0f) {
            config.paperWidthDots = WIDTH_58MM_DOTS;
        } else {
            config.paperWidthDots = WIDTH_80MM_DOTS;
        }

        return config;
    }

    public void save(Context context) {
        if (paperWidthCm <= 6.0f) {
            paperWidthDots = WIDTH_58MM_DOTS;
        } else {
            paperWidthDots = WIDTH_80MM_DOTS;
        }

        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString("connection_type", connectionType)
                .putString("bluetooth_mac", bluetoothMac)
                .putString("bluetooth_name", bluetoothName)
                .putString("network_host", networkHost)
                .putInt("network_port", networkPort)
                .putInt("paper_width_dots", paperWidthDots)
                .putBoolean("auto_cut", autoCut)
                .putBoolean("enabled", enabled)
                .putBoolean("default_printer", defaultPrinter)
                .putBoolean("keep_alive", keepAlive)
                .putString("driver", driver)
                .putString("media_type", mediaType)
                .putString("dithering", dithering)
                .putInt("dpi", dpi)
                .putFloat("paper_width_cm", paperWidthCm)
                .putFloat("paper_height_cm", paperHeightCm)
                .putFloat("margin_left_cm", marginLeftCm)
                .putFloat("margin_top_cm", marginTopCm)
                .putFloat("margin_right_cm", marginRightCm)
                .putFloat("margin_bottom_cm", marginBottomCm)
                .putFloat("speed_limit", speedLimit)
                .putFloat("delay_per_label", delayPerLabel)
                .putBoolean("skip_whitespace", skipWhitespace)
                .apply();
    }

    public boolean isConfigured() {
        if (TYPE_BLUETOOTH.equals(connectionType)) {
            return bluetoothMac != null && !bluetoothMac.trim().isEmpty();
        } else {
            return networkHost != null && !networkHost.trim().isEmpty();
        }
    }

    public String getSummary() {
        if (TYPE_BLUETOOTH.equals(connectionType)) {
            return "Bluetooth: " + (bluetoothName.isEmpty() ? "Device" : bluetoothName) + " (" + bluetoothMac + ")";
        } else {
            return "Network: " + networkHost + ":" + networkPort;
        }
    }

    public String getConnectionType() { return connectionType; }
    public void setConnectionType(String connectionType) { this.connectionType = connectionType; }

    public String getBluetoothMac() { return bluetoothMac; }
    public void setBluetoothMac(String bluetoothMac) { this.bluetoothMac = bluetoothMac; }

    public String getBluetoothName() { return bluetoothName; }
    public void setBluetoothName(String bluetoothName) { this.bluetoothName = bluetoothName; }

    public String getNetworkHost() { return networkHost; }
    public void setNetworkHost(String networkHost) { this.networkHost = networkHost; }

    public int getNetworkPort() { return networkPort; }
    public void setNetworkPort(int networkPort) { this.networkPort = networkPort; }

    public int getPaperWidthDots() { return paperWidthDots; }
    public void setPaperWidthDots(int paperWidthDots) { this.paperWidthDots = paperWidthDots; }

    public boolean isAutoCut() { return autoCut; }
    public void setAutoCut(boolean autoCut) { this.autoCut = autoCut; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isDefaultPrinter() { return defaultPrinter; }
    public void setDefaultPrinter(boolean defaultPrinter) { this.defaultPrinter = defaultPrinter; }

    public boolean isKeepAlive() { return keepAlive; }
    public void setKeepAlive(boolean keepAlive) { this.keepAlive = keepAlive; }

    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public String getDithering() { return dithering; }
    public void setDithering(String dithering) { this.dithering = dithering; }

    public int getDpi() { return dpi; }
    public void setDpi(int dpi) { this.dpi = dpi; }

    public float getPaperWidthCm() { return paperWidthCm; }
    public void setPaperWidthCm(float paperWidthCm) { this.paperWidthCm = paperWidthCm; }

    public float getPaperHeightCm() { return paperHeightCm; }
    public void setPaperHeightCm(float paperHeightCm) { this.paperHeightCm = paperHeightCm; }

    public float getMarginLeftCm() { return marginLeftCm; }
    public void setMarginLeftCm(float marginLeftCm) { this.marginLeftCm = marginLeftCm; }

    public float getMarginTopCm() { return marginTopCm; }
    public void setMarginTopCm(float marginTopCm) { this.marginTopCm = marginTopCm; }

    public float getMarginRightCm() { return marginRightCm; }
    public void setMarginRightCm(float marginRightCm) { this.marginRightCm = marginRightCm; }

    public float getMarginBottomCm() { return marginBottomCm; }
    public void setMarginBottomCm(float marginBottomCm) { this.marginBottomCm = marginBottomCm; }

    public float getSpeedLimit() { return speedLimit; }
    public void setSpeedLimit(float speedLimit) { this.speedLimit = speedLimit; }

    public float getDelayPerLabel() { return delayPerLabel; }
    public void setDelayPerLabel(float delayPerLabel) { this.delayPerLabel = delayPerLabel; }

    public boolean isSkipWhitespace() { return skipWhitespace; }
    public void setSkipWhitespace(boolean skipWhitespace) { this.skipWhitespace = skipWhitespace; }
}

