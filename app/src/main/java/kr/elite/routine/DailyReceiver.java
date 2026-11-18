package kr.elite.routine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DailyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { Notifs.resetIfNewDay(c); }
}
