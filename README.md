# ReQue Thermal Print Service

A high-performance native Android thermal POS printer driver and system `PrintService` (`com.reque.printer`) built for the ReQue POS ecosystem. Connects Android applications and web applications directly to ESC/POS thermal printers over **Bluetooth SPP** and **Network TCP/IP (Port 9100)** without third-party dependencies.

---

## Architecture Overview

```
[Web POS (window.print()) / Android Apps]
                   │
                   ▼
         [Android Print Spooler]
                   │
                   ▼
     ┌─────────────────────────────┐
     │    ThermalPrintService      │  <-- System PrintService
     │ • Multi-page canvas stitch  │
     │ • Floyd-Steinberg dither    │
     │ • ESC/POS raster (GS v 0)   │
     │ • Auto-feed & clean cutter  │
     └─────────────┬───────────────┘
                   │
         ┌─────────┴─────────┐
         ▼                   ▼
  [Bluetooth SPP]     [Network TCP:9100]
         │                   │
         ▼                   ▼
     Thermal Receipt Printer (80mm / 58mm)
```

In addition to system printing, the app registers a `rawbt:` URL scheme handler (`rawbt:base64,...`) for 100% backward-compatibility with web/POS apps designed for RawBT.

---

## Features

1. **Seamless Continuous Receipts**:
   - Automatically stitches multi-page print spooler PDFs into a single continuous thermal receipt with zero gaps, duplicate headers, or middle cuts.
2. **Floyd-Steinberg Dithering & Thresholding**:
   - High-fidelity image and receipt reproduction with "Gradient" Floyd-Steinberg error diffusion and crisp text thresholding.
3. **Hardware Cutter Clearance**:
   - Restores standard line pitch (`ESC 2`) and feeds clearance margin (`ESC d 8`) before cutting (`GS V 0`), ensuring footers and QR codes are never sliced across.
4. **Dual Connectivity**:
   - **Bluetooth (SPP)**: Connects to standard Bluetooth thermal printers via RFCOMM (`00001101-0000-1000-8000-00805F9B34FB`) with automated 600ms buffer flush before disconnect.
   - **Network (TCP/IP)**: Direct raw socket printing to Wi-Fi/Ethernet printers (standard port `9100`).
5. **Drop-in RawBT Compatibility**:
   - Accepts incoming `rawbt:base64,...` intents, decodes the raw ESC/POS bytes, and writes them straight to the connected printer.
6. **Dark-Themed Settings UI**:
   - Complete configuration interface for paired Bluetooth selection, network endpoints, dithering mode, custom margins, speed limit, and self-test printing.

---

## Setup Guide

### 1. Configure the Printer in App
1. Launch **ReQue Print Service** on your Android device.
2. Choose your connection type:
   - **Bluetooth (SPP)**: Tap **Change / Select Printer** and choose your printer from the list (pair the printer in Android Bluetooth settings first if not yet paired).
   - **Network (TCP)**: Enter the printer's LAN IP address and port (default: `9100`).
3. Tap **Print test page** to verify Bluetooth/Network connectivity, raster output, and clean cutter action.

### 2. Enable the Android Print Service
1. Tap **OS Settings** in the app (or go to **Android Settings** &rarr; **Connected devices** &rarr; **Connection preferences** &rarr; **Printing**).
2. Find **ReQue Print Service** and toggle it **ON**.

### 3. Printing from Web Applications (`window.print()`)
When visiting your POS or billing web application in Google Chrome or any browser:
1. Trigger `window.print()` or tap **Print Bill**.
2. In Android's print preview dialog, select **ReQue Thermal POS (80mm)**.
3. Tap the Print button.
