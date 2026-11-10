# MUSTAKIMZX — Scanner + Receiver

दो ऐप, एक ही Android Studio प्रोजेक्ट में:

| APK | फोन | काम |
|---|---|---|
| `scanner` | फोन 1 | QR / Barcode स्कैन करके Bluetooth से भेजता है |
| `receiver` | फोन 2 | Bluetooth से डेटा लेता है, Chrome / VRL में अपने आप टाइप करता है + Enter दबाता है |

दोनों फोन में एक ही ऐप के दोनों हिस्से अलग-अलग APK के रूप में बनेंगे (एक ही प्रोजेक्ट से)।

---

## 1. APK कैसे बनाएँ

APK बनाने के **तीन तरीके** हैं। जो सुविधा हो वही चुनें।

### तरीका A — सिर्फ फोन से (Termux) ⭐

लैपटॉप की ज़रूरत नहीं। एक बार सेटअप, फिर एक कमांड।

```bash
# Termux (F-Droid या GitHub से — Play Store वाला पुराना है)
termux-setup-storage
pkg update -y
pkg install -y openjdk-17 wget unzip

# aapt2 का ARM वर्ज़न (Google का aapt2 x86_64 होता है, फोन पर नहीं चलता)
curl -s https://raw.githubusercontent.com/rendiix/rendiix.github.io/master/install-repo.sh | bash
pkg install -y android-sdk-build-tools
```

फिर:

```bash
bash /sdcard/values/MUSTAKIMZX/build_on_phone.sh
```

स्क्रिप्ट खुद यह सब करती है:
Android SDK (platform-34) + Gradle 8.7 डाउनलोड → प्रोजेक्ट `$HOME` में कॉपी
(`/sdcard` noexec है) → keystore बनाना → `assembleRelease` → signed APK
`/sdcard/Download/` में कॉपी।

नतीजा:

```
/sdcard/Download/MUSTAKIMZX_scanner.apk    → फोन 1
/sdcard/Download/MUSTAKIMZX_receiver.apk   → फोन 2
```

पहली बार 10–25 मिनट (डाउनलोड + build), उसके बाद ~2–4 मिनट।
पूरा लॉग: `~/mustakimzx/build.log` — कुछ फेल हो तो उसका आखिरी हिस्सा भेजें।

> फोन में कम से कम **3 GB खाली जगह** और अच्छा internet चाहिए।

### तरीका B — GitHub Actions (बिल्कुल फ्री, फोन पर कुछ नहीं) ⭐ सबसे भरोसेमंद

फोन पर build बार-बार फेल हो रहा है तो यही तरीका अपनाएँ। GitHub के सर्वर पर
build होता है (लैपटॉप और Android Studio की ज़रूरत नहीं, बस internet)।

#### B-1. Account पहले से बना है? → सीधे यह करें

Termux खोलें और **बस ये 2 लाइन**:

```bash
pkg install -y git gh
bash /sdcard/values/MUSTAKIMZX/push_to_github.sh
```

स्क्रिप्ट खुद सब करती है:

| Step | क्या होता है |
|---|---|
| 1 | `git` और `gh` इंस्टॉल जाँचती है |
| 2 | प्रोजेक्ट `~/mustakimzx-git` में copy करती है |
| 3 | **login** — screen पर एक code आएगा |
| 4 | git commit बनाती है |
| 5 | GitHub पर `MUSTAKIMZX` repo बनाकर push करती है |
| 6 | Actions workflow चालू करती है |
| 7 | **खुद इंतज़ार करती है** (`.` दिखाते हुए) |
| 8 | सफल होने पर **दोनों APK सीधे `/sdcard/Download/` में रख देती है** |

यानी जब script खत्म हो, File manager → **Download** खोलिए — APK वहीं मिल
जाएँगी। GitHub पर कुछ ढूँढना ही नहीं पड़ेगा।

**Step 3 (login) में सिर्फ यह करना है:**

1. Termux में एक 8 अक्षर का code दिखेगा, जैसे `A1B2-C3D4`.
2. Chrome में `github.com/login/device` खोलें।
3. Code टाइप करें → **Continue** → **Authorize github**।
4. **"Authorize" वाले पेज पर `workflow` permission दिखनी चाहिए** — यह ज़रूरी है,
   इसके बिना `.github/workflows` वाली फाइल push नहीं होती।
5. Termux में वापस आएँ — script खुद आगे बढ़ जाएगी।

