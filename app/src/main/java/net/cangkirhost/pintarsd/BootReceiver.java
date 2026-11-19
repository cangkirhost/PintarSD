package net.cangkirhost.pintarsd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Setelah tablet dinyalakan ulang, tablet selalu terkunci lagi.
 * (Waktu bebas dihitung sejak tablet menyala, jadi tidak bisa dilanjutkan setelah restart.)
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Store.lockNow(ctx);
    }
}
