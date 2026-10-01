from pathlib import Path
import shutil
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "project")
java = root / "app/src/main/java/com/dmitry/wadaru"
java.mkdir(parents=True, exist_ok=True)

# Crash-resilient launcher. The main Activity deliberately contains no direct
# references to API-29/30/33-only classes, so older/vendor Android runtimes can
# verify and load it safely.
(java / "MainActivity.java").write_text(r'''package com.dmitry.wadaru;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class MainActivity extends Activity {
    private static final String START_URL = "file:///android_asset/index.html";
    private WebView webView;
    private Object backToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            WebView.setWebContentsDebuggingEnabled(false);
            createWebView(savedInstanceState);
            configureWindowBestEffort();
            registerBackHandler();
        } catch (Throwable error) {
            showStartupError(error);
        }
    }

    @SuppressWarnings("deprecation")
    private void configureWindowBestEffort() {
        try {
            getWindow().setStatusBarColor(Color.rgb(24, 23, 19));
            getWindow().setNavigationBarColor(Color.rgb(17, 17, 15));
            View decor = getWindow().getDecorView();
            if (decor != null) {
                decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                );
            }
        } catch (Throwable ignored) {
            // System-bar styling must never prevent the app from starting.
        }
    }

    private void createWebView(Bundle state) {
        WebView view = new WebView(this);
        webView = view;
        configureWebView(view);
        setContentView(view);
        if (state == null || view.restoreState(state) == null) {
            view.loadUrl(START_URL);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView view) {
        view.setBackgroundColor(Color.rgb(17, 17, 15));
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setHorizontalScrollBarEnabled(false);
        view.setVerticalScrollBarEnabled(false);

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setBlockNetworkLoads(true);

        view.setWebViewClient(new WebViewClient() {
            private boolean isAllowed(Uri uri) {
                if (uri == null) return false;
                String scheme = uri.getScheme();
                if ("about".equals(scheme) || "data".equals(scheme)) return true;
                if (!"file".equals(scheme)) return false;
                String path = uri.getPath();
                return path != null && path.startsWith("/android_asset/");
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return !isAllowed(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return !isAllowed(Uri.parse(url));
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                if (!isAllowed(request.getUrl())) {
                    return new WebResourceResponse(
                        "text/plain", "UTF-8", new ByteArrayInputStream(new byte[0])
                    );
                }
                return super.shouldInterceptRequest(v, request);
            }
        });
    }

    private void registerBackHandler() {
        if (Build.VERSION.SDK_INT >= 33) {
            backToken = Api33Back.register(this, this::requestAppBack);
        }
    }

    private void requestAppBack() {
        WebView view = webView;
        if (view == null) {
            finish();
            return;
        }
        view.evaluateJavascript(
            "(window.__wadaruBack ? window.__wadaruBack() : false)",
            result -> {
                if (!"true".equals(result)) finish();
            }
        );
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT >= 33) {
            super.onBackPressed();
        } else {
            requestAppBack();
        }
    }

    @SuppressLint("SetTextI18n")
    private void showStartupError(Throwable error) {
        try {
            WebView old = webView;
            webView = null;
            if (old != null) {
                try { old.destroy(); } catch (Throwable ignored) {}
            }

            StringWriter buffer = new StringWriter();
            error.printStackTrace(new PrintWriter(buffer));
            String trace = buffer.toString();
            if (trace.length() > 7000) trace = trace.substring(0, 7000);

            TextView text = new TextView(this);
            int pad = (int) (20 * getResources().getDisplayMetrics().density);
            text.setPadding(pad, pad, pad, pad);
            text.setTextColor(Color.WHITE);
            text.setBackgroundColor(Color.rgb(24, 23, 19));
            text.setTextSize(14);
            text.setTextIsSelectable(true);
            text.setText(
                "Цвета Вада — ошибка запуска\n\n" +
                "Версия: 1.5.6 (11)\n" +
                "Устройство: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "Android API: " + Build.VERSION.SDK_INT + "\n\n" +
                error.getClass().getName() + ": " + String.valueOf(error.getMessage()) +
                "\n\n" + trace +
                "\n\nСделайте скриншот этого экрана и отправьте разработчику."
            );
            setContentView(text);
        } catch (Throwable fatal) {
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        WebView view = webView;
        if (view != null) {
            try { view.saveState(outState); } catch (Throwable ignored) {}
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backToken != null) {
            try { Api33Back.unregister(this, backToken); } catch (Throwable ignored) {}
            backToken = null;
        }
        WebView view = webView;
        webView = null;
        if (view != null) {
            try {
                view.stopLoading();
                view.setWebViewClient(null);
                view.loadUrl("about:blank");
                view.clearHistory();
                view.removeAllViews();
                view.destroy();
            } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
''')

(java / "Api33Back.java").write_text(r'''package com.dmitry.wadaru;

import android.annotation.TargetApi;
import android.app.Activity;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

@TargetApi(33)
final class Api33Back {
    private Api33Back() {}

    static Object register(Activity activity, Runnable action) {
        OnBackInvokedCallback callback = action::run;
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        );
        return callback;
    }

    static void unregister(Activity activity, Object token) {
        if (token instanceof OnBackInvokedCallback) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                (OnBackInvokedCallback) token
            );
        }
    }
}
''')