> अगर Termux बंद हो जाए या code expire हो जाए — बस वही 1 कमांड दोबारा चलाएँ।
> Script पहले से बने repo को पहचान लेती है और दोबारा push कर देती है।

#### B-2. Account नया-नया बना है → पहले ये 3 चीज़ें (2 मिनट)

Actions चलाने के लिए इतना ज़रूरी है:

1. **Email verify करें** — `github.com/settings/emails` → `Verify` link।
   बिना verify, Actions button ही नहीं दिखेगा।
2. **Username याद रखें** — script उसी से repo का पता बनाती है।
3. **2-Step verification** (optional, पर recommend)।

बस — इसके बाद ऊपर वाली **B-1 की 2 लाइन** चला दें। नया account होने से
कोई फर्क नहीं पड़ता, GitHub Actions हर account के लिए फ्री है।

#### B-3. अगर script के बजाय हाथ से करना हो

1. **repo बनाएँ** — `github.com` → ऊपर दाएँ **+** → **New repository** →
   नाम `MUSTAKIMZX` → **Public** → **Create repository** (README न जोड़ें)।
2. **फाइलें upload** — नए repo में **Add file → Upload files** →
   `/sdcard/values/MUSTAKIMZX/` से सब कुछ चुनें (नीचे तालिका देखें) → **Commit changes**।
3. **चलाएँ** — **Actions** tab → बाएँ **Build APKs** → **Run workflow** → **Run workflow**।
4. **APK लें** — 5–10 मिनट बाद उसी run में नीचे **Artifacts** →
   **MUSTAKIMZX-apks** → zip download → दो APK।

| क्या upload करना है | ज़रूरी? |
|---|---|
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | ✅ |
| `scanner/`, `receiver/`, `shared/` (पूरे फोल्डर) | ✅ |
| `gradle/` पूरा फोल्डर | ✅ |
| `.github/` पूरा फोल्डर (workflow) | ✅ |
| `keystore.jks` + `keystore.properties` | ✅ (signature एक जैसा रहे) |
| `README.md` | अच्छा है |
| `build_on_phone.sh`, `push_to_github.sh` | ज़रूरी नहीं |

⚠️ **`.github` फोल्डर छिपा होता है** — file manager में "hidden files दिखाएँ"
चालू करें, वरना workflow upload ही नहीं होगा और Actions में कुछ नहीं दिखेगा।

⚠️ Browser upload में **एक बार में ~100 फाइलें** ही जाती हैं। ज़्यादा हों तो
`scanner/`, `receiver/`, `shared/` अलग-अलग बार upload करें। Script वाले
तरीके (B-1) में यह दिक्कत नहीं आती।

---

**अगर red ✗ (failure) आए** तो run खोलें → जो step लाल है उस पर क्लिक करें →
आखिरी 20–30 लाइन copy करके मुझे भेजें। असली कारण वहीं दिखता है।

आम कारण:

| Log में क्या दिखे | मतलब / हल |
|---|---|
| `sdkmanager: command not found` | `Set up Android SDK` step हट गया → workflow फाइल दोबारा upload करें |
| `Unresolved reference` | code में error — वह लाइन मुझे भेजें |
| `keystore.properties` / `Keystore was tampered` | `keystore.jks` और `keystore.properties` दोनों upload करें |
| `Artifact not found` / Actions टैब खाली | `.github/workflows/build.yml` upload नहीं हुआ (hidden फोल्डर) |
| `Billing` / `spending limit` | repo Settings → Billing में Actions चालू करें |
| `refusing to allow a Personal Access Token to create or update workflow` | login में `workflow` permission नहीं दी → `gh auth refresh -s workflow` चलाएँ |
| `termux me login par kuch nahi hota` | `Press Enter to open ...` आए तो **ENTER दबाएँ**; code 15 मिनट में expire होता है, दोबारा script चलाएँ |
| `! [rejected] main -> main (fetch first)` | script खुद `--force` से push करती है; फिर भी आए तो repo का नाम बदलकर चलाएँ: `bash push_to_github.sh /sdcard/values/MUSTAKIMZX MUSTAKIMZX2` |


### तरीका C — Android Studio (लैपटॉप/PC)

फोल्डर खोलें →

