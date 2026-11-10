#!/data/data/com.termux/files/usr/bin/bash
#
# MUSTAKIMZX - phone par hi APK build + sign (Termux ke liye)
#
# ================= SETUP (ek hi baar) =================
# 1) Termux kholein (F-Droid ya GitHub se - Play Store wala purana hai)
# 2) termux-setup-storage          -> permission dialog me Allow dabayein
# 3) pkg update -y
# 4) pkg install -y openjdk-17 wget unzip
# 5) aapt2 native chahiye (Google ka aapt2 x86_64 hota hai, phone ARM hai):
#      curl -s https://raw.githubusercontent.com/rendiix/rendiix.github.io/master/install-repo.sh | bash
#      pkg install -y android-sdk-build-tools
#
# ================= CHALAYEIN =================
#   bash /sdcard/values/MUSTAKIMZX/build_on_phone.sh
#   (project kahin aur ho to: bash build_on_phone.sh /path/to/MUSTAKIMZX)
#
# Output:
#   /sdcard/Download/MUSTAKIMZX_scanner.apk
#   /sdcard/Download/MUSTAKIMZX_receiver.apk
#
set -e

SRC="${1:-/sdcard/values/MUSTAKIMZX}"
BASE="$HOME/mustakimzx"
WORK="$BASE/MUSTAKIMZX"
GRADLE_VER="8.7"
GRADLE_HOME="$BASE/gradle-$GRADLE_VER"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
SDK="$HOME/android-sdk"
KS="$BASE/mustakimzx.jks"
KS_PASS="mustakimzx"
KS_ALIAS="mustakimzx"
OUTDIR="/sdcard/Download"

say() { echo ""; echo "==> $*"; }
die() { echo ""; echo "[!] $*"; exit 1; }

mkdir -p "$BASE"

say "Step 1: tools check"
missing=""
for t in java wget unzip keytool; do
    command -v "$t" >/dev/null 2>&1 || missing="$missing $t"
done
if [ -n "$missing" ]; then
    die "Ye tools nahi mile:$missing
    Chalayein:  pkg install -y openjdk-17 wget unzip"
fi
java -version 2>&1 | head -1

AAPT2=""
for c in "$PREFIX/bin/aapt2" "$(command -v aapt2 2>/dev/null || true)"; do
    if [ -n "$c" ] && [ -x "$c" ]; then AAPT2="$c"; break; fi
done
[ -n "$AAPT2" ] || die "aapt2 nahi mila.
    Chalayein:
      curl -s https://raw.githubusercontent.com/rendiix/rendiix.github.io/master/install-repo.sh | bash
      pkg install -y android-sdk-build-tools"
echo "aapt2: $AAPT2"
if command -v file >/dev/null 2>&1; then file "$AAPT2" | head -1; fi

# AGP default me x86_64 aapt2 download karta hai - use phone ke ARM aapt2 par override karein.
mkdir -p "$HOME/.gradle"
touch "$HOME/.gradle/gradle.properties"
if grep -q "aapt2FromMavenOverride" "$HOME/.gradle/gradle.properties"; then
    sed -i.bak "s|^android.aapt2FromMavenOverride=.*|android.aapt2FromMavenOverride=$AAPT2|" \
        "$HOME/.gradle/gradle.properties"
    rm -f "$HOME/.gradle/gradle.properties.bak"
else
    echo "android.aapt2FromMavenOverride=$AAPT2" >> "$HOME/.gradle/gradle.properties"
fi
echo "aapt2 override likh diya: ~/.gradle/gradle.properties"

[ -d "$SRC" ] || die "Project nahi mila: $SRC"

