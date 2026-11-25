package com.daftari.notes;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import java.util.ArrayList;
import org.json.JSONObject;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.OutputStream;

public class MainActivity extends Activity {
    // The app UI lives here; updating this site updates the app.
    static final String APP_URL = "https://abdullaisa707.github.io/stratex/";
    private static final int REQ_PICK = 1, REQ_SAVE = 2, REQ_SPEECH = 3;
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private String pendingJson;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("stratex", MODE_PRIVATE);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0c0c0e"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                Uri u = Uri.parse(url);
                if (url.startsWith(APP_URL) || "file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) v.loadUrl("file:///android_asset/offline.html");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(i, REQ_PICK); } catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        if (!prefs.getBoolean("migrationRead", false)) web.loadUrl("file:///android_asset/migrate.html");
        else web.loadUrl(APP_URL);
    }

    class Bridge {
        @JavascriptInterface public void migrated(String json) {
            prefs.edit().putBoolean("migrationRead", true).putString("oldNotes", json).apply();
            runOnUiThread(new Runnable() { public void run() { web.loadUrl(APP_URL); }});
        }
        @JavascriptInterface public String takeOldNotes() {
            String j = prefs.getString("oldNotes", "");
            prefs.edit().remove("oldNotes").apply();
            return j;
        }
        @JavascriptInterface public void retry() {
            runOnUiThread(new Runnable() { public void run() { web.loadUrl(APP_URL); }});
        }
        @JavascriptInterface public void listen(final String lang) {
            runOnUiThread(new Runnable() { public void run() {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلم...");
                try { startActivityForResult(i, REQ_SPEECH); } catch (Exception e) { js("onSpeech(null,'unavailable')"); }
            }});
        }
        @JavascriptInterface public void setReminders(String json) {
            ReminderReceiver.schedule(MainActivity.this, json);
        }
        @JavascriptInterface public void askNotifications() {
            runOnUiThread(new Runnable() { public void run() {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0)
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 9);
            }});
        }
        @JavascriptInterface public void testNotification() {
            ReminderReceiver.show(MainActivity.this, 1, "STRATEX 🔔", "التذكيرات شغالة ✅");
        }
        @JavascriptInterface public void saveFile(final String name, final String json) {
            runOnUiThread(new Runnable() { public void run() {
                pendingJson = json;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, name);
                try { startActivityForResult(i, REQ_SAVE); } catch (Exception e) { js("onAndroidSaved(false)"); }
            }});
        }
    }

    private void js(String code) { web.evaluateJavascript(code, null); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        Uri uri = (res == RESULT_OK && data != null) ? data.getData() : null;
        if (req == REQ_SPEECH) {
            String text = null;
            if (res == RESULT_OK && data != null) {
                ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) text = r.get(0);
            }
            js("onSpeech(" + (text == null ? "null" : JSONObject.quote(text)) + ")");
            return;
        }
        if (req == REQ_PICK && fileCb != null) {
            fileCb.onReceiveValue(uri != null ? new Uri[]{uri} : null);
            fileCb = null;
        } else if (req == REQ_SAVE) {
            if (uri == null) return;
            boolean ok = false;
            try {
                OutputStream o = getContentResolver().openOutputStream(uri);
                o.write(pendingJson.getBytes("UTF-8"));
                o.close();
                ok = true;
            } catch (Exception e) { }
            pendingJson = null;
            js("onAndroidSaved(" + ok + ")");
        }
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("window.onAndroidBack&&window.onAndroidBack()", new ValueCallback<String>() {
            public void onReceiveValue(String v) { if (!"true".equals(v)) MainActivity.super.onBackPressed(); }
        });
    }
}
