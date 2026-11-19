package net.cangkirhost.pintarsd;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Semua pengaturan & status kunci disimpan di SharedPreferences. */
public final class Store {
    private Store() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("pintar", Context.MODE_PRIVATE);
    }

    // ---- pengaturan orang tua ----
    public static int grade(Context c) { return p(c).getInt("grade", 1); }
    public static int sets(Context c) { return p(c).getInt("sets", 1); }
    public static int minCorrect(Context c) { return p(c).getInt("minCorrect", 7); }
    public static int minutes(Context c) { return p(c).getInt("minutes", 60); }
    public static String pin(Context c) { return p(c).getString("pin", ""); }

    public static void saveSettings(Context c, int grade, int sets, int minCorrect, int minutes) {
        p(c).edit().putInt("grade", grade).putInt("sets", sets)
                .putInt("minCorrect", minCorrect).putInt("minutes", minutes).apply();
    }

    public static void setPin(Context c, String pin) { p(c).edit().putString("pin", pin).apply(); }

    // ---- status misi ----
    public static int progress(Context c) { return p(c).getInt("progress", 0); }

    public static void incProgress(Context c) {
        p(c).edit().putInt("progress", progress(c) + 1).apply();
    }

    // ---- status kunci ----
    // Waktu bebas dihitung dengan jam sejak tablet menyala (elapsedRealtime), BUKAN jam dinding.
    // Jadi mengubah jam/tanggal di Setelan tidak bisa memperpanjang waktu bebas.
    // Setelah tablet dinyalakan ulang, tablet selalu kembali terkunci.
    private static final String K_UNTIL = "unlockUntilElapsed";
    private static final String K_BOOT = "unlockBoot";
    private static final String K_LEN = "unlockLength";

    private static int bootCount(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Sisa waktu bebas dalam milidetik; 0 jika terkunci. */
    public static long remainingMs(Context c) {
        long until = p(c).getLong(K_UNTIL, 0L);
        if (until == 0L) return 0L;
        if (p(c).getInt(K_BOOT, -2) != bootCount(c)) return 0L; // tablet sudah restart
        long left = until - SystemClock.elapsedRealtime();
        // Pengaman tambahan: sisa waktu tidak pernah lebih dari lama waktu bebas yang diberikan.
        long len = p(c).getLong(K_LEN, 0L);
        if (left > len) return 0L;
        return Math.max(0L, left);
    }

    /** true jika ada waktu bebas yang sedang berjalan atau baru saja habis (belum dikunci). */
    public static boolean unlockStarted(Context c) {
        return p(c).getLong(K_UNTIL, 0L) != 0L;
    }

    public static boolean isLocked(Context c) {
        return remainingMs(c) <= 0L;
    }

    /** Perkiraan jam dinding saat waktu bebas berakhir (hanya untuk ditampilkan). */
    public static long until(Context c) {
        return System.currentTimeMillis() + remainingMs(c);
    }

    public static void unlockFor(Context c, int minutes) {
        long len = minutes * 60_000L;
        long until = SystemClock.elapsedRealtime() + len;
        p(c).edit().putLong(K_UNTIL, until).putLong(K_LEN, len).putInt(K_BOOT, bootCount(c))
                .putInt("progress", 0).remove("unlockUntil").apply();
        scheduleAlarm(c, until);
    }

    public static void lockNow(Context c) {
        p(c).edit().putLong(K_UNTIL, 0L).remove("unlockUntil").apply();
        cancelAlarm(c);
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, ExpireReceiver.class);
        return PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static boolean canExactAlarm(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    /** @param atElapsed waktu dalam SystemClock.elapsedRealtime() */
    public static void scheduleAlarm(Context c, long atElapsed) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = alarmIntent(c);
        try {
            if (canExactAlarm(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, pi);
            } else {
                am.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, 60_000L, pi);
            }
        } catch (SecurityException e) {
            am.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, 60_000L, pi);
        }
    }

    public static void cancelAlarm(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmIntent(c));
    }

    // ---- riwayat ----
    public static void addHistory(Context c, String title, int right, int total) {
        try {
            JSONArray arr = new JSONArray(p(c).getString("history", "[]"));
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("title", title);
            o.put("r", right);
            o.put("n", total);
            arr.put(o);
            while (arr.length() > 50) arr.remove(0);
            p(c).edit().putString("history", arr.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public static JSONArray history(Context c) {
        try {
            return new JSONArray(p(c).getString("history", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
