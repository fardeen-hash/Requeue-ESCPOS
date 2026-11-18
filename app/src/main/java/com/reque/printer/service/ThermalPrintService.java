package com.reque.printer.service;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintDocument;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.reque.printer.printer.EscPosRasterizer;
import com.reque.printer.printer.PrinterConfig;
import com.reque.printer.transport.PrinterTransport;
import com.reque.printer.transport.TransportFactory;

public class ThermalPrintService extends PrintService {

    private static final String TAG = "ThermalPrintService";
    private static final String DEFAULT_PRINTER_LOCAL_ID = "thermal_pos_default";

    // Standard receipt roll sizes with realistic heights to prevent 18,000px OutOfMemory errors
    public static final PrintAttributes.MediaSize MEDIA_80MM_CONTINUOUS =
            new PrintAttributes.MediaSize("ROLL_80_CONTINUOUS", "80mm Continuous Roll", 3150, 19685);
    public static final PrintAttributes.MediaSize MEDIA_80MM_STD =
            new PrintAttributes.MediaSize("ROLL_80X297", "80mm Roll (Standard)", 3150, 11693);
    public static final PrintAttributes.MediaSize MEDIA_80MM_COMPACT =
            new PrintAttributes.MediaSize("ROLL_80X200", "80mm Roll (Compact)", 3150, 7874);
    public static final PrintAttributes.MediaSize MEDIA_80MM_LONG =
            new PrintAttributes.MediaSize("ROLL_80X400", "80mm Roll (Long)", 3150, 15748);
    public static final PrintAttributes.MediaSize MEDIA_58MM_CONTINUOUS =
            new PrintAttributes.MediaSize("ROLL_58_CONTINUOUS", "58mm Continuous Roll", 2283, 19685);
    public static final PrintAttributes.MediaSize MEDIA_58MM_STD =
            new PrintAttributes.MediaSize("ROLL_58X297", "58mm Roll (Standard)", 2283, 11693);
    public static final PrintAttributes.MediaSize MEDIA_58MM_COMPACT =
            new PrintAttributes.MediaSize("ROLL_58X200", "58mm Roll (Compact)", 2283, 7874);

