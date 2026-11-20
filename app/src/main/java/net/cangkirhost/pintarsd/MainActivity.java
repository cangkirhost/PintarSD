package net.cangkirhost.pintarsd;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/**
 * Layar utama (launcher) tablet.
 * - Terkunci: tampilkan soal Pintar SD (WebView). Anak harus menyelesaikan misi dulu.
 * - Terbuka: tampilkan daftar aplikasi + sisa waktu bebas.
 */
public class MainActivity extends Activity {
    private static final String URL = "file:///android_asset/www/index.html";

    private FrameLayout root;
    private WebView web;
    private LinearLayout appsView;
    private TextView timeText;
    private GridView grid;
    private final List<AppItem> apps = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private static final String GOOGLE_TTS = "com.google.android.tts";
    /** Status suara: init, ok, nolang (data bahasa Inggris belum ada), noengine (tidak ada mesin suara). */
    static volatile String ttsState = "init";
    private String pendingSay = null;
    private long lastTtsInit = 0L;
    private boolean webLoaded = false;
    private boolean wasLocked = true;
    boolean leavingForParent = false;

    private final Runnable relaunch = () -> {
        if (Store.isLocked(this) && !leavingForParent) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try { startActivity(i); } catch (Exception ignored) { }
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateTime();
            h.postDelayed(this, 15_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#FFF9E8"));
        setContentView(root);
        buildWeb();
        buildApps();
        initTts(null);
        if (Store.pin(this).isEmpty()) openParent(); // pertama kali: orang tua atur PIN & izin
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void buildWeb() {
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildApps() {
        appsView = new LinearLayout(this);
        appsView.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(28), dp(16), dp(20));
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#00B3A4"), Color.parseColor("#3B82F6"), Color.parseColor("#A855F7")}));

        timeText = new TextView(this);
        timeText.setTextColor(Color.WHITE);
        timeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        timeText.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(timeText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button parentBtn = new Button(this);
        parentBtn.setText("Orang tua");
        parentBtn.setAllCaps(false);
        parentBtn.setTextColor(Color.parseColor("#2B2350"));
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(Color.WHITE);
        pb.setCornerRadius(dp(14));
        parentBtn.setBackground(pb);
        parentBtn.setPadding(dp(16), 0, dp(16), 0);
        parentBtn.setOnClickListener(v -> openParent());
        header.addView(parentBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));

        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(104));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(14));
        grid.setPadding(dp(12), dp(16), dp(12), dp(16));
        grid.setClipToPadding(false);
        grid.setOnItemClickListener((parent, v, pos, id) -> {
            if (pos < 0 || pos >= apps.size()) return;
            AppItem a = apps.get(pos);
            Intent li = new Intent(Intent.ACTION_MAIN);
            li.addCategory(Intent.CATEGORY_LAUNCHER);
            li.setClassName(a.pkg, a.cls);
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try { startActivity(li); } catch (Exception ignored) { }
        });

        appsView.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        appsView.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(appsView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        appsView.setVisibility(View.GONE);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        Set<String> hidden = hiddenPackages(pm);
        apps.clear();
        for (ResolveInfo r : list) {
            if (r.activityInfo == null) continue;
            if (getPackageName().equals(r.activityInfo.packageName)) continue;
            if (hidden.contains(r.activityInfo.packageName)) continue;
            AppItem a = new AppItem();
            a.label = String.valueOf(r.loadLabel(pm));
            a.pkg = r.activityInfo.packageName;
            a.cls = r.activityInfo.name;
            a.icon = r.loadIcon(pm);
            apps.add(a);
        }
        Collections.sort(apps, (x, y) -> x.label.compareToIgnoreCase(y.label));
        grid.setAdapter(new AppAdapter());
    }

    /**
     * Aplikasi Setelan disembunyikan dari daftar anak, supaya anak tidak mudah
     * mengganti layar utama. Orang tua tetap bisa membukanya dari menu orang tua.
     */
    private Set<String> hiddenPackages(PackageManager pm) {
        Set<String> s = new HashSet<>();
        s.add("com.android.settings");
        for (String action : new String[]{Settings.ACTION_SETTINGS, Settings.ACTION_DATE_SETTINGS}) {
            try {
                ResolveInfo r = pm.resolveActivity(new Intent(action), PackageManager.MATCH_DEFAULT_ONLY);
                if (r != null && r.activityInfo != null && !"android".equals(r.activityInfo.packageName)) {
                    s.add(r.activityInfo.packageName);
                }
            } catch (Exception ignored) { }
        }
        return s;
    }

    private void refresh() {
        boolean locked = Store.isLocked(this);
        if (locked) {
            appsView.setVisibility(View.GONE);
            web.setVisibility(View.VISIBLE);
            if (!webLoaded) {
                web.loadUrl(URL);
                webLoaded = true;
            } else if (!wasLocked) {
                web.reload(); // misi baru dimulai dari awal
            }
            immersive(true);
        } else {
            web.setVisibility(View.GONE);
            appsView.setVisibility(View.VISIBLE);
            loadApps();
            immersive(false);
            updateTime();
        }
        wasLocked = locked;
    }

    private void updateTime() {
        if (Store.isLocked(this)) {
            if (!wasLocked) refresh();
            return;
        }
        long left = Store.remainingMs(this);
        long min = (left + 59_999L) / 60_000L;
        timeText.setText("Waktu bebas: " + min + " menit lagi");
    }

    private void immersive(boolean on) {
        View d = getWindow().getDecorView();
        if (on) {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        } else {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    void openParent() {
        leavingForParent = true;
        startActivity(new Intent(this, ParentActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.removeCallbacks(relaunch);
        if (leavingForParent) {
            leavingForParent = false;
            if (webLoaded) web.reload(); // pengaturan (kelas, misi) mungkin berubah
        }
        refresh();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Saat terkunci, anak tidak boleh pindah ke aplikasi lain (mis. lewat tombol Recent).
        if (Store.isLocked(this) && !leavingForParent && !isChangingConfigurations()
                && Settings.canDrawOverlays(this)) {
            h.postDelayed(relaunch, 500L);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && Store.isLocked(this)) immersive(true);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        // Launcher: tombol kembali tidak melakukan apa-apa.
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    // ---------------- suara Bahasa Inggris (Text-to-Speech) ----------------
    /** @param engine null = mesin suara bawaan tablet; kalau gagal dicoba Google TTS. */
    private void initTts(String engine) {
        lastTtsInit = SystemClock.elapsedRealtime();
        ttsState = "init";
        if (tts != null) {
            try { tts.shutdown(); } catch (Exception ignored) { }
            tts = null;
        }
        TextToSpeech.OnInitListener l = status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) {
                if (engine == null && isInstalled(GOOGLE_TTS)) { initTts(GOOGLE_TTS); return; }
                ttsState = "noengine";
                return;
            }
            int r = TextToSpeech.LANG_NOT_SUPPORTED;
            for (Locale loc : new Locale[]{Locale.US, Locale.UK, Locale.ENGLISH}) {
                try { r = tts.setLanguage(loc); } catch (Exception e) { r = TextToSpeech.LANG_NOT_SUPPORTED; }
                if (r >= TextToSpeech.LANG_AVAILABLE) break;
            }
            if (r < TextToSpeech.LANG_AVAILABLE && engine == null
                    && !GOOGLE_TTS.equals(tts.getDefaultEngine()) && isInstalled(GOOGLE_TTS)) {
                initTts(GOOGLE_TTS);
                return;
            }
            ttsState = r < TextToSpeech.LANG_AVAILABLE ? "nolang" : "ok";
            tts.setSpeechRate(0.85f);
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            if (pendingSay != null) {
                tts.speak(pendingSay, TextToSpeech.QUEUE_FLUSH, null, "pintar");
                pendingSay = null;
            }
        };
        tts = engine == null ? new TextToSpeech(this, l) : new TextToSpeech(this, l, engine);
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void sayNow(String text) {
        if (TextUtils.isEmpty(text)) return;
        String st = ttsState;
        if ("ok".equals(st) && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pintar");
            return;
        }
        pendingSay = text;
        // Mesin suara gagal sebelumnya: coba lagi (mungkin orang tua baru memasang data suaranya).
        if (!"init".equals(st) && SystemClock.elapsedRealtime() - lastTtsInit > 5_000L) initTts(null);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------- jembatan ke halaman soal ----------------
    private class Bridge {
        @JavascriptInterface
        public String getConfig() {
            JSONObject o = new JSONObject();
            try {
                o.put("grade", Store.grade(MainActivity.this));
                o.put("sets", Store.sets(MainActivity.this));
                o.put("minCorrect", Store.minCorrect(MainActivity.this));
                o.put("minutes", Store.minutes(MainActivity.this));
                o.put("done", Store.progress(MainActivity.this));
            } catch (JSONException ignored) { }
            return o.toString();
        }

        @JavascriptInterface
        public void reportSet(String title, int right, int total) {
            Store.addHistory(MainActivity.this, title, right, total);
            if (right >= Store.minCorrect(MainActivity.this)) Store.incProgress(MainActivity.this);
        }

        /** Latihan biasa: hanya dicatat di riwayat, tidak dihitung untuk misi. */
        @JavascriptInterface
        public void logSet(String title, int right, int total) {
            Store.addHistory(MainActivity.this, title, right, total);
        }

        @JavascriptInterface
        public void unlock() {
            // Dicek di sisi aplikasi, bukan hanya di halaman.
            if (Store.progress(MainActivity.this) >= Store.sets(MainActivity.this)) {
                Store.unlockFor(MainActivity.this, Store.minutes(MainActivity.this));
                runOnUiThread(MainActivity.this::refresh);
            }
        }

        @JavascriptInterface
        public void speak(String text) {
            runOnUiThread(() -> sayNow(text));
        }

        /** init / ok / nolang / noengine */
        @JavascriptInterface
        public String ttsStatus() {
            return ttsState;
        }

        /** Volume media tablet (0 = tanpa suara). */
        @JavascriptInterface
        public int volume() {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            return am == null ? 1 : am.getStreamVolume(AudioManager.STREAM_MUSIC);
        }

        @JavascriptInterface
        public void openParent() {
            runOnUiThread(MainActivity.this::openParent);
        }
    }

    // ---------------- daftar aplikasi ----------------
    private static class AppItem {
        String label, pkg, cls;
        Drawable icon;
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int pos) { return apps.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convert instanceof LinearLayout) {
                cell = (LinearLayout) convert;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(4), dp(8), dp(4), dp(8));
                icon = new ImageView(MainActivity.this);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(60), dp(60)));
                label = new TextView(MainActivity.this);
                label.setGravity(Gravity.CENTER_HORIZONTAL);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                label.setTextColor(Color.parseColor("#2B2350"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                cell.addView(label, lp);
            }
            AppItem a = apps.get(pos);
            icon.setImageDrawable(a.icon);
            label.setText(a.label);
            return cell;
        }
    }
}
