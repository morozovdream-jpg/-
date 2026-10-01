from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "project")

# Keep the framework-only Activity. Android 13+ uses OnBackInvokedDispatcher;
# Android 8-12 use the onBackPressed fallback. This avoids unnecessary AndroidX
# runtime components and permissions in a tiny offline app.
main = root / "app/src/main/java/com/dmitry/wadaru/MainActivity.java"
s = main.read_text()

s = s.replace(
'''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // This WebView only renders trusted bundled files and has no INTERNET permission.
            settings.setSafeBrowsingEnabled(false);
        }
''',
''
)

s = s.replace(
'''    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
''',
'''    @Override
    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
'''
)

main.write_text(s)

# Version bump for the crash-fix release.
gradle = root / "app/build.gradle.kts"
g = gradle.read_text()
g = g.replace("versionCode = 5", "versionCode = 6")
g = g.replace('versionName = "1.5.0"', 'versionName = "1.5.1"')
gradle.write_text(g)

appjs = root / "app/src/main/assets/app.js"
js = appjs.read_text()
js = js.replace("Версия 1.5.0.", "Версия 1.5.1.")
appjs.write_text(js)

release_notes = root / "store-listing/ru-RU/release-notes-1.5.1.txt"
release_notes.write_text(
    "Исправлен критический сбой при запуске версии 1.5.0 на реальном устройстве. "
    "Упрощена стартовая Android-оболочка: удалена ненужная зависимость AndroidX Activity, "
    "сохранена офлайн-работа и совместимость с Android 8+ и Android 16.\n"
)

# No AndroidX is required for this application.
gradle = root / "app/build.gradle.kts"
g = gradle.read_text()
if "dependencies {" in g and "androidx.activity:activity:" in g:
    before, _, tail = g.partition("\n\ndependencies {")
    if "androidx.activity:activity:" in tail:
        g = before.rstrip() + "\n"
gradle.write_text(g)

props = root / "gradle.properties"
p = props.read_text()
p = p.replace("android.useAndroidX=false\n", "")
p = p.replace("android.useAndroidX=true\n", "")
props.write_text(p)

# Clean production manifest: no dangerous/runtime permissions, no INTERNET,
# no hardcoded debuggable flag, no fixed orientation.
manifest = root / "app/src/main/AndroidManifest.xml"
manifest.write_text('''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Intentionally no INTERNET permission and no dangerous/runtime permissions. -->
    <application
        android:allowBackup="false"
        android:appCategory="productivity"
        android:fullBackupContent="@xml/backup_rules"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:hardwareAccelerated="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.WadaRu"
        android:usesCleartextTraffic="false">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:resizeableActivity="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
''')

# API-26-safe base theme. API-specific theme values stay in qualified folders.
themes = root / "app/src/main/res/values/themes.xml"
themes.write_text('''<resources>
    <style name="Theme.WadaRu" parent="android:style/Theme.Material.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowActionModeOverlay">true</item>
        <item name="android:windowBackground">@color/wada_bg</item>
        <item name="android:colorAccent">#A69BBF</item>
        <item name="android:statusBarColor">@color/wada_surface</item>
        <item name="android:navigationBarColor">@color/wada_bg</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
''')

colors = root / "app/src/main/res/values/colors.xml"
c = colors.read_text()
c = c.replace('    <color name="wada_paper">#EEE7D7</color>\n', '')
c = c.replace('    <color name="wada_paper">#E8E0CD</color>\n', '')
colors.write_text(c)

# Since minSdk is 26, adaptive icons do not need a v26 qualifier.
old = root / "app/src/main/res/mipmap-anydpi-v26"
new = root / "app/src/main/res/mipmap-anydpi"
new.mkdir(parents=True, exist_ok=True)
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    src = old / name
    if src.exists():
        shutil.copy2(src, new / name)
if old.exists():
    shutil.rmtree(old)

# Disable app-data cloud backup and device-transfer migration explicitly.
xml = root / "app/src/main/res/xml"
xml.mkdir(parents=True, exist_ok=True)
(xml / "backup_rules.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="root" path="." />
    <exclude domain="file" path="." />
    <exclude domain="database" path="." />
    <exclude domain="sharedpref" path="." />
    <exclude domain="external" path="." />
</full-backup-content>
''')
(xml / "data_extraction_rules.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </device-transfer>
</data-extraction-rules>
''')

# Fail early if an accidental AndroidX dependency or obsolete WebView branch appears.
final_main = main.read_text()
assert "ComponentActivity" not in final_main
assert "OnBackPressedCallback" not in final_main
assert "OnBackInvokedDispatcher" in final_main
assert '@SuppressLint("GestureBackNavigation")' in final_main
assert "setSafeBrowsingEnabled" not in final_main
assert "androidx.activity" not in gradle.read_text()

print("Production source patch applied: framework-only Activity")