    private final ExecutorService printExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PrinterInfo buildPrinterInfo() {
        PrinterConfig config = PrinterConfig.load(this);
        PrinterId printerId = generatePrinterId(DEFAULT_PRINTER_LOCAL_ID);

        String printerName;
        String btName = config.getBluetoothName();
        if (PrinterConfig.TYPE_BLUETOOTH.equals(config.getConnectionType()) && btName != null && !btName.isEmpty() && !"Not Configured".equals(btName)) {
            printerName = btName + " (" + (config.getPaperWidthDots() == PrinterConfig.WIDTH_80MM_DOTS ? "80mm" : "58mm") + ")";
        } else if (config.isConfigured()) {
            printerName = "Thermal POS (" + (config.getPaperWidthDots() == PrinterConfig.WIDTH_80MM_DOTS ? "80mm" : "58mm") + ")";
        } else {
            printerName = "Thermal POS (Not Configured)";
        }

        PrinterInfo.Builder builder = new PrinterInfo.Builder(printerId, printerName,
                config.isEnabled() ? PrinterInfo.STATUS_IDLE : PrinterInfo.STATUS_UNAVAILABLE);
        builder.setDescription(config.getSummary());

        boolean is80mm = (config.getPaperWidthDots() == PrinterConfig.WIDTH_80MM_DOTS);

        // Populate full capabilities to enable all options in Android's print preview dropdown
        PrinterCapabilitiesInfo.Builder capBuilder = new PrinterCapabilitiesInfo.Builder(printerId)
                .setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0)) // Zero margins for edge-to-edge thermal receipts
                .addResolution(new PrintAttributes.Resolution("203dpi", "203 DPI", 203, 203), true)
                .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME | PrintAttributes.COLOR_MODE_COLOR, PrintAttributes.COLOR_MODE_MONOCHROME)
                .setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE, PrintAttributes.DUPLEX_MODE_NONE);

        // Add standard roll and receipt paper sizes to populate the dropdown
        capBuilder.addMediaSize(MEDIA_80MM_CONTINUOUS, is80mm);
        capBuilder.addMediaSize(MEDIA_80MM_STD, false);
        capBuilder.addMediaSize(MEDIA_80MM_COMPACT, false);
        capBuilder.addMediaSize(MEDIA_80MM_LONG, false);
        capBuilder.addMediaSize(MEDIA_58MM_CONTINUOUS, !is80mm);
        capBuilder.addMediaSize(MEDIA_58MM_STD, false);
        capBuilder.addMediaSize(MEDIA_58MM_COMPACT, false);
        capBuilder.addMediaSize(PrintAttributes.MediaSize.ISO_A4, false);

        builder.setCapabilities(capBuilder.build());
        return builder.build();
    }

    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new PrinterDiscoverySession() {
            @Override
            public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
                addPrinters(new ArrayList<>(Collections.singletonList(buildPrinterInfo())));
            }

            @Override
            public void onStopPrinterDiscovery() {}

            @Override
            public void onValidatePrinters(List<PrinterId> printerIds) {
                // Re-confirm printer validity so Android Spooler recognizes it as active
                addPrinters(new ArrayList<>(Collections.singletonList(buildPrinterInfo())));
            }

            @Override
            public void onStartPrinterStateTracking(PrinterId printerId) {
                // Keep printer status active when user opens print preview
                addPrinters(new ArrayList<>(Collections.singletonList(buildPrinterInfo())));
            }

            @Override
            public void onStopPrinterStateTracking(PrinterId printerId) {}

            @Override
            public void onDestroy() {}
        };
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        if (printJob != null) {
            printJob.cancel();
        }
    }

    @Override
    protected void onPrintJobQueued(final PrintJob printJob) {
        if (printJob == null) return;

        // PrintJob binder methods (start, fail, complete, getDocument) MUST be called from main thread
        printJob.start();

        final PrinterConfig config = PrinterConfig.load(this);
        if (!config.isConfigured()) {
            printJob.fail("Printer not configured in settings");
            return;
        }
        if (!config.isEnabled()) {
            printJob.fail("Printer is disabled in settings");
            return;
        }

        final PrintDocument doc = printJob.getDocument();
        if (doc == null) {
            printJob.fail("No print document received");
            return;
        }

        final ParcelFileDescriptor pfd = doc.getData();
        if (pfd == null) {
            printJob.fail("No print document data received");
            return;
        }

        printExecutor.execute(new Runnable() {
            @Override
            public void run() {
                PrinterTransport transport = null;
                PdfRenderer pdfRenderer = null;
                File tempPdf = null;
                ParcelFileDescriptor seekablePfd = null;
                final List<Bitmap> rawPages = new ArrayList<>();
                try {
                    // Copy streamed pipe data into a seekable temporary file (PdfRenderer requires a seekable descriptor)
                    tempPdf = File.createTempFile("spooler_job_", ".pdf", getCacheDir());
                    try (FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
                         FileOutputStream out = new FileOutputStream(tempPdf)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                        out.flush();
                    }
                    try { pfd.close(); } catch (Throwable ignored) {}

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        seekablePfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
                        pdfRenderer = new PdfRenderer(seekablePfd);
                        int pageCount = pdfRenderer.getPageCount();
                        int targetWidth = config.getPaperWidthDots();

                        for (int i = 0; i < pageCount; i++) {
                            PdfRenderer.Page page = pdfRenderer.openPage(i);
                            int targetHeight = (int) ((float) page.getHeight() * targetWidth / page.getWidth());
                            if (targetHeight <= 0) targetHeight = 1;

                            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                            Canvas canvas = new Canvas(bitmap);
                            canvas.drawColor(Color.WHITE); // Solid white background for thermal monochrome print
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                            page.close();
                            rawPages.add(bitmap);
                        }
                        pdfRenderer.close();
                        pdfRenderer = null;
                        seekablePfd.close();
                        seekablePfd = null;

                        byte[] fullJobData;
                        if (PrinterConfig.MEDIA_PAGE.equals(config.getMediaType())) {
                            ByteArrayOutputStream pageOut = new ByteArrayOutputStream();
                            for (int i = 0; i < rawPages.size(); i++) {
                                Bitmap pageBmp = rawPages.get(i);
                                boolean isLast = (i == rawPages.size() - 1);
                                byte[] pageBytes = EscPosRasterizer.bitmapToEscPos(
                                        pageBmp,
                                        targetWidth,
                                        isLast && config.isAutoCut(),
                                        config.getDithering(),
                                        config.isSkipWhitespace(),
                                        config.getMarginBottomCm()
                                );
                                pageBmp.recycle();
                                if (pageBytes != null && pageBytes.length > 0) {
                                    pageOut.write(pageBytes);
                                }
                            }
                            fullJobData = pageOut.toByteArray();
                        } else {
                            // Default: Seamless Multi-Page Canvas Stitching!
                            // Stitches all pages into a continuous roll and cuts once at the end.
                            Bitmap unifiedReceipt = EscPosRasterizer.stitchPages(rawPages, targetWidth);
                            for (Bitmap b : rawPages) {
                                if (b != unifiedReceipt && !b.isRecycled()) {
                                    b.recycle();
                                }
                            }
                            if (unifiedReceipt != null) {
                                boolean shouldCut = config.isAutoCut() && !PrinterConfig.MEDIA_CONTINUOUS.equals(config.getMediaType());
                                fullJobData = EscPosRasterizer.bitmapToEscPos(
                                        unifiedReceipt,
                                        targetWidth,
                                        shouldCut,
                                        config.getDithering(),
                                        config.isSkipWhitespace(),
                                        config.getMarginBottomCm()
                                );
                                if (!unifiedReceipt.isRecycled()) {
                                    unifiedReceipt.recycle();
                                }
                            } else {
                                fullJobData = new byte[0];
                            }
                        }

                        if (fullJobData != null && fullJobData.length > 0) {
                            transport = TransportFactory.getTransport(config);
                            transport.connect();
                            transport.write(fullJobData);
                            // Allow Bluetooth radio and printer hardware buffer to finish receiving before close
                            try {
                                Thread.sleep(600);
                            } catch (InterruptedException ignored) {}
                            transport.close();
                            transport = null;
                        }

                        Log.i(TAG, "Thermal print job completed successfully");
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    printJob.complete();
                                } catch (Throwable ignored) {}
                            }
                        });
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    printJob.fail("Android 5.0+ required for thermal print rendering");
                                } catch (Throwable ignored) {}
                            }
                        });
                    }
                } catch (final Throwable t) {
                    Log.e(TAG, "Print job execution failed", t);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                printJob.fail("Print failed: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
                            } catch (Throwable ignored) {}
                        }
                    });
                } finally {
                    for (Bitmap b : rawPages) {
                        if (b != null && !b.isRecycled()) {
                            try { b.recycle(); } catch (Throwable ignored) {}
                        }
                    }
                    if (pdfRenderer != null) {
                        try { pdfRenderer.close(); } catch (Throwable ignored) {}
                    }
                    if (seekablePfd != null) {
                        try { seekablePfd.close(); } catch (Throwable ignored) {}
                    }
                    if (tempPdf != null && tempPdf.exists()) {
                        try { tempPdf.delete(); } catch (Throwable ignored) {}
                    }
                    if (transport != null) {
                        transport.close();
                    }
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        printExecutor.shutdown();
    }
}

