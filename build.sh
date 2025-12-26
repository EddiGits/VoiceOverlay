#!/data/data/com.termux/files/usr/bin/bash

set -e

dir=$(pwd)
echo "Building VoiceOverlay..."

# Clean previous build
rm -rf build
mkdir -p build/res build/classes

# Step 1: Compile resources
echo "[1/7] Compiling resources..."
aapt2 compile --dir res -o build/res/compiled.zip

# Step 2: Link resources
echo "[2/7] Linking resources..."
aapt2 link \
  -o build/base.apk \
  -I $dir/toolz/android.jar \
  --manifest AndroidManifest.xml \
  --java build \
  --auto-add-overlay \
  build/res/compiled.zip

# Step 3: Compile Java source
echo "[3/7] Compiling Java..."
$JAVA_HOME/bin/javac --release=8 \
  -d build/classes \
  --class-path $dir/toolz/android.jar \
  src/com/voiceoverlay/MainActivity.java \
  src/com/voiceoverlay/OverlayService.java \
  src/com/voiceoverlay/WhisperAPI.java \
  src/com/voiceoverlay/AudioRecorder.java \
  build/com/voiceoverlay/R.java

# Step 4: Convert to DEX
echo "[4/7] Converting to DEX..."
cd build/classes
dx --dex --output=../../classes.dex com/voiceoverlay/*.class
cd ../..

# Step 5: Package APK
echo "[5/7] Packaging APK..."
cd build
unzip -q base.apk
cd ..
cp classes.dex build/
cd build
# Create APK with resources.arsc uncompressed (-0 for .arsc files)
zip -q -r -0 ../unsigned.apk resources.arsc
zip -q -r ../unsigned.apk . -x "*.zip" "base.apk" "classes/*" "compiled.zip" "resources.arsc"
cd ..

# Step 6: Zipalign
echo "[6/7] Optimizing APK..."
zipalign -f 4 unsigned.apk aligned.apk

# Step 7: Sign APK
echo "[7/7] Signing APK..."
apksigner sign --min-sdk-version 17 --ks ~/.android/debug.keystore --ks-pass pass:android --out final.apk aligned.apk

# Cleanup
rm unsigned.apk aligned.apk

echo "✓ Build complete: final.apk"
ls -lh final.apk
