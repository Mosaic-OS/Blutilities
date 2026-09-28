**English** | [Magyar](README_HU.md)

<p align="center">
  <img src="images/icon.png" width="128" height="128" alt="Blutilities icon">
</p>

<h1 align="center">Blutilities</h1>

<p align="center">
  <strong>Bluetooth audio codec management directly from your Quick Settings.</strong>
</p>

<br>

## Overview

**Blutilities** is a lightweight, Material Design-inspired Android utility that brings a MosaicOS Beta 8 feature to compatible Android devices, making Bluetooth audio management effortless. Instead of digging deep into Android's Developer Options, Blutilities provides a convenient **Quick Settings Tile** to switch between active audio codecs on the fly.

It is designed with audiophiles in mind, offering extensive support for high-res codecs-especially **LDAC**-allowing you to easily force specific playback qualities (e.g., 990 kbps, 660 kbps) for your connected A2DP devices.

## Key Features

* **Quick Settings Integration:** 1-tap access to your active Bluetooth audio configuration. The tile subtitle shows the codec currently in use, including the LDAC bitrate.
* **Smart Codec Switching:** The picker lists codecs reported as selectable by Android. Android 15 and newer supply codec names and identities, older versions use the standard Android codec IDs. Unavailable or refused codec access produces a message.
* **Deep LDAC Control:** Override system defaults and manually select your preferred LDAC playback quality (990/909 kbps, 660/606 kbps, 330/303 kbps, or Best Effort adaptive bitrate).
* **Hand Control Back:** The **System Selection (Optimal)** entry explains how to restore automatic selection through Developer options, the app cannot reliably reset the Bluetooth stack's codec priorities.
* **Material Design UI:** A dialog that follows your Material You colours, hosted by an activity launched directly from the tile so permission and device-consent screens can open reliably.
* **Context-Aware:** The tile refreshes while Quick Settings is open and stays tappable during setup, loading, or disconnection. With multiple connected devices, it uses the active A2DP device or explains when the active device cannot be identified.

## Requirements

* Android 13 (API 33) or newer
* A connected A2DP Bluetooth audio device

## Supported languages
* English
* Hungarian

Translations are welcome.

## Screenshots

<p align="center">
  <img src="images/screenshot_1.png" width="250" alt="Quick Settings Tile"> &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="images/screenshot_2.png" width="250" alt="Codec Selection Dialog">
</p>

## How to Use

1. Install the app.
2. Swipe down twice to open your expanded Quick Settings panel.
3. Tap the **Edit** (pencil) icon.
4. Find **Blutilities** in the available tiles and drag it into your active tiles area.
5. Connect your Bluetooth headphones/earbuds.
6. Tap the tile to open the codec picker.
7. The first time you open the picker for a device, Android asks you to let Blutilities manage that device. Approve it - the codec list appears once you do.

## Permissions

| Permission | Why it is needed |
| --- | --- |
| `BLUETOOTH_CONNECT` | Detect the connected device, read its supported codecs and apply your choice. Requested at runtime on first use. |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | Legacy compatibility declarations. |
| `BLUETOOTH_PRIVILEGED` | Declared for system installations, ordinary installations do not receive it and use per-device companion association instead. |

Codec reading and writing use the `@hide` platform APIs `BluetoothA2dp.getCodecStatus` and `setCodecConfigPreference` through reflection, so exact behaviour depends on the ROM's Bluetooth stack. No background-running or background-data exemption is requested. A codec change is reported as applied only after its reported configuration matches the request.

---
*Built with Kotlin.*
