package net.cangkirhost.pintarsd;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Menu orang tua (dilindungi PIN): atur misi, izin, buka/kunci tablet, lihat riwayat. */
public class ParentActivity extends Activity {
    private static final int INK = Color.parseColor("#2B2350");
    private static final int MUTED = Color.parseColor("#6A6191");

    private LinearLayout box;
    private boolean authed = false;
    private boolean toSystem = false;
    private TextView statusText;

    private EditText fGrade, fSets, fMin, fMinutes, fPin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#FFF9E8"));
        sv.setFillViewport(true);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(32), dp(24), dp(32));
        sv.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
        if (Store.pin(this).isEmpty()) showSetup(); else showLogin();
    }

    // ---------------- PIN ----------------
    private void showSetup() {
        box.removeAllViews();
        title("Selamat datang di Pintar SD");
        text("Buat PIN orang tua (4–6 angka). PIN dipakai untuk membuka menu ini, jadi jangan beri tahu anak.", MUTED);
        EditText p1 = pinField("PIN baru");
        EditText p2 = pinField("Ulangi PIN");
        button("Simpan PIN", "#22C55E", v -> {
            String a = p1.getText().toString(), b = p2.getText().toString();
            if (a.length() < 4) { toast("PIN minimal 4 angka"); return; }
            if (!a.equals(b)) { toast("PIN tidak sama, coba ketik ulang"); p2.setText(""); return; }
            Store.setPin(this, a);
            authed = true;
            showSettings();
        });
    }

    private void showLogin() {
        box.removeAllViews();
        title("Menu orang tua");
        text("Masukkan PIN orang tua.", MUTED);
        EditText p = pinField("PIN");
        button("Masuk", "#3B82F6", v -> {
            if (p.getText().toString().equals(Store.pin(this))) {
                authed = true;
                showSettings();
            } else {
                toast("PIN salah");
                p.setText("");
            }
        });
        button("Kembali", "#A855F7", v -> finish());
    }

    // ---------------- pengaturan ----------------
    private void showSettings() {
        box.removeAllViews();
        title("Pengaturan Pintar SD");

        section("Status");
        statusText = text("", INK);
        button("1. Jadikan Pintar SD layar utama", "#FF7A1A", v -> openSystem(new Intent(Settings.ACTION_HOME_SETTINGS)));
        button("2. Izinkan tampil di atas aplikasi lain", "#FF7A1A", v -> openSystem(
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        if (Build.VERSION.SDK_INT >= 31) {
            button("3. Izinkan alarm tepat waktu", "#FF7A1A", v -> openSystem(
                    new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))));
        }

        section("Misi sebelum tablet terbuka");
        fGrade = numField("Kelas anak (1–6)", Store.grade(this));
        fSets = numField("Jumlah latihan yang harus selesai", Store.sets(this));
        fMin = numField("Minimal jawaban benar per latihan (dari 10)", Store.minCorrect(this));
        fMinutes = numField("Waktu bebas setelah misi selesai (menit)", Store.minutes(this));
        fPin = pinField("Ganti PIN (kosongkan jika tidak diganti)");
        button("Simpan pengaturan", "#22C55E", v -> save());

        section("Kontrol cepat");
        button("Buka tablet sekarang (" + Store.minutes(this) + " menit)", "#3B82F6", v -> {
            Store.unlockFor(this, Store.minutes(this));
            toast("Tablet terbuka");
            finish();
        });
        button("Kunci tablet sekarang", "#E11D48", v -> {
            Store.lockNow(this);
            toast("Tablet dikunci");
            finish();
        });

        section("Riwayat latihan");
        text(historyText(), INK);

        button("Selesai", "#A855F7", v -> finish());
        updateStatus();
    }

    private void save() {
        int grade = parse(fGrade, 1, 1, 6);
        int sets = parse(fSets, 1, 1, 10);
        int min = parse(fMin, 7, 1, 10);
        int minutes = parse(fMinutes, 60, 5, 600);
        Store.saveSettings(this, grade, sets, min, minutes);
        String np = fPin.getText().toString();
        if (!np.isEmpty()) {
            if (np.length() < 4) { toast("PIN baru minimal 4 angka"); return; }
            Store.setPin(this, np);
            fPin.setText("");
        }
        fGrade.setText(String.valueOf(grade));
        fSets.setText(String.valueOf(sets));
        fMin.setText(String.valueOf(min));
        fMinutes.setText(String.valueOf(minutes));
        toast("Pengaturan tersimpan");
    }

    private void updateStatus() {
        if (statusText == null) return;
        boolean home = isDefaultHome();
        boolean overlay = Settings.canDrawOverlays(this);
        boolean exact = Store.canExactAlarm(this);
        String state;
        if (Store.isLocked(this)) {
            state = "Terkunci (anak harus mengerjakan soal)";
        } else {
            state = "Terbuka sampai " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(Store.until(this)));
        }
        statusText.setText(
                (home ? "✓" : "✗") + "  Layar utama: " + (home ? "Pintar SD" : "belum, tekan tombol 1") + "\n"
                + (overlay ? "✓" : "✗") + "  Tampil di atas aplikasi lain" + (overlay ? "" : ": belum, tekan tombol 2") + "\n"
                + (exact ? "✓" : "✗") + "  Alarm tepat waktu" + (exact ? "" : ": belum, tekan tombol 3") + "\n"
                + "•  Status tablet: " + state);
    }

    private boolean isDefaultHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
    }

    private String historyText() {
        JSONArray arr = Store.history(this);
        if (arr.length() == 0) return "Belum ada latihan.";
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = arr.length() - 1; i >= 0 && shown < 30; i--, shown++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            sb.append(f.format(new Date(o.optLong("t"))))
                    .append("  ·  ").append(o.optString("title"))
                    .append("  ·  ").append(o.optInt("r")).append("/").append(o.optInt("n"))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private void openSystem(Intent i) {
        toSystem = true;
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { toSystem = false; }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        toSystem = false;
        if (authed) updateStatus();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Kalau ditinggal (mis. anak menekan Recent di layar PIN), tutup menu dan kembali ke soal.
        if (!toSystem && !isChangingConfigurations() && !isFinishing()) {
            finish();
            if (Store.isLocked(this)) {
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                try { startActivity(i); } catch (Exception ignored) { }
            }
        }
    }

    // ---------------- helper tampilan ----------------
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#A855F7"));
        box.addView(t, lp(0, dp(8)));
    }

    private void section(String s) {
        TextView t = new TextView(this);
        t.setText(s.toUpperCase(Locale.getDefault()));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setTextColor(MUTED);
        box.addView(t, lp(dp(24), dp(8)));
    }

    private TextView text(String s, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.25f);
        box.addView(t, lp(0, dp(10)));
        return t;
    }

    private EditText baseField(String label) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        l.setTextColor(INK);
        l.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(l, lp(dp(6), dp(4)));
        EditText e = new EditText(this);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        e.setTextColor(INK);
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(2), Color.parseColor("#ECE6FF"));
        e.setBackground(bg);
        box.addView(e, lp(0, dp(6)));
        return e;
    }

    private EditText pinField(String label) {
        EditText e = baseField(label);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        return e;
    }

    private EditText numField(String label, int value) {
        EditText e = baseField(label);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        e.setText(String.valueOf(value));
        return e;
    }

    private void button(String label, String color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = lp(dp(8), dp(4));
        p.height = dp(52);
        box.addView(b, p);
    }

    private LinearLayout.LayoutParams lp(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = top;
        p.bottomMargin = bottom;
        return p;
    }

    private int parse(EditText e, int def, int lo, int hi) {
        try {
            int v = Integer.parseInt(e.getText().toString().trim());
            return Math.max(lo, Math.min(hi, v));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
