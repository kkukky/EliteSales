package kr.elite.routine;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.ContentValues;
import android.net.Uri;
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
        Notifs.scheduleDaily(this);
        Notifs.resetIfNewDay(this);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (web != null) web.post(() -> web.evaluateJavascript("window.syncNotif&&syncNotif(true)", null));   // 허용 직후 알림 표시
    }

    /** 엑셀 파일을 '다운로드' 폴더에 저장 */
    class Bridge {
        @JavascriptInterface public void setProgress(int done, int total, String title, String next, String date, String first) {
            Notifs.post(MainActivity.this, done, total, title, next, date, first);
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
