package net.cangkirhost.pintarsd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dipanggil saat waktu bebas habis: kunci lagi dan tampilkan layar soal. */
public class ExpireReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Store.unlockStarted(ctx) || Store.remainingMs(ctx) > 5_000L) return;
        Store.lockNow(ctx);
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            ctx.startActivity(i);
        } catch (Exception ignored) {
            // Tanpa izin "tampil di atas aplikasi lain", layar soal muncul saat anak menekan Home.
        }
    }
}