# Deterministic version bump for the 1.5.6 release.
gradle = root / "app/build.gradle.kts"
g = gradle.read_text()
g, n_code = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 11', g, count=1)
g, n_name = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.5.6"', g, count=1)
if n_code != 1 or n_name != 1:
    raise RuntimeError(f"Version bump failed: versionCode={n_code}, versionName={n_name}")
# Keep the app framework-only; remove accidental AndroidX dependency blocks.
if "dependencies {" in g and "androidx.activity:activity:" in g:
    before, _, tail = g.partition("\n\ndependencies {")
    if "androidx.activity:activity:" in tail:
        g = before.rstrip() + "\n"
gradle.write_text(g)

props = root / "gradle.properties"
p = props.read_text().replace("android.useAndroidX=false\n", "").replace("android.useAndroidX=true\n", "")
props.write_text(p)

# Keep API-gated helper classes physically separate in release DEX. Without
# these rules R8 may inline them back into MainActivity and defeat old-API
# class-verification safety.
proguard = root / "app/proguard-rules.pro"
proguard.write_text("""# Release entry point and API-gated compatibility islands.
-keep public class com.dmitry.wadaru.MainActivity { public <init>(); }
-keep class com.dmitry.wadaru.Api33Back { *; }
""")

appjs = root / "app/src/main/assets/app.js"

for ux_name in ("ux154.py", "ux155.py", "ux156.py"):
    ux_patch = Path(__file__).resolve().parent / ux_name
    exec(
        compile(ux_patch.read_text(), str(ux_patch), "exec"),
        {"root": root, "__builtins__": __builtins__},
    )

# Normalize the visible version after all UX migrations.
js = appjs.read_text()
js = re.sub(r'Версия 1\.5\.\d+\.', 'Версия 1.5.6.', js)
appjs.write_text(js)

# Keep privacy wording in-app and in store/source documents aligned with
# the actual local-only state: favorites + selected theme.
js = appjs.read_text()
js = js.replace(
    "Избранное хранится только в локальном хранилище приложения на устройстве. Удаление приложения удаляет эти локальные данные.",
    "Избранное и выбранная тема интерфейса хранятся только в локальном хранилище приложения на устройстве. Эти данные не синхронизируются и не передаются третьим лицам. Удаление приложения удаляет эти локальные данные."
)
appjs.write_text(js)

privacy_md = root / "docs/PRIVACY_POLICY_RU.md"
pm = privacy_md.read_text()
pm = pm.replace(
    "Отмеченные пользователем избранные сочетания хранятся только в локальном хранилище приложения на устройстве. Эти данные не синхронизируются с сервером и не передаются третьим лицам. При удалении приложения локальные данные удаляются средствами операционной системы.",
    "Отмеченные пользователем избранные сочетания и выбранная тема интерфейса хранятся только в локальном хранилище приложения на устройстве. Эти данные не синхронизируются с сервером и не передаются третьим лицам. При удалении приложения локальные данные удаляются средствами операционной системы."
)
privacy_md.write_text(pm)

privacy_html = root / "docs/PRIVACY_POLICY_RU.html"
ph = privacy_html.read_text()
ph = ph.replace(
    "Избранные сочетания хранятся только локально на устройстве, не синхронизируются и не передаются третьим лицам. При удалении приложения локальные данные удаляются средствами операционной системы.",
    "Избранные сочетания и выбранная тема интерфейса хранятся только локально на устройстве, не синхронизируются и не передаются третьим лицам. При удалении приложения локальные данные удаляются средствами операционной системы."
)
privacy_html.write_text(ph)

data_safety = root / "store-listing/data-safety-ru.md"
ds = data_safety.read_text()
ds = ds.replace(
    "- Локальное хранение: только список избранного в WebView LocalStorage.",
    "- Локальное хранение: только список избранного и выбранная тема интерфейса в WebView LocalStorage."
)
data_safety.write_text(ds)

assert "Избранное и выбранная тема интерфейса хранятся только" in appjs.read_text()
assert "избранные сочетания и выбранная тема интерфейса" in privacy_md.read_text()
assert "Избранные сочетания и выбранная тема интерфейса" in privacy_html.read_text()
assert "список избранного и выбранная тема интерфейса" in data_safety.read_text()

release_notes = root / "store-listing/ru-RU/release-notes-1.5.6.txt"
release_notes.write_text(
    "Возвращён фирменный художественный дизайн при точном соответствии цветов палитр. "
    "Главная снова вынесена в нижнюю навигацию, фильтр расположен под поиском, верхняя часть "
    "стала компактнее, а фон и обложки получили более выразительную композицию.\n"
)

manifest = root / "app/src/main/AndroidManifest.xml"
manifest.write_text('''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
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
c = colors.read_text().replace('    <color name="wada_paper">#EEE7D7</color>\n', '').replace('    <color name="wada_paper">#E8E0CD</color>\n', '')
colors.write_text(c)

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

# Hard assertions for the compatibility architecture.
main_text = (java / "MainActivity.java").read_text()
assert "android.window." not in main_text
assert "WindowInsetsController" not in main_text
assert "Api30Window" not in main_text
assert "Api29Window" not in main_text
assert "ComponentActivity" not in main_text
assert "OnBackPressedCallback" not in main_text
assert "showStartupError" in main_text
assert "androidx.activity" not in gradle.read_text()

print("Production source patch applied: 1.5.6 restored Wada identity + compact home")
