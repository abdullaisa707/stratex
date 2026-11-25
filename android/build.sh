#!/bin/bash
# Builds STRATEX.apk. Needs (Ubuntu): android-sdk-platform-23 aapt apksigner zipalign dalvik-exchange
# Usage: KEYSTORE=path KS_PASS=pass ./build.sh
set -e
cd "$(dirname "$0")"
AJ=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf build && mkdir -p build/gen build/classes build/apk
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I $AJ
javac -nowarn -source 8 -target 8 -bootclasspath $AJ -d build/classes src/com/daftari/notes/*.java build/gen/com/daftari/notes/R.java
dalvik-exchange --dex --output=build/apk/classes.dex build/classes
aapt package -f -M AndroidManifest.xml -S res -A assets -I $AJ -F build/unsigned.apk
(cd build/apk && aapt add ../unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks "$KEYSTORE" --ks-pass pass:"$KS_PASS" --out build/STRATEX.apk build/aligned.apk
echo "Built android/build/STRATEX.apk"
