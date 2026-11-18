package com.reque.printer;

import org.junit.Test;

import com.reque.printer.printer.EscPosRasterizer;
import com.reque.printer.printer.PrinterConfig;

import static org.junit.Assert.*;

public class PrinterTest {

    @Test
    public void testPrinterConfigDefaults() {
        PrinterConfig config = new PrinterConfig();
        assertEquals(PrinterConfig.TYPE_BLUETOOTH, config.getConnectionType());
        assertEquals(PrinterConfig.WIDTH_80MM_DOTS, config.getPaperWidthDots());
        assertTrue(config.isAutoCut());
        assertFalse(config.isConfigured()); // MAC is initially empty
    }

    @Test
    public void testPrinterConfigSummary() {
        PrinterConfig config = new PrinterConfig();
        config.setConnectionType(PrinterConfig.TYPE_BLUETOOTH);
        config.setBluetoothName("RP-80");
        config.setBluetoothMac("00:11:22:33:44:55");
        assertTrue(config.isConfigured());
        assertEquals("Bluetooth: RP-80 (00:11:22:33:44:55)", config.getSummary());

        config.setConnectionType(PrinterConfig.TYPE_NETWORK);
        config.setNetworkHost("192.168.1.200");
        config.setNetworkPort(9100);
        assertTrue(config.isConfigured());
        assertEquals("Network: 192.168.1.200:9100", config.getSummary());
    }

    @Test
    public void testPrinterConfigRawBtParityDefaults() {
        PrinterConfig config = new PrinterConfig();
        assertTrue(config.isEnabled());
        assertTrue(config.isDefaultPrinter());
        assertTrue(config.isKeepAlive());
        assertEquals("ESC / POS", config.getDriver());
        assertEquals("Receipt", config.getMediaType());
        assertEquals("Gradient", config.getDithering());
        assertEquals(203, config.getDpi());
        assertEquals(8.0f, config.getPaperWidthCm(), 0.01f);
        assertEquals(0.0f, config.getPaperHeightCm(), 0.01f);
        assertEquals(0.0f, config.getMarginLeftCm(), 0.01f);
        assertEquals(0.0f, config.getMarginTopCm(), 0.01f);
        assertEquals(0.0f, config.getMarginRightCm(), 0.01f);
        assertEquals(0.0f, config.getMarginBottomCm(), 0.01f);
        assertEquals(2.0f, config.getSpeedLimit(), 0.01f);
        assertEquals(0.0f, config.getDelayPerLabel(), 0.01f);
        assertTrue(config.isSkipWhitespace());
    }

    @Test
    public void testGenerateTestReceipt() {
        PrinterConfig config = new PrinterConfig();
        config.setConnectionType(PrinterConfig.TYPE_BLUETOOTH);
        config.setBluetoothName("RP-80");
        config.setBluetoothMac("00:11:22:33:44:55");
        config.setAutoCut(true);

        byte[] receipt = EscPosRasterizer.generateTestReceipt(config);
        assertNotNull(receipt);
        assertTrue(receipt.length > 50);

        // Verify ESC @ (init: 0x1B, 0x40)
        assertEquals(0x1B, receipt[0]);
        assertEquals(0x40, receipt[1]);

        // Verify cut command at end: GS V 0 (0x1D, 0x56, 0x00)
        int len = receipt.length;
        assertEquals(0x1D, receipt[len - 3]);
        assertEquals(0x56, receipt[len - 2]);
        assertEquals(0x00, receipt[len - 1]);
    }

    @Test
    public void testContentBoundsCalculation() {
        EscPosRasterizer.ContentBounds bounds = new EscPosRasterizer.ContentBounds();
        assertFalse(bounds.hasContent());
        assertEquals(0, bounds.getWidth());
        assertEquals(0, bounds.getHeight());

        bounds.minX = 10;
        bounds.maxX = 50;
        bounds.minY = 5;
        bounds.maxY = 25;
        assertTrue(bounds.hasContent());
        assertEquals(41, bounds.getWidth());
        assertEquals(21, bounds.getHeight());
    }

    @Test
    public void testRasterizerNullAndEmptySafety() {
        assertNull(EscPosRasterizer.stitchPages(null, 576));
        assertNull(EscPosRasterizer.stitchPages(new java.util.ArrayList<android.graphics.Bitmap>(), 576));

        byte[] emptyEscPos = EscPosRasterizer.bitmapToEscPos(null, 576, true, "Gradient", true);
        assertNotNull(emptyEscPos);
        assertEquals(0, emptyEscPos.length);
    }
}

