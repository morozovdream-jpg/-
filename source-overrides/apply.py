from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "project")

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

manifest = root / "app/src/main/AndroidManifest.xml"
s = manifest.read_text()
s = s.replace(
'<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
'<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:tools="http://schemas.android.com/tools">'
)
s = s.replace('        android:debuggable="false"\n', '')
s = s.replace('        android:extractNativeLibs="false"\n', '')
s = s.replace(
'        android:fullBackupContent="false"\n',
'        android:fullBackupContent="@xml/backup_rules"\n        android:dataExtractionRules="@xml/data_extraction_rules"\n'
)
s = s.replace(
'            android:enableOnBackInvokedCallback="true"\n',
'            android:enableOnBackInvokedCallback="true"\n            tools:targetApi="33"\n'
)
s = s.replace(
'            android:resizeableActivity="true"\n            android:screenOrientation="unspecified">',
'            android:resizeableActivity="true">'
)
manifest.write_text(s)

themes = root / "app/src/main/res/values/themes.xml"
themes.write_text(themes.read_text().replace(
'        <item name="android:windowLightNavigationBar">false</item>\n', ''
))

colors = root / "app/src/main/res/values/colors.xml"
colors.write_text(colors.read_text().replace(
'    <color name="wada_paper">#EEE7D7</color>\n', ''
))

props = root / "gradle.properties"
props.write_text(props.read_text().replace('android.useAndroidX=false\n', ''))

old = root / "app/src/main/res/mipmap-anydpi-v26"
new = root / "app/src/main/res/mipmap-anydpi"
new.mkdir(parents=True, exist_ok=True)
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    src = old / name
    if src.exists():
        shutil.copy2(src, new / name)
if old.exists():
    shutil.rmtree(old)

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

print("Production source patch applied")
