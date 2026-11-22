package kr.elite.routine;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;

/** 알림 창에 '오늘의 루틴' 진행 상황을 보여주는 상시 알림 */
public class Notifs {
    static final String CH = "routine2";   // 잠금화면에 잘 보이도록 중요도를 올린 새 채널
    static final int ID = 1;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("notif", Context.MODE_PRIVATE); }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel("routine") != null) nm.deleteNotificationChannel("routine");
        if (nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "오늘의 루틴", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("오늘 루틴 진행 상황");
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
    }

    static void post(Context c, int done, int total, String title, String next, String date, String first) {
        prefs(c).edit().putBoolean("on", true).putString("day", LocalDate.now().toString())
                .putInt("total", total).putString("first", first)
                .putInt("done", done).putString("title", title).putString("next", next).putString("date", date).apply();
        ensureChannel(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title + "  " + done + "/" + total)
                .setContentText(next)
                .setSubText(date)
                .setProgress(total, done, false)
                .setColor(0xFF960303)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(prefs(c).getBoolean("lock", false) ? Notification.VISIBILITY_PUBLIC : Notification.VISIBILITY_PRIVATE)
                .setContentIntent(pi)
                .setDeleteIntent(PendingIntent.getBroadcast(c, 2, new Intent(c, DailyReceiver.class).setAction(ACT_DISMISSED), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .setAutoCancel(false)
                .build();
        n.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        c.getSystemService(NotificationManager.class).notify(ID, n);
    }

    static final String ACT_DISMISSED = "kr.elite.routine.NOTIF_DISMISSED";

    /** 밀어서 지워졌거나, 재부팅·업데이트·날짜 변경 뒤에도 마지막 상태로 다시 띄운다 */
    static void repost(Context c) {
        SharedPreferences sp = prefs(c);
        if (!sp.getBoolean("on", false)) return;
        post(c, sp.getInt("done", 0), sp.getInt("total", 6), sp.getString("title", "오늘의 루틴을 점검해요"),
                sp.getString("next", sp.getString("first", "다음 · 출근 기록")), sp.getString("date", ""), sp.getString("first", "다음 · 출근 기록"));
    }

    static void clear(Context c) {
        prefs(c).edit().putBoolean("on", false).apply();
        c.getSystemService(NotificationManager.class).cancel(ID);
    }

    /** 날짜가 바뀌었는데 앱을 열지 않았다면 0/N으로 새로 시작 */
    static void resetIfNewDay(Context c) {
        SharedPreferences sp = prefs(c);
        if (!sp.getBoolean("on", false)) return;
        String today = LocalDate.now().toString();
        if (today.equals(sp.getString("day", ""))) { repost(c); return; }
        String first = sp.getString("first", "다음 · 출근 기록");
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN));
        post(c, 0, sp.getInt("total", 6), "오늘의 루틴을 점검해요", first, date, first);
    }

    /** 0시 5분부터 6시간마다 날짜 변경과 알림 유지를 확인 */
    static void scheduleDaily(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, DailyReceiver.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 5); cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() < System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        am.setInexactRepeating(AlarmManager.RTC, cal.getTimeInMillis(), AlarmManager.INTERVAL_HOUR * 6, pi);
    }
}
