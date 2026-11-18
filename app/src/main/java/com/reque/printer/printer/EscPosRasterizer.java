package com.reque.printer.printer;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EscPosRasterizer {

    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;
    private static final byte LF = 0x0A;

    // Slicing bitmap into 128-line chunks prevents buffer overflow in low-memory thermal printer firmware
    private static final int CHUNK_HEIGHT = 128;

    public static class ContentBounds {
        public int minX = -1;
        public int maxX = -1;
        public int minY = -1;
        public int maxY = -1;

        public boolean hasContent() {
            return minY >= 0 && maxY >= minY && minX >= 0 && maxX >= minX;
        }

        public int getWidth() {
            return hasContent() ? (maxX - minX + 1) : 0;
        }

        public int getHeight() {
            return hasContent() ? (maxY - minY + 1) : 0;
        }
    }

    /**
     * Scans the bitmap to locate the 2D bounding box of all printed content.
     * Uses a row threshold of >= 2 dark pixels to filter out stray anti-aliased noise.
     */
    public static ContentBounds findContentBounds(Bitmap bitmap, int width, int height) {
        ContentBounds bounds = new ContentBounds();
        int[] row = new int[width];
        final int MIN_ROW_DARK_PIXELS = 2;

        for (int y = 0; y < height; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            int rowDarkCount = 0;
            int rowMinX = -1;
            int rowMaxX = -1;

            for (int x = 0; x < width; x++) {
                int pixel = row[x];
                int alpha = Color.alpha(pixel);
                if (alpha > 50) {
                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int bl = Color.blue(pixel);
                    int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * bl);
                    if (luminance < 160) {
                        rowDarkCount++;
                        if (rowMinX == -1) rowMinX = x;
                        rowMaxX = x;
                    }
                }
            }

            if (rowDarkCount >= MIN_ROW_DARK_PIXELS) {
                if (bounds.minY == -1) bounds.minY = y;
                bounds.maxY = y;
                if (bounds.minX == -1 || rowMinX < bounds.minX) bounds.minX = rowMinX;
                if (bounds.maxX == -1 || rowMaxX > bounds.maxX) bounds.maxX = rowMaxX;
            }
        }
        return bounds;
    }

    /**
     * Stitches multiple PDF page bitmaps vertically into a single seamless continuous roll bitmap.
     * Trims top whitespace on the first page, preserves continuity across pages, and trims trailing whitespace on the last page.
     */
    public static Bitmap stitchPages(List<Bitmap> pages, int targetWidth) {
        if (pages == null || pages.isEmpty()) return null;
        java.util.List<Bitmap> contentSlices = new java.util.ArrayList<>();
        int totalHeight = 0;

        for (int i = 0; i < pages.size(); i++) {
            Bitmap page = pages.get(i);
            int w = targetWidth;
            int h = (int) ((float) page.getHeight() * targetWidth / page.getWidth());
            if (h <= 0) h = 1;
            Bitmap scaledPage = (page.getWidth() == w && page.getHeight() == h) ? page :
                    Bitmap.createScaledBitmap(page, w, h, true);

            ContentBounds bounds = findContentBounds(scaledPage, w, h);
            if (!bounds.hasContent()) {
                if (scaledPage != page) scaledPage.recycle();
                continue;
            }

            int top = Math.max(0, bounds.minY - 4);
            int bottom = Math.min(h, bounds.maxY + (i == pages.size() - 1 ? 16 : 8));
            int sliceH = bottom - top;
            if (sliceH > 0) {
                Bitmap slice = Bitmap.createBitmap(scaledPage, 0, top, w, sliceH);
                contentSlices.add(slice);
                totalHeight += sliceH;
            }
            if (scaledPage != page) scaledPage.recycle();
        }

        if (contentSlices.isEmpty()) return null;
        if (contentSlices.size() == 1) return contentSlices.get(0);

        Bitmap stitched = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(stitched);
        canvas.drawColor(Color.WHITE);

        int currentY = 0;
        for (Bitmap slice : contentSlices) {
            canvas.drawBitmap(slice, 0, currentY, null);
            currentY += slice.getHeight();
            slice.recycle();
        }
        return stitched;
    }

    /**
     * Finds the last vertical row containing any printed (non-white) content.
     */
    public static int findLastContentY(Bitmap bitmap, int width, int height) {
        ContentBounds bounds = findContentBounds(bitmap, width, height);
        return bounds.maxY;
    }

    public static byte[] bitmapToEscPos(Bitmap sourceBitmap, int targetWidthDots, boolean autoCut) {
        return bitmapToEscPos(sourceBitmap, targetWidthDots, autoCut, "Threshold", true, 0.0f);
    }

    public static byte[] bitmapToEscPos(Bitmap sourceBitmap, int targetWidthDots, boolean autoCut, String dithering) {
        return bitmapToEscPos(sourceBitmap, targetWidthDots, autoCut, dithering, true, 0.0f);
    }

    public static byte[] bitmapToEscPos(Bitmap sourceBitmap, int targetWidthDots, boolean autoCut, String dithering, boolean skipWhitespace) {
        return bitmapToEscPos(sourceBitmap, targetWidthDots, autoCut, dithering, skipWhitespace, 0.0f);
    }

    /**
     * Converts an Android Bitmap into ESC/POS GS v 0 raster commands.
     * Automatically trims trailing blank whitespace and auto-scales narrow receipts
     * to fill the full printable width (576 dots for 80mm).
     */
    public static byte[] bitmapToEscPos(Bitmap sourceBitmap, int targetWidthDots, boolean autoCut, String dithering, boolean skipWhitespace, float marginBottomCm) {
        if (sourceBitmap == null) return new byte[0];

        int width = targetWidthDots;
        int height = (int) ((float) sourceBitmap.getHeight() * targetWidthDots / sourceBitmap.getWidth());
        if (height <= 0) height = 1;

        Bitmap bitmap = sourceBitmap;
        boolean needsRecycleBase = false;
        if (sourceBitmap.getWidth() != width || sourceBitmap.getHeight() != height) {
            bitmap = Bitmap.createScaledBitmap(sourceBitmap, width, height, true);
            needsRecycleBase = true;
        }

        // Detect content bounds (minX, maxX, minY, maxY) with noise filtering
        ContentBounds bounds = findContentBounds(bitmap, width, height);
        if (!bounds.hasContent()) {
            // Page is completely blank (e.g. trailing blank page from browser) - do not print
            if (needsRecycleBase && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return new byte[0];
        }

        Bitmap printBitmap;
        boolean needsRecyclePrint = false;

        int contentWidth = bounds.getWidth();
        if (!skipWhitespace) {
            // Raw uncropped mode: preserve all page margins and whitespace
            printBitmap = bitmap;
        } else if (contentWidth < (int) (width * 0.88f) && contentWidth > 50) {
            int cropLeft = Math.max(0, bounds.minX - 4);
            int cropRight = Math.min(width, bounds.maxX + 4);
            int cropW = cropRight - cropLeft;

            // Trim top whitespace and bottom whitespace tightly
            int cropTop = Math.max(0, bounds.minY - 4);
            int cropBottom = Math.min(height, bounds.maxY + 16);
            int cropH = cropBottom - cropTop;

            if (cropW > 0 && cropH > 0) {
                Bitmap cropped = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH);
                float scale = (float) width / cropW;
                int scaledH = Math.max(1, (int) (cropH * scale));
                printBitmap = Bitmap.createScaledBitmap(cropped, width, scaledH, true);
                if (cropped != printBitmap) {
                    cropped.recycle();
                }
                needsRecyclePrint = true;
            } else {
                printBitmap = bitmap;
            }
        } else {
            // Already full width - trim vertical blank tail only
            int effectiveH = Math.min(height, bounds.maxY + 24);
            if (effectiveH < height && effectiveH > 0) {
                printBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, effectiveH);
                needsRecyclePrint = true;
            } else {
                printBitmap = bitmap;
            }
        }

        if (needsRecycleBase && bitmap != printBitmap && !bitmap.isRecycled()) {
            bitmap.recycle();
        }

        int finalWidth = printBitmap.getWidth();
        int finalHeight = printBitmap.getHeight();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // ESC @ (Initialize printer)
        out.write(ESC);
        out.write('@');

        // ESC 3 0 (Set line spacing to 0)
        out.write(ESC);
        out.write('3');
        out.write(0);

        int bytesPerRow = (finalWidth + 7) / 8;
        int[] rowPixels = new int[finalWidth];

        boolean isGradient = "Gradient".equalsIgnoreCase(dithering);
        int[][] diffErrors = isGradient ? new int[2][finalWidth + 2] : null;

        for (int yOffset = 0; yOffset < finalHeight; yOffset += CHUNK_HEIGHT) {
            int chunkHeight = Math.min(CHUNK_HEIGHT, finalHeight - yOffset);

            // GS v 0 m xL xH yL yH
            out.write(GS);
            out.write('v');
            out.write('0');
            out.write(0); // Normal mode
            out.write(bytesPerRow & 0xFF);
            out.write((bytesPerRow >> 8) & 0xFF);
            out.write(chunkHeight & 0xFF);
            out.write((chunkHeight >> 8) & 0xFF);

            for (int y = yOffset; y < yOffset + chunkHeight; y++) {
                printBitmap.getPixels(rowPixels, 0, finalWidth, 0, y, finalWidth, 1);
                int curLine = y & 1;
                int nextLine = 1 - curLine;
                if (isGradient) {
                    java.util.Arrays.fill(diffErrors[nextLine], 0);
                }

                for (int byteIdx = 0; byteIdx < bytesPerRow; byteIdx++) {
                    int b = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = (byteIdx * 8) + bit;
                        if (x < finalWidth) {
                            int pixel = rowPixels[x];
                            int alpha = Color.alpha(pixel);
                            if (alpha > 50) {
                                int r = Color.red(pixel);
                                int g = Color.green(pixel);
                                int bl = Color.blue(pixel);
                                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * bl);

                                if (isGradient) {
                                    int val = gray + diffErrors[curLine][x + 1];
                                    if (val < 160) {
                                        b |= (1 << (7 - bit));
                                        int err = val; // ideal black is 0
                                        diffErrors[curLine][x + 2] += (err * 7) / 16;
                                        diffErrors[nextLine][x]     += (err * 3) / 16;
                                        diffErrors[nextLine][x + 1] += (err * 5) / 16;
                                        diffErrors[nextLine][x + 2] += (err * 1) / 16;
                                    } else {
                                        int err = val - 255; // ideal white is 255
                                        diffErrors[curLine][x + 2] += (err * 7) / 16;
                                        diffErrors[nextLine][x]     += (err * 3) / 16;
                                        diffErrors[nextLine][x + 1] += (err * 5) / 16;
                                        diffErrors[nextLine][x + 2] += (err * 1) / 16;
                                    }
                                } else {
                                    if (gray < 160) {
                                        b |= (1 << (7 - bit));
                                    }
                                }
                            }
                        }
                    }
                    out.write(b);
                }
            }
        }

        if (needsRecyclePrint && !printBitmap.isRecycled()) {
            printBitmap.recycle();
        }

        // Restore standard line spacing (1/6 inch ~30 dots) to cancel ESC 3 0
        out.write(ESC);
        out.write('2');

        // Calculate feed lines:
        // Thermal POS cutter blades sit 28-35mm above the print head.
        // Base clearance feed is 8 lines (~30mm). Extra margin adds ~2.5 lines per cm.
        int extraMarginLines = (int) Math.max(0, marginBottomCm * 2.5f);
        int totalFeedLines = Math.min(40, 8 + extraMarginLines);

        out.write(ESC);
        out.write('d');
        out.write(totalFeedLines);

        // Auto cut paper if requested
        if (autoCut) {
            out.write(GS);
            out.write('V');
            out.write(0); // Full cut
        }

        return out.toByteArray();
    }

    /**
     * Generates a self-test diagnostic receipt.
     */
    public static byte[] generateTestReceipt(PrinterConfig config) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(ESC);
            out.write('@'); // Init

            // Center alignment
            out.write(ESC);
            out.write('a');
            out.write(1);

            // Double size title
            out.write(GS);
            out.write('!');
            out.write(0x11);
            out.write("PRINTER SELF-TEST\n".getBytes());

            // Normal text
            out.write(GS);
            out.write('!');
            out.write(0x00);
            out.write("Native Android Driver\n".getBytes());
            out.write("================================\n".getBytes());

            // Left alignment
            out.write(ESC);
            out.write('a');
            out.write(0);

            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            out.write(("Date: " + dateStr + "\n").getBytes());
            out.write(("Type: " + config.getConnectionType() + "\n").getBytes());
            if (PrinterConfig.TYPE_BLUETOOTH.equals(config.getConnectionType())) {
                out.write(("Name: " + config.getBluetoothName() + "\n").getBytes());
                out.write(("MAC : " + config.getBluetoothMac() + "\n").getBytes());
            } else {
                out.write(("Host: " + config.getNetworkHost() + "\n").getBytes());
                out.write(("Port: " + config.getNetworkPort() + "\n").getBytes());
            }
            out.write(("Width: " + (config.getPaperWidthDots() == PrinterConfig.WIDTH_80MM_DOTS ? "80mm (576 dots)" : "58mm (384 dots)") + "\n").getBytes());
            out.write("Status: DRIVER ACTIVE & OK\n".getBytes());
            out.write("--------------------------------\n".getBytes());

            // Center alignment
            out.write(ESC);
            out.write('a');
            out.write(1);
            out.write("*** READY TO PRINT ***\n".getBytes());
            out.write("Web and Android system print enabled.\n\n\n".getBytes());

            // Feed & Cut
            out.write(ESC);
            out.write('2'); // Restore normal line spacing
            out.write(ESC);
            out.write('d');
            out.write(8); // Clear cutter blade

            if (config.isAutoCut()) {
                out.write(GS);
                out.write('V');
                out.write(0);
            }
        } catch (IOException ignored) {}

        return out.toByteArray();
    }
}

