package studio.sundown.weather;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;

/** Device actions are exposed only to bundled pages on the app's private origin. */
public final class DeviceServices implements LocationListener {
    private static final int LOCATION_REQUEST = 901;
    private final Activity activity;
    private final WebView webView;
    private final LocationManager locations;
    private final SharedPreferences preferences;
    private volatile boolean enabled;
    private volatile String locationState;
    private boolean visible;

    DeviceServices(Activity activity, WebView webView) {
        this.activity = activity; this.webView = webView;
        locations = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        preferences = activity.getSharedPreferences("sundown-device", Context.MODE_PRIVATE);
        enabled = preferences.getBoolean("location_follow", false);
        setState(enabled ? "waiting" : "off", null);
    }
    private boolean permitted() {
        return activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void setState(String status, Location location) {
        try {
            JSONObject result = new JSONObject().put("enabled", enabled).put("status", status);
            if (location != null) result.put("latitude", location.getLatitude()).put("longitude", location.getLongitude()).put("accuracy", location.getAccuracy());
            locationState = result.toString();
            webView.post(() -> webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('sundown-device',{detail:" + locationState + "}))", null));
        } catch (Exception ignored) { locationState = "{\"enabled\":false,\"status\":\"unavailable\"}"; }
    }
    @JavascriptInterface public String getLocationState() { return locationState; }
    @JavascriptInterface public String setLocationEnabled(boolean value) {
        enabled = value;
        preferences.edit().putBoolean("location_follow", value).apply();
        setState(value ? "permission" : "off", null);
        activity.runOnUiThread(() -> {
            if (!enabled) { stop(); return; }
            if (!permitted()) activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            else start();
        });
        return locationState;
    }
    void permissionResult(int requestCode) {
        if (requestCode != LOCATION_REQUEST) return;
        if (!enabled) { setState("off", null); return; }
        if (permitted()) start();
        else { enabled = false; preferences.edit().putBoolean("location_follow", false).apply(); setState("denied", null); }
    }
    private void start() {
        stop();
        if (!enabled || !visible) return;
        if (!permitted()) { enabled = false; preferences.edit().putBoolean("location_follow", false).apply(); setState("denied", null); return; }
        if (locations == null || !locations.isLocationEnabled()) { setState("system_off", null); return; }
        try {
            String provider = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && locations.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (!locations.isProviderEnabled(provider)) { setState("unavailable", null); return; }
            setState("waiting", null);
            locations.requestLocationUpdates(provider, 60000L, 100f, this);
        } catch (SecurityException | IllegalArgumentException exception) { setState("unavailable", null); }
    }
    private void stop() { try { if (locations != null) locations.removeUpdates(this); } catch (SecurityException ignored) { } }
    void resume() { visible = true; if (enabled) start(); }
    void pause() { visible = false; stop(); }
    void destroy() { pause(); }
    @Override public void onLocationChanged(Location location) { if (enabled && visible) setState("fix", location); }
    @Override public void onProviderDisabled(String provider) { if (enabled) setState("system_off", null); }
    @Override public void onProviderEnabled(String provider) { if (enabled && visible) start(); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @JavascriptInterface public String getVersionInfo() {
        try { PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return new JSONObject().put("version", info.versionName).put("versionCode", info.getLongVersionCode()).put("build", "20261022.11").toString();
        } catch (Exception ignored) { return "null"; }
    }
    private File dataCache(String name) { return new File(activity.getCacheDir(), name); }
    private boolean inside(File file, File root) throws IOException {
        return file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator) || file.getCanonicalPath().equals(root.getCanonicalPath());
    }
    private long cacheBytes(File file, File root) throws IOException {
        if (!file.exists() || java.nio.file.Files.isSymbolicLink(file.toPath()) || !inside(file, root)) return 0;
        if (file.isFile()) return file.length();
        long total = 0; File[] children = file.listFiles();
        if (children != null) for (File child : children) total += cacheBytes(child, root);
        return total;
    }
    private void removeCache(File file, File root) throws IOException {
        if (!file.exists() || java.nio.file.Files.isSymbolicLink(file.toPath()) || !inside(file, root)) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) removeCache(child, root);
        if (!file.delete() && file.exists()) throw new IOException("Cache deletion failed");
    }
    @JavascriptInterface public String getCacheInfo() {
        try { long weather = cacheBytes(dataCache("weather"), dataCache("weather")); long maps = cacheBytes(dataCache("maps"), dataCache("maps"));
            return new JSONObject().put("weather", weather).put("maps", maps).put("total", weather + maps).toString();
        } catch (Exception ignored) { return "null"; }
    }
    @JavascriptInterface public String clearDataCache() {
        try { removeCache(dataCache("weather"), dataCache("weather")); removeCache(dataCache("maps"), dataCache("maps")); return getCacheInfo(); }
        catch (Exception ignored) { return "null"; }
    }
    @JavascriptInterface public boolean copyFeedback(String text) {
        if (text == null || text.length() > 5000) return false;
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return false;
        clipboard.setPrimaryClip(ClipData.newPlainText("BARO feedback", text)); return true;
    }
    @JavascriptInterface public boolean openExternal(String url) {
        try { Uri uri = Uri.parse(url); if (!"https".equals(uri.getScheme()) || uri.getHost() == null) return false;
            activity.runOnUiThread(() -> { try { activity.startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { } }); return true;
        } catch (Exception ignored) { return false; }
    }
}
