package com.daftari.notes;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/** Schedules and shows due-date reminders. The web app sends the full list; we keep it for reboots. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "reminders";
    static final int FLAG_IMMUTABLE = 67108864; // PendingIntent.FLAG_IMMUTABLE (API 23)

    @Override public void onReceive(Context ctx, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
            SharedPreferences p = ctx.getSharedPreferences("stratex", Context.MODE_PRIVATE);
            schedule(ctx, p.getString("reminders", "[]"));
            return;
        }
        show(ctx, i.getIntExtra("code", 0), i.getStringExtra("title"), i.getStringExtra("body"));
    }

    static PendingIntent alarmIntent(Context ctx, int code, String title, String body) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.setAction("com.daftari.notes.REMIND." + code);
        i.putExtra("code", code); i.putExtra("title", title); i.putExtra("body", body);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
    }

    /** json: [{code:int, at:ms, title, body}] — replaces everything scheduled before. */
    static void schedule(Context ctx, String json) {
        SharedPreferences p = ctx.getSharedPreferences("stratex", Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        try {
            JSONArray old = new JSONArray(p.getString("reminders", "[]"));
            for (int k = 0; k < old.length(); k++) {
                JSONObject o = old.getJSONObject(k);
                am.cancel(alarmIntent(ctx, o.getInt("code"), o.optString("title"), o.optString("body")));
            }
            JSONArray list = new JSONArray(json);
            long now = System.currentTimeMillis();
            for (int k = 0; k < list.length(); k++) {
                JSONObject o = list.getJSONObject(k);
                long at = o.getLong("at");
                if (at < now) continue;
                PendingIntent pi = alarmIntent(ctx, o.getInt("code"), o.optString("title"), o.optString("body"));
                try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); }
                catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); }
            }
            p.edit().putString("reminders", json).apply();
        } catch (Exception e) { }
    }

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            Class<?> ch = Class.forName("android.app.NotificationChannel");
            Object c = ch.getConstructor(String.class, CharSequence.class, int.class).newInstance(CHANNEL, "تذكير المواعيد", 4);
            NotificationManager.class.getMethod("createNotificationChannel", ch).invoke(nm, c);
        } catch (Exception e) { }
    }

    static void show(Context ctx, int code, String title, String body) {
        ensureChannel(ctx);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
        Notification.Builder b;
        try {
            b = (Build.VERSION.SDK_INT >= 26)
                ? (Notification.Builder) Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(ctx, CHANNEL)
                : new Notification.Builder(ctx);
        } catch (Exception e) { b = new Notification.Builder(ctx); }
        b.setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(body)
         .setStyle(new Notification.BigTextStyle().bigText(body))
         .setContentIntent(pi).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH)
         .setDefaults(Notification.DEFAULT_ALL).setColor(0xFF50C83C);
        nm.notify(code, b.build());
    }
}
