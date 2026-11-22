package kr.elite.routine;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.provider.DocumentsContract;
import org.json.JSONObject;
import android.net.Uri;
import android.provider.Settings;
import android.view.WindowManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private static final int REQ_TREE = 7741;
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage 사용 (루틴 기록 저장)
        s.setAllowFileAccess(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        applyLock(Notifs.prefs(this).getBoolean("lock", false));
        Notifs.scheduleDaily(this);
        Notifs.resetIfNewDay(this);
        web.loadUrl("file:///android_asset/index.html");
    }

    /** 잠금화면 위에 앱 표시 켜기/끄기 */
    private void applyLock(boolean on) {
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(on); }
        else if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    @Override protected void onResume() {
        super.onResume();   // 설정 화면에서 돌아오면 권한 상태를 다시 읽는다
        if (web != null) web.post(() -> web.evaluateJavascript("window.permBack&&permBack()", null));
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (web != null) web.post(() -> web.evaluateJavascript("window.syncNotif&&syncNotif(true)", null));   // 허용 직후 알림 표시
    }


    /* ---------- 저장 폴더 선택 (SAF) ---------- */
    private Uri tree() { String u = Notifs.prefs(this).getString("tree", ""); return u.isEmpty() ? null : Uri.parse(u); }
    private Uri findChild(Uri t, String name) {
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(t, DocumentsContract.getTreeDocumentId(t));
        Cursor c = null;
        try {
            c = getContentResolver().query(kids, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            while (c != null && c.moveToNext()) if (name.equals(c.getString(1))) return DocumentsContract.buildDocumentUriUsingTree(t, c.getString(0));
        } catch (Exception e) { /* 폴더가 지워졌거나 접근 불가 */ } finally { if (c != null) c.close(); }
        return null;
    }
    private String treeLabel(Uri t) {
        String id = DocumentsContract.getTreeDocumentId(t);   // 예: primary:Documents/ELITE 기록
        int k = id.indexOf(':');
        String vol = k < 0 ? id : id.substring(0, k), path = k < 0 ? "" : id.substring(k + 1);
        String base = "primary".equals(vol) ? "내장 저장소" : "SD카드";
        return path.isEmpty() ? base : base + " › " + path.replace("/", " › ");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_TREE) return;
        boolean ok = false;
        if (res == RESULT_OK && data != null && data.getData() != null) {
            Uri t = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(t, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                Notifs.prefs(this).edit().putString("tree", t.toString()).apply();
                ok = true;
            } catch (Exception e) { ok = false; }
        }
        final boolean fok = ok;
        if (web != null) web.post(() -> web.evaluateJavascript("window.onFolderPicked&&onFolderPicked(" + fok + ")", null));
    }

    /** 엑셀 파일을 '다운로드' 폴더 또는 고른 폴더에 저장 */
    class Bridge {
        @JavascriptInterface public void setProgress(int done, int total, String title, String next, String date, String first) {
            Notifs.post(MainActivity.this, done, total, title, next, date, first);
        }
        @JavascriptInterface public void setLockShow(boolean on) {
            Notifs.prefs(MainActivity.this).edit().putBoolean("lock", on).apply();
            runOnUiThread(() -> applyLock(on));
        }
        @JavascriptInterface public boolean canOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(MainActivity.this); }
        @JavascriptInterface public void openOverlaySettings() {
            runOnUiThread(() -> {
                try { startActivity(new android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { startActivity(new android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
            });
        }
        @JavascriptInterface public void pickFolder() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    if (Build.VERSION.SDK_INT >= 26) i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents"));
                    startActivityForResult(i, REQ_TREE);
                } catch (Exception e) { web.evaluateJavascript("window.onFolderPicked&&onFolderPicked(false)", null); }
            });
        }
        @JavascriptInterface public String getFolder() {
            JSONObject o = new JSONObject();
            try {
                Uri t = tree();
                boolean ok = false; String label = "";
                if (t != null) {
                    label = treeLabel(t);
                    for (UriPermission p : getContentResolver().getPersistedUriPermissions()) if (p.getUri().equals(t) && p.isWritePermission()) ok = true;
                }
                o.put("has", t != null); o.put("ok", ok); o.put("label", label);
            } catch (Exception e) { /* 빈 값 */ }
            return o.toString();
        }
        @JavascriptInterface public boolean folderHasFile(String name) { Uri t = tree(); return t != null && findChild(t, name) != null; }
        @JavascriptInterface public boolean saveToFolder(String name, String base64) {
            try {
                Uri t = tree(); if (t == null) return false;
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                Uri f = findChild(t, name);
                if (f == null) f = DocumentsContract.createDocument(getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(t, DocumentsContract.getTreeDocumentId(t)), XLSX, name);
                if (f == null) return false;
                OutputStream out = getContentResolver().openOutputStream(f, "wt");
                out.write(data); out.close();
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public void clearFolder() {
            try {
                Uri t = tree();
                if (t != null) getContentResolver().releasePersistableUriPermission(t, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) { /* 이미 해제됨 */ }
            Notifs.prefs(MainActivity.this).edit().remove("tree").apply();
        }
        @JavascriptInterface public void clearNotif() { Notifs.clear(MainActivity.this); }
        @JavascriptInterface public boolean saveFile(String name, String base64) {
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                String mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                OutputStream out;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    out = getContentResolver().openOutputStream(u);
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    out = new FileOutputStream(new File(dir, name));
                }
                out.write(data); out.close();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "다운로드 폴더에 저장했어요: " + name, Toast.LENGTH_LONG).show());
                return true;
            } catch (Exception e) { return false; }
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