```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

या टर्मिनल में:

```
./gradlew :scanner:assembleRelease :receiver:assembleRelease
```

फाइलें यहाँ बनेंगी:

```
scanner/build/outputs/apk/release/scanner-release.apk
receiver/build/outputs/apk/release/receiver-release.apk
```

> Android Studio में पहली बार खोलते समय `keystore.properties` नहीं होगा, तब भी build चलेगा
> (APK बस unsigned रहेगा) — install के लिए तरीका A या B आसान है।

### साइनिंग के बारे में (ज़रूरी)

- इस repo में **`keystore.jks` और `keystore.properties` जान-बूझकर** रखे गए हैं।
  इससे Termux और GitHub — दोनों **एक ही signature** से APK बनाते हैं।
  फायदा: नया APK पुराने के ऊपर सीधा install होता है, बार-बार uninstall नहीं करना पड़ता।
- Password: `mustakimzx`, alias: `mustakimzx` (दोनों फाइलों में लिखा है)।
- ⚠️ Repo को public रखना है तो ध्यान दें: कोई भी इस key से APK साइन कर सकता है।
  अपने personal use के लिए यह ठीक है; Play Store पर डालना हो तो नया keystore बनाएँ।
- Keystore खो गया तो भी APK बनेगी (debug), पर पुराने APK के ऊपर install नहीं होगी —
  पहले uninstall करना पड़ेगा।

> Gradle wrapper jar रेपो में नहीं है (डाउनलोड blocked था)। GitHub Actions में हम
> `gradle` सीधे इस्तेमाल करते हैं और Termux स्क्रिप्ट भी Gradle खुद डाउनलोड करती है,
> इसलिए `./gradlew` की ज़रूरत नहीं।

---

## 2. पहली बार सेटअप

### दोनों फोन में (एक बार)

1. **Bluetooth ON** करें।
2. दोनों फोन को एक-दूसरे से **pair** करें (Settings → Bluetooth → Pair)।
   - कोई PIN माँगे तो दोनों फोन पर वही PIN दिखेगा, OK दबाएँ।
3. दोनों फोन पर बैटरी ऑप्टिमाइज़ेशन से ऐप को छूट दें (वरना Android बैकग्राउंड में बंद कर देगा):
   Settings → Apps → MUSTAKIMZX → Battery → **Unrestricted**।

### फोन 2 — MUSTAKIMZX Receiver

1. ऐप खोलें → **Start receiver** दबाएँ।
   - Bluetooth permission माँगे तो Allow करें।
   - Notification permission माँगे तो Allow करें (आवश्यक नहीं, पर तभी ऐप बैकग्राउंड में चलेगा)।
   - स्टेटस दिखेगा: *Waiting for the scanner phone…*
2. **Keyboard चालू करें**:
   - *Enable keyboard* दबाएँ → Settings में **MUSTAKIMZX Keyboard** ON करें → वापस आएँ।
   - *Choose keyboard* दबाएँ → **MUSTAKIMZX Keyboard** चुनें।
   - ऐप में *MUSTAKIMZX keyboard is active ✓* दिखने लगेगा।
3. (वैकल्पिक) **Auto type** चालू करें:
   - *Enable auto type* दबाएँ → Accessibility में **MUSTAKIMZX Auto Type** ON करें।
   - यह सिर्फ सामान्य (native) ऐप्स के टेक्स्ट बॉक्स में काम करता है।
   - **Chrome / VRL के लिए कीबोर्ड ही सही तरीका है** (नीचे देखें)।

### फोन 1 — MUSTAKIMZX Scanner

1. ऐप खोलें → Bluetooth permission Allow करें।
2. **Choose receiver phone** दबाएँ → लिस्ट में फोन 2 चुनें।
   - स्टेटस हरा होकर *Connected to …* दिखेगा।
3. **Start scanning** दबाएँ → कैमरा खुलेगा।

---

## 3. रोज़ का इस्तेमाल (VRL)

1. फोन 2 में Receiver चालू रहने दें (notification दिखेगा)।
2. फोन 2 में **Chrome** खोलें → VRL वेबसाइट → जिस बॉक्स में डेटा जाना है उस पर टैप करें।
   - MUSTAKIMZX कीबोर्ड खुद खुलेगा। ऊपर पट्टी में आखिरी स्कैन दिखेगा।
3. फोन 1 से QR / Barcode स्कैन करें।
4. फोन 2 में:
   - डेटा अपने आप बॉक्स में टाइप होगा,
   - और **Enter** भी अपने आप दबेगा (`Auto Enter: ON`)।

लगातार स्कैनिंग चालू रहती है — फोन 1 में कैमरा खुला रहता है, हर स्कैन तुरंत भेजा जाता है।
एक ही कोड 1.5 सेकंड के अंदर दोबारा नहीं भेजा जाता (गलती से डबल entry न हो)।

### कीबोर्ड की खास चीज़ें

- ऊपर की पट्टी में **आखिरी स्कैन** दिखता है।
- **SEND LAST** — आखिरी स्कैन फिर से टाइप करता है।
- **Auto Enter: ON/OFF** — Enter अपने आप दबे या नहीं।
- **⌨** — दूसरा कीबोर्ड चुनने की सूची खोलता है।
- पूरा QWERTY + numbers + symbols, Shift, Backspace, Space, Enter।

---

## 4. तकनीकी जानकारी

- **Bluetooth**: Classic Bluetooth RFCOMM (SPP जैसा) — `8f7a3c1e-5c5a-4d3e-9f0a-1b2c3d4e5f60`
- **फ्रेम फॉर्मेट**: हर स्कैन एक UTF-8 लाइन + `\n` (नई लाइन = एक स्कैन)
- **Scanner**: CameraX + ML Kit Barcode Scanning (QR, Code128, Code39, EAN, UPC, ITF, PDF417, DataMatrix, Aztec…)
- **Receiver**: Foreground service + wake lock, टूटने पर अपने आप दोबारा listen करता है
- **Auto type**: AccessibilityService (`ACTION_SET_TEXT` + Enter)
- **Keyboard**: `InputMethodService`, स्कैन आते ही `commitText` + Enter key event
- **minSdk 24, targetSdk 34**, Kotlin

### फोल्डर

```
MUSTAKIMZX/
├── shared/              BtProtocol.kt (UUID + फ्रेम फॉर्मेट)
├── scanner/             MainActivity, QrScanActivity, BtClient, Feedback, ScanLog
├── receiver/            MainActivity, ReceiverService, AutoTypeService, ime/MustakimzxIme
├── build_on_phone.sh    Termux में build + sign (तरीका A)
├── .github/workflows/   GitHub Actions से build (तरीका B)
└── README.md            यह गाइड
```

---

## 5. अगर कुछ काम न करे

| दिक्कत | कारण / हल |
|---|---|
| *Not connected* रहता है | दोनों फोन pair हैं? फोन 2 में Receiver **Start** है? दोनों में Location/Bluetooth permission दी है? |
| स्कैन जाता है पर टाइप नहीं होता | फोन 2 में MUSTAKIMZX कीबोर्ड चालू/चुना हुआ है? टेक्स्ट बॉक्स पर टैप किया? |
| Chrome में पुराना कीबोर्ड खुलता है | कीबोर्ड में **⌨** दबाकर MUSTAKIMZX चुनें (Android याद रख लेगा) |
| स्क्रीन बंद होने पर रुक जाता है | Battery → Unrestricted करें; ऐप को "recent apps" से बंद न करें |
| Enter नहीं दबता | कीबोर्ड में **Auto Enter: ON** करें |
| Termux में build फेल: `aapt2 ... not found` / `cannot execute` | ARM वाला aapt2 नहीं है → `pkg install -y android-sdk-build-tools` (rendiix repo, ऊपर step देखें) |
| `attr/123 ... not found` / `failed linking references` | किसी string में `?` या `@` बिना escape के है। `?123` को `\?123` और `@` को `\@` लिखें |
| Termux में `Could not determine java version` | `pkg install -y openjdk-17` करें, फिर `java -version` से 17 दिखना चाहिए |
| Termux में `SDK location not found` | स्क्रिप्ट फिर चलाएँ; वह `local.properties` खुद बनाती है। मैन्युअल: `echo sdk.dir=$HOME/android-sdk > local.properties` |
| Build "out of memory" पर रुके | `gradle.properties` में `org.gradle.jvmargs` का `-Xmx1536m` घटाकर `-Xmx1024m` करें, फोन में जगह खाली करें |
| Build `BUILD FAILED` पर `:scanner:compileReleaseKotlin` के पास रुके | सबसे आम वजह Kotlin daemon की RAM। स्क्रिप्ट खुद दोबारा try करती है; फिर भी न हो तो `-Xmx1024m` करें और बैकग्राउंड ऐप्स बंद करें |
| Build चुपचाप रुक जाए, कोई error न दिखे | अब स्क्रिप्ट `~/mustakimzx/build.log` से असली lines छापती है — वही भेजें |
| `/sdcard` में build fail | Termux प्रोजेक्ट `$HOME` में कॉपी करके बनाता है (`/sdcard` पर exec नहीं होता) — स्क्रिप्ट यह खुद करती है |
