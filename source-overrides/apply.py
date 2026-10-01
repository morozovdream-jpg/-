from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "project")

# Android 16 / predictive-back compliant Activity.
main = root / "app/src/main/java/com/dmitry/wadaru/MainActivity.java"
s = main.read_text()
s = s.replace("import android.app.Activity;\n", "")
s = s.replace(
    "import android.window.OnBackInvokedCallback;\n"
    "import android.window.OnBackInvokedDispatcher;\n",
    ""
)
if "import androidx.activity.ComponentActivity;" not in s:
    s = s.replace(
        "import java.io.ByteArrayInputStream;\n\n",
        "import java.io.ByteArrayInputStream;\n\n"
        "import androidx.activity.ComponentActivity;\n"
        "import androidx.activity.OnBackPressedCallback;\n\n"
    )
s = s.replace(
    "public final class MainActivity extends Activity {",
    "public final class MainActivity extends ComponentActivity {"
)
s = s.replace("    private OnBackInvokedCallback backCallback;\n", "")
s = s.replace(
'''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // This WebView only renders trusted bundled files and has no INTERNET permission.
            settings.setSafeBrowsingEnabled(false);
        }
''',
''
)
s = s.replace(
'''    private void registerBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = this::requestAppBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backCallback
            );
        }
    }
''',
'''    private void registerBackHandler() {
        getOnBackPressedDispatcher().addCallback(
            this,
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    requestAppBack();
                }
            }
        );
    }
'''
)
s = s.replace(
'''    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.onBackPressed();
        } else {
            requestAppBack();
        }
    }

''',
''
)
s = s.replace(
'''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
''',
''
)
main.write_text(s)

# Release configuration and AndroidX dependency.
gradle = root / "app/build.gradle.kts"
g = gradle.read_text()
if 'androidx.activity:activity:' not in g:
    g += '\n\ndependencies {\n    implementation("androidx.activity:activity:1.11.0")\n}\n'
gradle.write_text(g)

props = root / "gradle.properties"
p = props.read_text()
p = p.replace("android.useAndroidX=false\n", "")
if "android.useAndroidX=true" not in p:
    p += "\nandroid.useAndroidX=true\n"
props.write_text(p)

# Clean production manifest: no hardcoded debuggable, no unnecessary native-lib
# extraction flag, no fixed orientation, no obsolete back opt-in.
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

# API-26-safe base theme. API-31-specific settings remain in values-v31.
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

# Disable app-data backup and device-transfer migration explicitly.
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

# Fail early if any legacy back implementation survived the patch.
final_main = main.read_text()
assert "onBackPressed()" not in final_main
assert "OnBackInvoked" not in final_main
assert "ComponentActivity" in final_main
assert "OnBackPressedCallback" in final_main

print("Production source patch applied")