say "Step 2: Android SDK (platform 34)"
if [ ! -f "$SDK/platforms/android-34/android.jar" ]; then
    SDKMGR=""
    for c in "$SDK/cmdline-tools/latest/bin/sdkmanager" "$SDK/cmdline-tools/bin/sdkmanager"; do
        [ -x "$c" ] && { SDKMGR="$c"; break; }
    done
    if [ -z "$SDKMGR" ]; then
        echo "cmdline-tools download ho rahe hain (~130 MB)..."
        mkdir -p "$SDK"
        wget -q -O "$BASE/cmdtools.zip" \
            "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
            || die "cmdline-tools download fail. Internet check karein."
        unzip -q -o "$BASE/cmdtools.zip" -d "$SDK"
        mkdir -p "$SDK/cmdline-tools/latest"
        if [ -d "$SDK/cmdline-tools/bin" ]; then
            mv "$SDK/cmdline-tools/bin" "$SDK/cmdline-tools/lib" \
               "$SDK/cmdline-tools/NOTICE.txt" "$SDK/cmdline-tools/source.properties" \
               "$SDK/cmdline-tools/latest/"
        fi
        SDKMGR="$SDK/cmdline-tools/latest/bin/sdkmanager"
    fi
    [ -x "$SDKMGR" ] || die "sdkmanager nahi mila ($SDKMGR)"
    export ANDROID_HOME="$SDK"
    export ANDROID_SDK_ROOT="$SDK"
    echo "platform-34 install ho raha hai..."
    yes | "$SDKMGR" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
    "$SDKMGR" --sdk_root="$SDK" "platforms;android-34" \
        || die "sdkmanager fail hua. 'ANDROID_HOME=$SDK' ke saath dobara chalayein."
    # build-tools optional hai (d8/zip-align AGP ke andar aate hain); fail ho to ignore.
    "$SDKMGR" --sdk_root="$SDK" "build-tools;34.0.0" || echo "(build-tools skip - zaroori nahi)"
fi
[ -f "$SDK/platforms/android-34/android.jar" ] || die "android-34 platform nahi mila"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
echo "SDK ready: $SDK"

say "Step 3: project copy ($HOME me, /sdcard noexec hai)"
rm -rf "$WORK"
mkdir -p "$BASE"
cp -r "$SRC" "$WORK"
rm -rf "$WORK/build" "$WORK/scanner/build" "$WORK/receiver/build" "$WORK/shared/build"
echo "sdk.dir=$SDK" > "$WORK/local.properties"

say "Step 4: Gradle $GRADLE_VER"
if [ ! -x "$GRADLE_BIN" ]; then
    echo "Gradle download ho raha hai (~130 MB)..."
    wget -q -O "$BASE/gradle.zip" \
        "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip" \
        || die "Gradle download fail. Internet check karein."
    unzip -q -o "$BASE/gradle.zip" -d "$BASE"
    rm -f "$BASE/gradle.zip"
fi
[ -x "$GRADLE_BIN" ] || die "Gradle nahi mila: $GRADLE_BIN"
"$GRADLE_BIN" --version | grep -E "^Gradle " || true

say "Step 5: keystore (signing)"
# Project ke saath aayi keystore ko hi upar rakhein - warna Termux aur GitHub
# alag-alag key se sign karenge aur update install nahi hoga.
if [ -f "$WORK/keystore.jks" ] && [ -f "$WORK/keystore.properties" ]; then
    echo "project ki keystore use ho rahi hai (signature GitHub jaisa hi rahega)"
else
    if [ ! -f "$KS" ]; then
        keytool -genkeypair -v -keystore "$KS" -alias "$KS_ALIAS" \
            -keyalg RSA -keysize 2048 -validity 10000 \
            -storepass "$KS_PASS" -keypass "$KS_PASS" \
            -dname "CN=MUSTAKIMZX, O=MUSTAKIMZX, C=IN" >/dev/null
        echo "naya keystore: $KS"
    else
        echo "purana keystore use ho raha hai: $KS"
    fi
    cp "$KS" "$WORK/keystore.jks"
    cat > "$WORK/keystore.properties" <<EOF
storeFile=keystore.jks
storePassword=$KS_PASS
keyAlias=$KS_ALIAS
keyPassword=$KS_PASS
EOF
    echo "keystore.properties banaya"
fi

say "Step 6: build (pehli baar 10-25 min lag sakte hain)"
cd "$WORK"

# Gradle ko bina pipe ke chalate hain: pipe me exit code kho jaata hai aur
# build fail hone par bhi script aage badh jaati thi.
run_build() {
    set +e
    "$GRADLE_BIN" \
        -Pandroid.aapt2FromMavenOverride="$AAPT2" \
        :scanner:assembleRelease :receiver:assembleRelease \
        > "$BASE/build.log" 2>&1
    RC=$?
    set -e
}

run_build

