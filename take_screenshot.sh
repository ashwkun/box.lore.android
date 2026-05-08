#!/bin/bash

# Configuration
ADB="/Users/aswinc/Library/Android/sdk/platform-tools/adb"
OUT_DIR="/Users/aswinc/Desktop/boxcast_screenshots"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
FILENAME="screenshot_$TIMESTAMP.png"

# Create output directory if it doesn't exist
mkdir -p "$OUT_DIR"

echo "📸 Capturing screen from Pixel_Working..."

# 1. Take screenshot on device
"$ADB" shell screencap -p /sdcard/screen_temp.png

if [ $? -eq 0 ]; then
    # 2. Pull to Mac
    "$ADB" pull /sdcard/screen_temp.png "$OUT_DIR/$FILENAME"
    
    # 3. Clean up device
    "$ADB" shell rm /sdcard/screen_temp.png
    
    echo "✅ Saved to: $OUT_DIR/$FILENAME"
else
    echo "❌ Failed to capture screenshot. Is the emulator running?"
fi
