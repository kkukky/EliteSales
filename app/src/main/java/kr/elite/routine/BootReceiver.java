package kr.elite.routine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Notifs.scheduleDaily(c);
        Notifs.resetIfNewDay(c);
    }
}