# Termux par sabse aam failure: Kotlin daemon RAM ki kami (alag JVM nahi ban
# paata). Aise log me daemon ka zikr hota hai - us haalat me ek baar memory
# kam karke, in-process compiler ke saath dobara try karte hain.
if [ "$RC" -ne 0 ] && grep -qiE "kotlin daemon|daemon (could not|failed|terminated)|Cannot allocate memory|OutOfMemoryError" "$BASE/build.log"; then
    echo ""
    echo "Kotlin daemon ki memory problem mili - dobara try kar rahe hain..."
    echo "(in-process compiler, kam memory)"
    export GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=384m"
    rm -rf "$WORK/.gradle" "$WORK/scanner/build/kotlin"
    set +e
    "$GRADLE_BIN" -Dkotlin.compiler.execution.strategy=in-process \
        -Pandroid.aapt2FromMavenOverride="$AAPT2" \
        --stacktrace \
        :scanner:assembleRelease :receiver:assembleRelease \
        >> "$BASE/build.log" 2>&1
    RC=$?
    set -e
fi

echo "--- aakhri 40 line ---"
tail -40 "$BASE/build.log"
echo ""
echo "poora log: $BASE/build.log"

if [ "$RC" -ne 0 ] || ! grep -q "BUILD SUCCESSFUL" "$BASE/build.log"; then
    echo ""
    echo "======================================================"
    echo " BUILD FAIL (exit $RC)"
    echo ""
    # Kotlin errors: "e: file:///path/File.kt:123:45 Unresolved reference: xyz"
    KT_ERR=$(grep -cE "^e: file:" "$BASE/build.log" 2>/dev/null)
    KT_ERR=${KT_ERR:-0}
    if [ "$KT_ERR" -gt 0 ] 2>/dev/null; then
        echo " Kotlin errors ($KT_ERR) - file aur line ke saath:"
        grep -E "^e: file:" "$BASE/build.log" | sed 's|file://.*/MUSTAKIMZX/||' | head -20
        echo ""
    fi
    echo " Baaki zaroori lines:"
    grep -nE "error:|FAILURE:|What went wrong|Caused by|Could not (connect|resolve|find)|OutOfMemory|Unresolved reference|Execution failed for task" \
        "$BASE/build.log" | head -20 || true
    echo ""
    echo " Aakhri 15 line:"
    tail -15 "$BASE/build.log"
    echo "======================================================"
    die "Build fail hua. Upar ki lines copy karke bhejein (ya $BASE/build.log)."
fi

say "Step 7: APK check"
mkdir -p "$OUTDIR"
found=0
for apk in "$WORK/scanner/build/outputs/apk/release/"*.apk \
           "$WORK/receiver/build/outputs/apk/release/"*.apk; do
    [ -f "$apk" ] || continue
    case "$apk" in
        *scanner*) name="MUSTAKIMZX_scanner.apk" ;;
        *receiver*) name="MUSTAKIMZX_receiver.apk" ;;
        *) name="MUSTAKIMZX_$(basename "$apk")" ;;
    esac
    cp "$apk" "$OUTDIR/$name"
    size=$(du -h "$apk" 2>/dev/null | cut -f1)
    echo "OK: $OUTDIR/$name  (${size:-?})"
    found=$((found + 1))
done
[ "$found" -ge 2 ] || die "APK nahi mile - build ke output folder check karein."

say "Step 8: signature verify"
if command -v apksigner >/dev/null 2>&1; then
    for f in "$OUTDIR/MUSTAKIMZX_scanner.apk" "$OUTDIR/MUSTAKIMZX_receiver.apk"; do
        apksigner verify --verbose "$f" >/dev/null 2>&1 \
            && echo "OK signed: $(basename "$f")" \
            || echo "[!] verify nahi hua (purane apksigner ka farq ho sakta hai): $(basename "$f")"
    done
else
    echo "(apksigner nahi hai, verify skip - pkg install apksigner)"
fi

echo ""
echo "======================================================"
echo " TAYYAR - dono APK ban gaye"
echo "   $OUTDIR/MUSTAKIMZX_scanner.apk   -> PHONE 1 (scanner)"
echo "   $OUTDIR/MUSTAKIMZX_receiver.apk  -> PHONE 2 (receiver)"
echo ""
echo " Dono phones ke Download folder me copy karke Install karein."
echo " Source code aur keystore: $WORK"
echo " (keystore kho na dein: $KS - warna update install nahi hoga)"
echo "======================================================"
