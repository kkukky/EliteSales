package kr.elite.routine;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** 화면이 켜질 때(잠금 상태) 앱을 잠금화면 위로 올린다. 켜 두면 항상 이 서비스가 대기한다. */
public class LockService extends Service {
    static final String CH = "keep";
    static final int ID = 2;
    private BroadcastReceiver rcv;

    static void start(Context c) {
        try {
            Intent i = new Intent(c, LockService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) { /* 백그라운드 시작이 막힌 경우: 다음에 앱을 열 때 다시 시도 */ }
    }
    static void stop(Context c) { c.stopService(new Intent(c, LockService.class)); }

    @Override public int onStartCommand(Intent in, int flags, int id) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "잠금화면 표시", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, CH).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("잠금화면 위에 표시 중").setContentText("화면이 켜지면 앱이 먼저 보여요").setOngoing(true).setShowWhen(false).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID, n);
        if (rcv == null) {
            rcv = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (!Notifs.prefs(c).getBoolean("lock", false)) return;
                    KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
                    if (km == null || !km.isKeyguardLocked()) return;
                    AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null && am.getMode() != AudioManager.MODE_NORMAL) return;   // 전화 오는 중에는 가리지 않는다
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            c.startActivity(new Intent(c, MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                        } catch (Exception e) { /* '다른 앱 위에 표시' 권한이 없으면 막힘 */ }
                    }, 300);
                }
            };
            registerReceiver(rcv, new IntentFilter(Intent.ACTION_SCREEN_ON));
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (rcv != null) { try { unregisterReceiver(rcv); } catch (Exception e) { } rcv = null; }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
