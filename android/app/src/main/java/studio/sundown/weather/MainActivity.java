package studio.sundown.weather;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.IOException;

/** Offline weather design preview with optional foreground device location. */
public final class MainActivity extends Activity {
    private WebView webView;
    private DeviceServices deviceServices;
    private static final String ORIGIN = "https://sundown.local/";

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(252, 252, 248));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(100);
        settings.setSupportZoom(false);
        settings.setUseWideViewPort(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                view.post(() -> applyImmersive());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith(ORIGIN)) return new WebResourceResponse("text/plain", "UTF-8", null);
                String path = request.getUrl().getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                if (path.contains("..")) return new WebResourceResponse("text/plain", "UTF-8", null);
                String type = path.endsWith(".html") ? "text/html" : path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "text/javascript" : path.endsWith(".svg") ? "image/svg+xml" : path.endsWith(".ttf") ? "font/ttf" : path.endsWith(".woff2") ? "font/woff2" : "application/octet-stream";
                try { return new WebResourceResponse(type, "UTF-8", getAssets().open(path.substring(1))); }
                catch (IOException error) { return new WebResourceResponse("text/plain", "UTF-8", null); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith(ORIGIN);
            }
        });
        deviceServices = new DeviceServices(this, webView);
        webView.addJavascriptInterface(deviceServices, "SundownDevice");
        setContentView(webView);
        applyImmersive();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::closeSettingsOrFinish);
        }
        webView.loadUrl(ORIGIN);
    }

    @SuppressWarnings("deprecation")
    private void applyImmersive() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override protected void onResume() {
        super.onResume();
        if (deviceServices != null) deviceServices.resume();
        if (webView != null) { webView.onResume(); webView.post(() -> applyImmersive()); }
    }

    @Override protected void onPause() {
        if (deviceServices != null) deviceServices.pause();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    private void closeSettingsOrFinish() {
        webView.evaluateJavascript("(() => { const settings = document.getElementById('settings-panel'); const scenes = document.getElementById('scenes-panel'); if (!scenes.hidden) { document.getElementById('scenes-close').click(); return true; } if (settings.hidden) return false; const back = document.getElementById('settings-back'); document.getElementById(back.hidden ? 'settings-close' : 'settings-back').click(); return true; })()", handled -> {
            if (!"true".equals(handled)) finish();
        });
    }

    // API 29–32 fallback only; API 33+ uses the OnBackInvokedDispatcher above.
    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { closeSettingsOrFinish(); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (deviceServices != null) deviceServices.permissionResult(requestCode);
    }

    @Override protected void onDestroy() {
        if (deviceServices != null) deviceServices.destroy();
        webView.destroy();
        super.onDestroy();
    }
}
