package com.retro.explorer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // ---------------------------------------------------------------- constants
    static final String V_LARGE = "large", V_TILES = "tiles", V_LIST = "list", V_DETAILS = "details";
    static final int LOC_DIR = 0, LOC_BIN = 1, LOC_SEARCH = 2;

    static final int M_OPEN = 1, M_OPENWITH = 2, M_SHARE = 3, M_CUT = 4, M_COPY = 5, M_COPYTO = 6, M_MOVETO = 7,
            M_RENAME = 8, M_DELETE = 9, M_DELPERM = 10, M_PIN = 11, M_LOCATION = 12, M_PROPS = 13, M_RESTORE = 14,
            M_SELALL = 15, M_PASTE_INTO = 16;
    static final int X_NEWFILE = 101, X_PASTE = 102, X_SELECT = 103, X_SELALL = 104, X_INVERT = 105, X_COPYTO = 106,
            X_MOVETO = 107, X_SHARE = 108, X_PROPS = 109, X_PIN = 110, X_HIDDEN = 111, X_PLACES = 112, X_HOME = 113,
            X_BIN = 114, X_EMPTY = 115, X_REFRESH = 116, X_ABOUT = 117, X_NEWFOLDER = 118, X_GOTO = 119, X_THEME = 120,
            X_VIEW = 121, X_SORT = 122, X_STORAGE = 200;

    static final class Loc {
        final int type;
        final File dir;
        final String q;

        Loc(int type, File dir, String q) {
            this.type = type;
            this.dir = dir;
            this.q = q;
        }
    }

    interface OpBody { void run(List<String> errs) throws Exception; }
    interface NameCb { String apply(String value); }
    interface FileCb { void on(File f); }

    // ---------------------------------------------------------------- state
    SharedPreferences prefs;
    Theme T;
    File root;
    RecycleBin bin;
    Loc loc;
    final ArrayDeque<Loc> backStack = new ArrayDeque<>(), fwdStack = new ArrayDeque<>();
    final List<Item> items = new ArrayList<>();
    final Set<String> sel = new HashSet<>();
    boolean selMode;
    final List<File> clip = new ArrayList<>();
    boolean clipCut;
    String viewMode, sortKey;
    boolean sortAsc, showHidden, sideOpen, fullscreen, suppressSearch;
    final List<String> pins = new ArrayList<>();
    final Set<String> expanded = new HashSet<>();
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newCachedThreadPool();
    final ExecutorService thumbIo = Executors.newFixedThreadPool(2);
    LruCache<String, Bitmap> thumbs;
    final Set<String> thumbFailed = new HashSet<>();
    int loadToken;

    // ---------------------------------------------------------------- views
    LinearLayout headerRow, detailsPane, crumbs, sideList, cmdRow;
    TextView titleText, statusText, emptyText, skTitle;
    ImageView titleIcon, backBtn, fwdBtn, upBtn;
    GridView grid;
    FrameLayout sideOverlay;
    View permView;
    HorizontalScrollView crumbScroll;
    EditText searchBox;
    ItemAdapter adapter;
    final Map<String, View> cmdBtns = new HashMap<>();

    // ================================================================ lifecycle
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("explorer", MODE_PRIVATE);
        root = Environment.getExternalStorageDirectory();
        bin = new RecycleBin(root);
        viewMode = prefs.getString("view", V_LARGE);
        sortKey = prefs.getString("sort", "name");
        sortAsc = prefs.getBoolean("asc", true);
        showHidden = prefs.getBoolean("hidden", false);
        loadPins();
        loc = new Loc(LOC_DIR, root, null);
        expanded.add(root.getAbsolutePath());
        int cacheKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        thumbs = new LruCache<String, Bitmap>(cacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
        T = Theme.get(prefs.getString("theme", "7"));
        buildUi();
        renderAll();
        if (!hasPerm()) showPermPanel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPerm()) refresh();
        else showPermPanel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        thumbIo.shutdownNow();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        buildUi();
        renderAll();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (hasPerm()) refresh();
    }

    @Override
    public void onBackPressed() {
        if (sideOpen) { hideSide(); return; }
        if (selMode || !sel.isEmpty()) { clearSelection(); return; }
        if (goBack()) return;
        if (goUp()) return;
        super.onBackPressed();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        if (searchBox != null && searchBox.hasFocus()) return super.onKeyUp(keyCode, e);
        if (e.isCtrlPressed()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A: selectAll(); return true;
                case KeyEvent.KEYCODE_C: setClip(false); return true;
                case KeyEvent.KEYCODE_X: setClip(true); return true;
                case KeyEvent.KEYCODE_V: if (loc.type == LOC_DIR) paste(loc.dir); return true;
                default: break;
            }
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_FORWARD_DEL: deleteSel(e.isShiftPressed()); return true;
            case KeyEvent.KEYCODE_F2: renameSel(); return true;
            case KeyEvent.KEYCODE_F5: refresh(); return true;
            default: return super.onKeyUp(keyCode, e);
        }
    }

    // ================================================================ permissions
    boolean hasPerm() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT >= 23)
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return true;
    }

    void requestPerm() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
                catch (Exception ignored) { toast("Open Settings › Apps › Explorer and allow file access."); }
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    void showPermPanel() { if (permView != null) permView.setVisibility(View.VISIBLE); }

    void hidePermPanel() { if (permView != null) permView.setVisibility(View.GONE); }

    // ================================================================ small UI helpers
    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    static GradientDrawable grad(int[] c, float radius) {
        GradientDrawable g;
        if (c.length == 1) {
            g = new GradientDrawable();
            g.setColor(c[0]);
        } else {
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, c);
        }
        g.setCornerRadius(radius);
        return g;
    }

    static GradientDrawable solid(int color, float radius) { return grad(new int[]{color}, radius); }

    TextView tv(String s, int color, float sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    LinearLayout hl() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    LinearLayout vl() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    Drawable pressable(Drawable normal, int pressedColor, float radius) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, solid(pressedColor, radius));
        s.addState(new int[]{}, normal != null ? normal : new ColorDrawable(0));
        return s;
    }

    boolean wide() { return getResources().getConfiguration().screenWidthDp >= 480; }

    AlertDialog.Builder builder() {
        return new AlertDialog.Builder(this, T.dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    Context popupCtx() {
        return new ContextThemeWrapper(this, T.dark ? android.R.style.Theme_Material : android.R.style.Theme_Material_Light);
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    void alert(String title, String msg) { builder().setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); }

    void confirm(String title, String msg, final Runnable yes) {
        builder().setTitle(title).setMessage(msg)
                .setPositiveButton("Yes", (d, w) -> yes.run())
                .setNegativeButton("No", null).show();
    }

    void prompt(String title, final String def, final boolean selectStem, String ok, final NameCb cb) {
        final EditText et = new EditText(builder().getContext());
        et.setSingleLine(true);
        et.setText(def);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(et);
        final AlertDialog d = builder().setTitle(title).setView(wrap).setPositiveButton(ok, null).setNegativeButton("Cancel", null).create();
        Window w = d.getWindow();
        if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        d.setOnShowListener(x -> {
            int dot = selectStem ? def.lastIndexOf('.') : -1;
            et.setSelection(0, dot > 0 ? dot : def.length());
            et.requestFocus();
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String err = cb.apply(et.getText().toString().trim());
                if (err == null) d.dismiss();
                else et.setError(err);
            });
        });
        d.show();
    }

    AlertDialog progressDialog(String msg) {
        LinearLayout l = hl();
        l.setPadding(dp(24), dp(20), dp(24), dp(20));
        l.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar pb = new ProgressBar(builder().getContext());
        l.addView(pb, dp(36), dp(36));
        TextView t = new TextView(builder().getContext());
        t.setText(msg);
        t.setPadding(dp(16), 0, 0, 0);
        t.setTextSize(16);
        l.addView(t);
        AlertDialog d = builder().setView(l).setCancelable(false).create();
        d.show();
        return d;
    }

    void runOp(String msg, final OpBody body) {
        final AlertDialog pd = progressDialog(msg);
        io.execute(() -> {
            final List<String> errs = new ArrayList<>();
            try {
                body.run(errs);
            } catch (Exception e) {
                errs.add(String.valueOf(e.getMessage()));
            }
            ui.post(() -> {
                try { pd.dismiss(); } catch (Exception ignored) { }
                if (!errs.isEmpty()) alert("Some items had problems", TextUtils.join("\n", errs));
                refresh();
            });
        });
    }

    // ================================================================ storage roots
    List<File> storageRoots() {
        List<File> out = new ArrayList<>();
        out.add(root);
        try {
            File[] dirs = getExternalFilesDirs(null);
            if (dirs != null) {
                for (File f : dirs) {
                    if (f == null) continue;
                    String p = f.getAbsolutePath();
                    int i = p.indexOf("/Android/data/");
                    if (i <= 0) continue;
                    File r = new File(p.substring(0, i));
                    if (!r.equals(root) && !out.contains(r)) out.add(r);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    File rootFor(File d) {
        File best = null;
        for (File r : storageRoots())
            if (FileOps.isInside(d, r) && (best == null || r.getAbsolutePath().length() > best.getAbsolutePath().length())) best = r;
        return best;
    }

    String rootLabel(File r) { return r.equals(root) ? "Internal storage" : "SD card (" + r.getName() + ")"; }

    // ================================================================ prefs
    void loadPins() {
        pins.clear();
        String s = prefs.getString("pins", null);
        if (s == null) {
            for (String n : new String[]{"Download", "Documents", "DCIM", "Pictures", "Music", "Movies"}) {
                File f = new File(root, n);
                pins.add(f.getAbsolutePath());
            }
            return;
        }
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) pins.add(a.getString(i));
        } catch (Exception ignored) { }
    }

    void savePins() {
        JSONArray a = new JSONArray();
        for (String p : pins) a.put(p);
        prefs.edit().putString("pins", a.toString()).apply();
    }

    void togglePin(File f) {
        String p = f.getAbsolutePath();
        if (pins.contains(p)) { pins.remove(p); toast("Unpinned from Quick access"); }
        else { pins.add(p); toast("Pinned to Quick access"); }
        savePins();
        if (sideOpen) renderSide();
    }

    void movePins(File from, File to) {
        String a = from.getAbsolutePath(), b = to.getAbsolutePath();
        for (int i = 0; i < pins.size(); i++) {
            String p = pins.get(i);
            if (p.equals(a)) pins.set(i, b);
            else if (p.startsWith(a + "/")) pins.set(i, b + p.substring(a.length()));
        }
        savePins();
    }

    // ================================================================ building the UI
    void buildUi() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(T.title[0]);
            w.setNavigationBarColor(T.dark ? 0xFF000000 : 0xFF202020);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            View decor = w.getDecorView();
            int f = decor.getSystemUiVisibility();
            f = T.lightTitle ? (f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) : (f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            decor.setSystemUiVisibility(f);
        }
        cmdBtns.clear();

        LinearLayout rootL = vl();
        rootL.setBackgroundColor(T.frame);
        rootL.addView(buildTitle(), lp(-1, dp(T.titleH), 0));
        if (T.accentLine) {
            View line = new View(this);
            line.setBackgroundColor(T.accent);
            rootL.addView(line, lp(-1, dp(3), 0));
        }
        LinearLayout inner = vl();
        int fw = dp(T.frameW);
        inner.setPadding(fw, 0, fw, fw);
        View nav = buildNav();
        View cmd = buildCmd();
        if (T.xpTasks) { inner.addView(cmd); inner.addView(nav); }
        else { inner.addView(nav); inner.addView(cmd); }
        inner.addView(buildBody(), lp(-1, 0, 1));

        detailsPane = hl();
        detailsPane.setGravity(Gravity.CENTER_VERTICAL);
        detailsPane.setPadding(dp(14), dp(4), dp(14), dp(4));
        detailsPane.setBackground(grad(T.details, 0));
        inner.addView(detailsPane, lp(-1, dp(60), 0));
        detailsPane.setVisibility(T.detailsPane ? View.VISIBLE : View.GONE);

        statusText = tv("", T.statusText, 12);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        statusText.setPadding(dp(10), 0, dp(10), 0);
        statusText.setBackground(grad(T.status, 0));
        if (!T.softkeys) inner.addView(statusText, lp(-1, dp(26), 0));
        if (T.softkeys) inner.addView(buildSoftkeys(), lp(-1, dp(50), 0));

        rootL.addView(inner, lp(-1, 0, 1));
        setContentView(rootL);
        applyViewMode();
        if (loc != null && loc.type == LOC_SEARCH) setSearchText(loc.q);
        sideOverlay.setVisibility(sideOpen ? View.VISIBLE : View.GONE);
    }

    View buildTitle() {
        LinearLayout t = hl();
        t.setGravity(T.softkeys ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        t.setBackground(grad(T.title, 0));
        t.setPadding(dp(8), 0, dp(4), 0);
        titleIcon = new ImageView(this);
        int is = dp(T.softkeys ? 26 : 18);
        t.addView(titleIcon, is, is);
        titleText = tv("", T.titleText, T.softkeys ? 19 : 14);
        if (T.titleBold) titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleText.setSingleLine(true);
        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titleText.setPadding(dp(7), 0, dp(4), 0);
        titleText.setShadowLayer(T.lightTitle ? 6 : 1.5f, T.lightTitle ? 0 : 1, T.lightTitle ? 0 : 1, T.titleShadow);
        t.addView(titleText, T.softkeys ? lp(-2, -2, 0) : lp(0, -2, 1));
        if (T.winButtons) {
            t.addView(winBtn("—", T.minBtn, false, () -> moveTaskToBack(true)));
            t.addView(winBtn("□", T.minBtn, false, this::toggleFullscreen));
            t.addView(winBtn("✕", T.closeBtn, true, this::finish));
        }
        return t;
    }

    View winBtn(String glyph, int[] colors, boolean close, final Runnable r) {
        TextView b = tv(glyph, 0xFFFFFFFF, close ? 13 : 11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setShadowLayer(1.5f, 0, 0, 0xAA000000);
        GradientDrawable g = grad(colors, dp(3));
        g.setStroke(dp(1), T.winBorder);
        b.setBackground(g);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams l = lp(dp(T.xpTasks ? 24 : (close ? 42 : 27)), dp(T.xpTasks ? 22 : 19), 0);
        l.leftMargin = dp(T.xpTasks ? 2 : 0);
        if (!T.xpTasks) l.gravity = Gravity.TOP;
        b.setLayoutParams(l);
        return b;
    }

    void toggleFullscreen() {
        fullscreen = !fullscreen;
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(fullscreen
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                : (T.lightTitle && Build.VERSION.SDK_INT >= 23 ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0));
    }

    ImageView navBtn(String dir, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new NavDrawable(dir, T.navBtn, true));
        iv.setOnClickListener(l);
        int s = dp(34);
        LinearLayout.LayoutParams p = lp(s, s, 0);
        p.rightMargin = dp(4);
        iv.setLayoutParams(p);
        return iv;
    }

    void setNavEnabled(ImageView iv, String dir, boolean en) {
        iv.setEnabled(en);
        iv.setImageDrawable(new NavDrawable(dir, T.navBtn, en));
    }

    View buildNav() {
        LinearLayout n = hl();
        n.setGravity(Gravity.CENTER_VERTICAL);
        n.setBackground(grad(T.nav, 0));
        n.setPadding(dp(6), dp(5), dp(6), dp(5));
        backBtn = navBtn("back", v -> goBack());
        fwdBtn = navBtn("fwd", v -> goFwd());
        upBtn = navBtn("up", v -> goUp());
        n.addView(backBtn);
        n.addView(fwdBtn);
        n.addView(upBtn);
        if (T.xpTasks) {
            TextView l = tv("Address", T.navText, 12);
            l.setPadding(dp(2), 0, dp(6), 0);
            n.addView(l);
        }
        FrameLayout addr = new FrameLayout(this);
        GradientDrawable ab = solid(T.addrBg, dp(T.addrRadius));
        ab.setStroke(dp(1), T.addrBorder);
        addr.setBackground(ab);
        crumbScroll = new HorizontalScrollView(this);
        crumbScroll.setHorizontalScrollBarEnabled(false);
        crumbs = hl();
        crumbs.setGravity(Gravity.CENTER_VERTICAL);
        crumbs.setPadding(dp(6), 0, dp(6), 0);
        crumbScroll.addView(crumbs, new FrameLayout.LayoutParams(-2, -1));
        addr.addView(crumbScroll, new FrameLayout.LayoutParams(-1, -1));
        addr.setOnClickListener(v -> editAddress());
        n.addView(addr, lp(0, dp(34), 1));
        if (T.xpTasks) {
            TextView go = tv("➡ Go", 0xFF2E7D32, 13);
            go.setTypeface(Typeface.DEFAULT_BOLD);
            go.setPadding(dp(8), dp(6), dp(4), dp(6));
            go.setOnClickListener(v -> editAddress());
            n.addView(go);
        }
        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Search");
        searchBox.setHintTextColor(T.addrHint);
        searchBox.setTextColor(T.addrText);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchBox.setPadding(dp(10), 0, dp(8), 0);
        GradientDrawable sb = solid(T.addrBg, dp(T.addrRadius));
        sb.setStroke(dp(1), T.addrBorder);
        searchBox.setBackground(sb);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (suppressSearch) return;
                ui.removeCallbacks(searchRun);
                ui.postDelayed(searchRun, 450);
            }
        });
        LinearLayout.LayoutParams sp = lp(dp(wide() ? 170 : 104), dp(34), 0);
        sp.leftMargin = dp(6);
        n.addView(searchBox, sp);
        return n;
    }

    final Runnable searchRun = new Runnable() {
        @Override
        public void run() {
            String q = searchBox.getText().toString().trim();
            if (q.isEmpty()) {
                if (loc.type == LOC_SEARCH) navigate(new Loc(LOC_DIR, loc.dir, null), true);
                return;
            }
            File base = loc.type == LOC_BIN ? root : loc.dir;
            if (loc.type == LOC_SEARCH) {
                loc = new Loc(LOC_SEARCH, base, q);
                sel.clear();
                refresh();
            } else {
                navigate(new Loc(LOC_SEARCH, base, q), true);
            }
        }
    };

    void setSearchText(String s) {
        if (searchBox == null) return;
        suppressSearch = true;
        searchBox.setText(s == null ? "" : s);
        suppressSearch = false;
    }

    View buildCmd() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackground(grad(T.cmd, 0));
        cmdRow = hl();
        cmdRow.setGravity(Gravity.CENTER_VERTICAL);
        cmdRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        hs.addView(cmdRow);
        addTb("places", T.xpTasks ? "places" : "menu", T.xpTasks ? "Folders" : "Places", v -> toggleSide());
        addTb("newfolder", "newfolder", "New folder", v -> newFolder());
        addTb("cut", "cut", "Cut", v -> setClip(true));
        addTb("copy", "copy", "Copy", v -> setClip(false));
        addTb("paste", "paste", "Paste", v -> { if (loc.type == LOC_DIR) paste(loc.dir); });
        addTb("delete", "delete", "Delete", v -> deleteSel(false));
        addTb("rename", "rename", "Rename", v -> renameSel());
        addTb("restore", "restore", "Restore", v -> restoreSel());
        addTb("empty", "empty", "Empty Recycle Bin", v -> emptyBin());
        addTb("props", "props", "Properties", v -> showProps(selItems()));
        addTb("view", "view", "Views", this::showViewMenu);
        addTb("sort", "sort", "Sort", this::showSortMenu);
        addTb("theme", "theme", "Theme", this::showThemeMenu);
        addTb("more", "more", "More", this::showMainMenu);
        return hs;
    }

    void addTb(String key, String glyph, String label, View.OnClickListener l) {
        LinearLayout b = hl();
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(10), dp(7), dp(12), dp(7));
        ImageView gi = new ImageView(this);
        gi.setImageDrawable(new GlyphDrawable(glyph, T.cmdText));
        b.addView(gi, dp(18), dp(18));
        TextView t = tv(label, T.cmdText, 13);
        t.setPadding(dp(6), 0, 0, 0);
        b.addView(t);
        b.setBackground(pressable(T.tb != null ? grad(T.tb, dp(T.tbRadius)) : null, T.tbPressed, dp(T.tbRadius)));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = lp(-2, -2, 0);
        p.rightMargin = dp(T.tb != null ? 6 : 2);
        cmdRow.addView(b, p);
        cmdBtns.put(key, b);
    }

    View buildSoftkeys() {
        LinearLayout s = hl();
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setBackground(grad(new int[]{0xFF3B3B3B, 0xFF111111}, 0));
        final TextView opt = tv("Options", 0xFFFFFFFF, 16);
        opt.setTypeface(Typeface.DEFAULT_BOLD);
        opt.setPadding(dp(18), dp(12), dp(18), dp(12));
        opt.setOnClickListener(this::showMainMenu);
        s.addView(opt);
        skTitle = tv("", T.accent, 12);
        skTitle.setGravity(Gravity.CENTER);
        skTitle.setSingleLine(true);
        skTitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        s.addView(skTitle, lp(0, -2, 1));
        TextView back = tv("Back", 0xFFFFFFFF, 16);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setPadding(dp(18), dp(12), dp(18), dp(12));
        back.setOnClickListener(v -> onBackPressed());
        s.addView(back);
        return s;
    }

    View buildBody() {
        FrameLayout body = new FrameLayout(this);
        LinearLayout col = vl();
        col.setBackgroundColor(T.content);
        headerRow = hl();
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setBackground(grad(T.header, 0));
        col.addView(headerRow, lp(-1, dp(30), 0));

        FrameLayout gw = new FrameLayout(this);
        grid = new GridView(this);
        grid.setSelector(new ColorDrawable(0));
        grid.setPadding(dp(6), dp(6), dp(6), dp(6));
        grid.setClipToPadding(false);
        grid.setVerticalSpacing(dp(3));
        grid.setHorizontalSpacing(dp(3));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setFastScrollEnabled(true);
        adapter = new ItemAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, v, pos, id) -> onItemTap(pos));
        grid.setOnItemLongClickListener((parent, v, pos, id) -> {
            onItemLong(pos, v);
            return true;
        });
        gw.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        emptyText = tv("", T.dimText, 14);
        emptyText.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyText.setPadding(dp(16), dp(48), dp(16), 0);
        gw.addView(emptyText, new FrameLayout.LayoutParams(-1, -2));
        col.addView(gw, lp(-1, 0, 1));
        body.addView(col, new FrameLayout.LayoutParams(-1, -1));

        sideOverlay = new FrameLayout(this);
        View scrim = new View(this);
        scrim.setBackgroundColor(0x66000000);
        scrim.setOnClickListener(v -> hideSide());
        sideOverlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setBackground(grad(T.side, 0));
        if (Build.VERSION.SDK_INT >= 21) sv.setElevation(dp(10));
        sideList = vl();
        sideList.setPadding(0, dp(4), 0, dp(20));
        sv.addView(sideList);
        sideOverlay.addView(sv, new FrameLayout.LayoutParams(dp(285), -1, Gravity.START));
        body.addView(sideOverlay, new FrameLayout.LayoutParams(-1, -1));

        permView = buildPermView();
        body.addView(permView, new FrameLayout.LayoutParams(-1, -1));
        permView.setVisibility(hasPerm() ? View.GONE : View.VISIBLE);
        return body;
    }

    View buildPermView() {
        LinearLayout p = vl();
        p.setGravity(Gravity.CENTER);
        p.setBackgroundColor(T.content);
        p.setPadding(dp(32), dp(32), dp(32), dp(32));
        p.setClickable(true);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(new IconDrawable(FileOps.K_DRIVE, T.id, ""));
        p.addView(ic, dp(80), dp(80));
        TextView h = tv("Allow access to your files", T.contentText, 20);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, dp(14), 0, dp(8));
        p.addView(h);
        TextView m = tv("Explorer needs permission to show and manage the files on this device.\n\nOn the next screen, turn on “Allow access to manage all files”, then come back.",
                T.dimText, 14);
        m.setGravity(Gravity.CENTER);
        p.addView(m);
        Button b = new Button(this);
        b.setText("Grant access");
        b.setOnClickListener(v -> requestPerm());
        LinearLayout.LayoutParams bl = lp(-2, -2, 0);
        bl.topMargin = dp(20);
        p.addView(b, bl);
        return p;
    }

    void applyViewMode() {
        switch (viewMode) {
            case V_TILES:
                grid.setNumColumns(GridView.AUTO_FIT);
                grid.setColumnWidth(dp(280));
                break;
            case V_LIST:
                grid.setNumColumns(GridView.AUTO_FIT);
                grid.setColumnWidth(dp(260));
                break;
            case V_DETAILS:
                grid.setNumColumns(1);
                break;
            default:
                grid.setNumColumns(GridView.AUTO_FIT);
                grid.setColumnWidth(dp(T.softkeys ? 96 : 90));
        }
        grid.setVerticalSpacing(dp(V_DETAILS.equals(viewMode) ? 0 : 3));
        headerRow.setVisibility(V_DETAILS.equals(viewMode) ? View.VISIBLE : View.GONE);
        renderHeader();
        adapter.notifyDataSetChanged();
    }

    // ================================================================ data loading
    void refresh() {
        if (!hasPerm()) { showPermPanel(); return; }
        hidePermPanel();
        final int tok = ++loadToken;
        final Loc l = loc;
        final boolean hid = showHidden;
        io.execute(() -> {
            final List<Item> res = new ArrayList<>();
            String err = null;
            try {
                if (l.type == LOC_DIR) {
                    File[] fs = l.dir.listFiles();
                    if (fs == null) throw new IOException("“" + l.dir.getName() + "” can't be opened. You may not have permission to view this folder.");
                    for (File f : fs) {
                        if (!hid && f.getName().startsWith(".")) continue;
                        res.add(new Item(f));
                    }
                } else if (l.type == LOC_BIN) {
                    for (RecycleBin.Entry e : bin.list()) res.add(new Item(e, bin.fileOf(e)));
                } else {
                    for (File f : FileOps.search(l.dir, l.q, hid, 2000)) res.add(new Item(f));
                }
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String fe = err;
            ui.post(() -> {
                if (tok != loadToken) return;
                if (fe != null) {
                    toast(fe);
                    if (!backStack.isEmpty()) { loc = backStack.pop(); refresh(); }
                    return;
                }
                items.clear();
                items.addAll(res);
                sortItems();
                Set<String> keep = new HashSet<>();
                for (Item i : items) keep.add(i.path());
                sel.retainAll(keep);
                renderAll();
            });
        });
    }

    void sortItems() {
        final int dir = sortAsc ? 1 : -1;
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
                int r = 0;
                switch (sortKey) {
                    case "date": r = Long.compare(a.mtime, b.mtime); break;
                    case "size": r = Long.compare(a.size, b.size); break;
                    case "type": r = FileOps.typeName(a.name, a.isDir).compareToIgnoreCase(FileOps.typeName(b.name, b.isDir)); break;
                    default: break;
                }
                if (r == 0) r = a.name.compareToIgnoreCase(b.name);
                return r * dir;
            }
        });
    }

    List<Item> selItems() {
        List<Item> out = new ArrayList<>();
        for (Item i : items) if (sel.contains(i.path())) out.add(i);
        return out;
    }

    // ================================================================ navigation
    void navigate(Loc l, boolean push) {
        if (push) {
            backStack.push(loc);
            while (backStack.size() > 100) backStack.removeLast();
            fwdStack.clear();
        }
        loc = l;
        sel.clear();
        selMode = false;
        if (l.type != LOC_SEARCH) setSearchText("");
        if (l.type == LOC_DIR) expandTo(l.dir);
        hideSide();
        grid.setSelection(0);
        refresh();
    }

    void openDir(File d) { navigate(new Loc(LOC_DIR, d, null), true); }

    void openBin() { navigate(new Loc(LOC_BIN, root, null), true); }

    boolean goBack() {
        if (backStack.isEmpty()) return false;
        fwdStack.push(loc);
        loc = backStack.pop();
        sel.clear();
        selMode = false;
        setSearchText(loc.type == LOC_SEARCH ? loc.q : "");
        refresh();
        return true;
    }

    boolean goFwd() {
        if (fwdStack.isEmpty()) return false;
        backStack.push(loc);
        loc = fwdStack.pop();
        sel.clear();
        selMode = false;
        setSearchText(loc.type == LOC_SEARCH ? loc.q : "");
        refresh();
        return true;
    }

    boolean goUp() {
        if (loc.type != LOC_DIR) {
            openDir(loc.dir != null ? loc.dir : root);
            return true;
        }
        File base = rootFor(loc.dir);
        if (base != null && base.equals(loc.dir)) return false;
        File p = loc.dir.getParentFile();
        if (p == null) return false;
        openDir(p);
        return true;
    }

    void expandTo(File d) {
        File c = d;
        while (c != null) {
            expanded.add(c.getAbsolutePath());
            if (c.equals(root)) break;
            File r = rootFor(c);
            if (r != null && r.equals(c)) break;
            c = c.getParentFile();
        }
    }

    void editAddress() {
        String cur = loc.type == LOC_BIN ? "Recycle Bin" : loc.dir.getAbsolutePath();
        prompt("Go to", cur, false, "Go", v -> {
            if (v.isEmpty()) return "Type a location.";
            if (v.equalsIgnoreCase("recycle bin")) { openBin(); return null; }
            File f = v.startsWith("/") ? new File(v) : new File(loc.dir, v);
            if (f.isDirectory()) { openDir(f); return null; }
            if (f.isFile()) {
                File parent = f.getParentFile();
                if (parent != null) openDir(parent);
                openItem(new Item(f));
                return null;
            }
            return "Explorer can't find this location. Check the spelling and try again.";
        });
    }

    // ================================================================ rendering
    String locTitle() {
        if (loc.type == LOC_BIN) return "Recycle Bin";
        if (loc.type == LOC_SEARCH) return "Search results for “" + loc.q + "”";
        for (File r : storageRoots()) if (r.equals(loc.dir)) return rootLabel(r);
        return loc.dir.getName();
    }

    int locKind() {
        if (loc.type == LOC_BIN) return FileOps.K_BIN;
        if (loc.type == LOC_SEARCH) return FileOps.K_STAR;
        if (loc.dir.equals(root)) return FileOps.K_DRIVE;
        File r = rootFor(loc.dir);
        if (r != null && r.equals(loc.dir)) return FileOps.K_SD;
        return FileOps.K_FOLDER;
    }

    void renderAll() {
        if (grid == null) return;
        adapter.notifyDataSetChanged();
        emptyText.setText(items.isEmpty() && hasPerm()
                ? (loc.type == LOC_SEARCH ? "No items match your search." : loc.type == LOC_BIN ? "The Recycle Bin is empty." : "This folder is empty.")
                : "");
        titleText.setText(T.softkeys ? "Files" : locTitle());
        titleIcon.setImageDrawable(new IconDrawable(T.softkeys ? FileOps.K_FOLDER : locKind(), T.id, ""));
        if (skTitle != null) skTitle.setText(locTitle());
        setNavEnabled(backBtn, "back", !backStack.isEmpty());
        setNavEnabled(fwdBtn, "fwd", !fwdStack.isEmpty());
        File base = loc.type == LOC_DIR ? rootFor(loc.dir) : null;
        setNavEnabled(upBtn, "up", !(loc.type == LOC_DIR && base != null && base.equals(loc.dir)));
        renderCrumbs();
        updateCmd();
        updateStatus();
        updateDetails();
        renderHeader();
        if (sideOpen) renderSide();
    }

    void onSelectionChanged() {
        adapter.notifyDataSetChanged();
        updateCmd();
        updateStatus();
        updateDetails();
        if (sideOpen && T.xpTasks) renderSide();
    }

    void renderCrumbs() {
        crumbs.removeAllViews();
        if (loc.type == LOC_BIN) {
            crumbs.addView(crumb("Recycle Bin", null));
        } else {
            File base = rootFor(loc.dir);
            if (base == null) base = new File("/");
            List<File> chain = new ArrayList<>();
            File c = loc.dir;
            while (c != null && !c.equals(base)) {
                chain.add(0, c);
                c = c.getParentFile();
            }
            if (T.xpTasks) {
                StringBuilder sb = new StringBuilder(base.equals(root) ? "Internal storage" : base.getAbsolutePath());
                for (File f : chain) sb.append("\\").append(f.getName());
                crumbs.addView(crumb(sb.toString(), null));
            } else {
                String sep = T.softkeys ? " / " : " ▸ ";
                String baseLabel = base.equals(root) ? "Internal storage" : storageRoots().contains(base) ? rootLabel(base) : base.getAbsolutePath();
                crumbs.addView(crumb(baseLabel, base));
                for (File f : chain) {
                    crumbs.addView(sepView(sep));
                    crumbs.addView(crumb(f.getName(), f));
                }
            }
            if (loc.type == LOC_SEARCH) {
                crumbs.addView(sepView(T.softkeys ? " / " : " ▸ "));
                crumbs.addView(crumb("Search “" + loc.q + "”", null));
            }
        }
        crumbScroll.post(() -> crumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    TextView crumb(String text, final File target) {
        TextView t = tv(text, T.addrText, 13);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(4), dp(6), dp(4), dp(6));
        if (target != null) {
            t.setBackground(pressable(null, T.dark ? 0x40FF8A00 : 0xFFD7EFFC, dp(2)));
            t.setOnClickListener(v -> openDir(target));
        } else {
            t.setOnClickListener(v -> editAddress());
        }
        return t;
    }

    TextView sepView(String s) {
        TextView t = tv(s, T.dimText, 11);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    void setEn(String key, boolean en) {
        View v = cmdBtns.get(key);
        if (v == null) return;
        v.setEnabled(en);
        v.setAlpha(en ? 1f : 0.4f);
    }

    void setVis(String key, boolean vis) {
        View v = cmdBtns.get(key);
        if (v != null) v.setVisibility(vis ? View.VISIBLE : View.GONE);
    }

    void updateCmd() {
        boolean inBin = loc.type == LOC_BIN, inDir = loc.type == LOC_DIR;
        int n = sel.size();
        for (String k : new String[]{"newfolder", "cut", "copy", "paste", "rename"}) setVis(k, !inBin);
        setVis("restore", inBin);
        setVis("empty", inBin);
        setEn("newfolder", inDir);
        setEn("cut", n > 0);
        setEn("copy", n > 0);
        setEn("paste", !clip.isEmpty() && inDir);
        setEn("delete", n > 0);
        setEn("rename", n == 1);
        setEn("restore", n > 0);
        setEn("empty", inBin && !items.isEmpty());
    }

    void updateStatus() {
        List<Item> s = selItems();
        StringBuilder b = new StringBuilder();
        if (!s.isEmpty()) {
            long sz = 0;
            for (Item i : s) if (!i.isDir) sz += i.size;
            b.append(s.size()).append(s.size() == 1 ? " item selected" : " items selected");
            if (sz > 0) b.append("   ").append(FileOps.formatSize(sz));
        } else {
            b.append(items.size()).append(items.size() == 1 ? " item" : " items");
            if (selMode) b.append("   •   Tap items to select");
        }
        if (!clip.isEmpty()) b.append("   •   ").append(clip.size()).append(clipCut ? " cut" : " copied");
        try {
            File r = loc.dir != null ? rootFor(loc.dir) : root;
            StatFs st = new StatFs((r != null ? r : root).getPath());
            b.append("   •   Free: ").append(FileOps.formatSize(st.getAvailableBytes())).append(" of ").append(FileOps.formatSize(st.getTotalBytes()));
        } catch (Exception ignored) { }
        statusText.setText(b.toString());
    }

    void updateDetails() {
        if (!T.detailsPane) return;
        detailsPane.removeAllViews();
        List<Item> s = selItems();
        ImageView ic = new ImageView(this);
        LinearLayout tx = vl();
        tx.setPadding(dp(12), 0, 0, 0);
        TextView name = tv("", T.detailsText, 15);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        TextView meta = tv("", T.detailsText, 12);
        meta.setAlpha(0.75f);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        if (s.size() == 1) {
            Item it = s.get(0);
            bindIcon(ic, it, true);
            name.setText(it.name);
            String m = FileOps.typeName(it.name, it.isDir);
            if (!it.isDir) m += "     Size: " + FileOps.formatSize(it.size);
            m += "     " + (it.bin != null ? "Deleted: " : "Modified: ") + FileOps.formatDate(it.mtime);
            meta.setText(m);
        } else if (s.size() > 1) {
            ic.setImageDrawable(new IconDrawable(FileOps.K_TEXT, T.id, ""));
            long sz = 0;
            for (Item i : s) if (!i.isDir) sz += i.size;
            name.setText(s.size() + " items selected");
            meta.setText("Size: " + FileOps.formatSize(sz));
        } else {
            ic.setImageDrawable(new IconDrawable(locKind(), T.id, ""));
            name.setText(locTitle());
            meta.setText(items.size() + " items");
        }
        detailsPane.addView(ic, dp(44), dp(44));
        tx.addView(name);
        tx.addView(meta);
        detailsPane.addView(tx, lp(0, -2, 1));
    }

    void renderHeader() {
        if (headerRow == null) return;
        headerRow.removeAllViews();
        headerRow.setPadding(dp(38), 0, dp(12), 0);
        headerRow.addView(headerCell("Name", "name"), lp(0, -1, 1));
        if (wide()) headerRow.addView(headerCell("Date modified", "date"), lp(dp(140), -1, 0));
        headerRow.addView(headerCell("Size", "size"), lp(dp(80), -1, 0));
    }

    TextView headerCell(String label, final String key) {
        String arrow = sortKey.equals(key) ? (sortAsc ? "  ▲" : "  ▼") : "";
        TextView t = tv(label + arrow, T.headerText, 12);
        t.setGravity(Gravity.CENTER_VERTICAL | ("size".equals(key) ? Gravity.END : Gravity.START));
        t.setPadding(dp(4), 0, dp(4), 0);
        t.setOnClickListener(v -> {
            if (sortKey.equals(key)) sortAsc = !sortAsc;
            else { sortKey = key; sortAsc = true; }
            prefs.edit().putString("sort", sortKey).putBoolean("asc", sortAsc).apply();
            sortItems();
            renderAll();
        });
        return t;
    }

    // ================================================================ side panel (Places / XP task pane)
    void toggleSide() { if (sideOpen) hideSide(); else showSide(); }

    void showSide() {
        sideOpen = true;
        renderSide();
        sideOverlay.setVisibility(View.VISIBLE);
    }

    void hideSide() {
        sideOpen = false;
        if (sideOverlay != null) sideOverlay.setVisibility(View.GONE);
    }

    boolean isCur(File d) { return loc.type == LOC_DIR && loc.dir.equals(d); }

    void renderSide() {
        sideList.removeAllViews();
        if (T.xpTasks) { renderXpTasks(); return; }
        sideHeader(T.id.equals("vista") ? "Favorite Links" : "Quick access");
        for (String p : pins) {
            final File f = new File(p);
            if (!f.isDirectory()) continue;
            sideRow(0, FileOps.K_FOLDER, f.getName(), isCur(f), v -> openDir(f), null, null, v -> {
                togglePin(f);
                return true;
            });
        }
        sideHeader("This device");
        for (File r : storageRoots()) addTree(r, 0, rootLabel(r), r.equals(root) ? FileOps.K_DRIVE : FileOps.K_SD);
        sideHeader(" ");
        boolean full = !bin.list().isEmpty();
        sideRow(0, full ? FileOps.K_BINFULL : FileOps.K_BIN, "Recycle Bin", loc.type == LOC_BIN, v -> openBin(), null, null, null);
    }

    void addTree(final File d, int depth, String label, int kind) {
        final String p = d.getAbsolutePath();
        boolean open = expanded.contains(p);
        File[] kids = open ? d.listFiles() : null;
        List<File> dirs = new ArrayList<>();
        if (kids != null) {
            for (File k : kids) if (k.isDirectory() && (showHidden || !k.getName().startsWith("."))) dirs.add(k);
            Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }
        String tw = open ? (dirs.isEmpty() ? " " : "◢") : "▷";
        sideRow(depth, kind, label, isCur(d), v -> openDir(d), tw, v -> {
            if (expanded.contains(p)) expanded.remove(p); else expanded.add(p);
            renderSide();
        }, v -> {
            togglePin(d);
            return true;
        });
        if (open) for (File k : dirs) addTree(k, depth + 1, k.getName(), FileOps.K_FOLDER);
    }

    void sideHeader(String s) {
        TextView h = tv(T.softkeys ? s.toUpperCase(Locale.ROOT) : s, T.sideHeader, T.softkeys ? 11 : 13);
        if (T.softkeys || T.xpTasks) h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(dp(12), dp(12), dp(12), dp(4));
        sideList.addView(h);
    }

    void sideRow(int depth, int kind, String label, boolean current, View.OnClickListener click, String twisty,
                 View.OnClickListener onTwisty, View.OnLongClickListener longClick) {
        LinearLayout r = hl();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(4 + depth * 14), dp(8), dp(8), dp(8));
        TextView tw = tv(twisty == null ? "" : twisty, T.dimText, 10);
        tw.setGravity(Gravity.CENTER);
        if (onTwisty != null) tw.setOnClickListener(onTwisty);
        r.addView(tw, dp(24), dp(24));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(new IconDrawable(kind, T.id, ""));
        r.addView(ic, dp(20), dp(20));
        TextView t = tv(label, current ? T.sideSelText : T.sideText, 14);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(8), 0, 0, 0);
        r.addView(t, lp(0, -2, 1));
        if (current) {
            GradientDrawable g = solid(T.sideSel, dp(T.itemRadius > 6 ? 8 : 2));
            g.setStroke(dp(1), T.sideSelBorder);
            r.setBackground(g);
        } else {
            r.setBackground(pressable(null, T.dark ? 0x22FFFFFF : 0x22316AC5, 0));
        }
        r.setOnClickListener(click);
        if (longClick != null) r.setOnLongClickListener(longClick);
        LinearLayout.LayoutParams l = lp(-1, -2, 0);
        l.leftMargin = dp(4);
        l.rightMargin = dp(4);
        sideList.addView(r, l);
    }

    void renderXpTasks() {
        List<Item> s = selItems();
        final Item one = s.size() == 1 ? s.get(0) : null;
        List<View> tasks = new ArrayList<>();
        String what = one != null ? (one.isDir ? "folder" : "file") : "";
        if (loc.type == LOC_BIN) {
            tasks.add(xpLink("empty", "Empty the Recycle Bin", v -> emptyBin()));
            if (!s.isEmpty()) tasks.add(xpLink("restore", s.size() > 1 ? "Restore the selected items" : "Restore this item", v -> restoreSel()));
        } else if (one != null) {
            tasks.add(xpLink("rename", "Rename this " + what, v -> renameSel()));
            tasks.add(xpLink("cut", "Move this " + what, v -> moveSelTo()));
            tasks.add(xpLink("copy", "Copy this " + what, v -> copySelTo()));
            if (!one.isDir) tasks.add(xpLink("more", "Share this file", v -> share(selItems())));
            if (one.isDir) tasks.add(xpLink("up", pins.contains(one.path()) ? "Unpin from Quick access" : "Pin to Quick access", v -> togglePin(one.file)));
            tasks.add(xpLink("delete", "Delete this " + what, v -> deleteSel(false)));
        } else if (!s.isEmpty()) {
            tasks.add(xpLink("cut", "Move the selected items", v -> moveSelTo()));
            tasks.add(xpLink("copy", "Copy the selected items", v -> copySelTo()));
            tasks.add(xpLink("more", "Share the selected items", v -> share(selItems())));
            tasks.add(xpLink("delete", "Delete the selected items", v -> deleteSel(false)));
        } else {
            tasks.add(xpLink("newfolder", "Make a new folder", v -> newFolder()));
            tasks.add(xpLink("newfile", "Create a text document", v -> newFile()));
            if (!clip.isEmpty() && loc.type == LOC_DIR) tasks.add(xpLink("paste", "Paste " + clip.size() + " item(s) here", v -> paste(loc.dir)));
            tasks.add(xpLink("view", "Select items", v -> { hideSide(); enterSelectMode(); }));
        }
        xpBox(loc.type == LOC_BIN ? "Recycle Bin Tasks" : "File and Folder Tasks", tasks);

        List<View> places = new ArrayList<>();
        if (loc.type == LOC_DIR) {
            File base = rootFor(loc.dir);
            final File parent = loc.dir.getParentFile();
            if (parent != null && (base == null || !base.equals(loc.dir))) places.add(xpPlace(FileOps.K_FOLDER, parent.equals(root) ? "Internal storage" : parent.getName(), v -> openDir(parent)));
        }
        for (final File r : storageRoots()) if (!isCur(r)) places.add(xpPlace(r.equals(root) ? FileOps.K_DRIVE : FileOps.K_SD, rootLabel(r), v -> openDir(r)));
        int n = 0;
        for (String p : pins) {
            final File f = new File(p);
            if (!f.isDirectory() || isCur(f) || n >= 6) continue;
            places.add(xpPlace(FileOps.K_FOLDER, f.getName(), v -> openDir(f)));
            n++;
        }
        if (loc.type != LOC_BIN) places.add(xpPlace(FileOps.K_BIN, "Recycle Bin", v -> openBin()));
        xpBox("Other Places", places);

        List<View> det = new ArrayList<>();
        TextView d = tv("", 0xFF000000, 13);
        if (one != null) {
            d.setText(one.name + "\n" + FileOps.typeName(one.name, one.isDir) + (one.isDir ? "" : "\nSize: " + FileOps.formatSize(one.size))
                    + "\n" + (one.bin != null ? "Deleted: " : "Modified: ") + FileOps.formatDate(one.mtime));
        } else if (!s.isEmpty()) {
            d.setText(s.size() + " items selected.");
        } else {
            d.setText(locTitle() + "\n" + (loc.type == LOC_BIN ? "Recycle Bin" : "File Folder") + "\n" + items.size() + " items");
        }
        det.add(d);
        xpBox("Details", det);
    }

    TextView xpLink(String glyph, String text, View.OnClickListener l) {
        TextView t = tv(text, 0xFF215DC6, 14);
        GlyphDrawable g = new GlyphDrawable(glyph, 0xFF215DC6);
        g.setBounds(0, 0, dp(16), dp(16));
        t.setCompoundDrawables(g, null, null, null);
        t.setCompoundDrawablePadding(dp(8));
        t.setPadding(0, dp(6), 0, dp(6));
        t.setOnClickListener(l);
        return t;
    }

    TextView xpPlace(int kind, String text, View.OnClickListener l) {
        TextView t = tv(text, 0xFF215DC6, 14);
        IconDrawable g = new IconDrawable(kind, T.id, "");
        g.setBounds(0, 0, dp(18), dp(18));
        t.setCompoundDrawables(g, null, null, null);
        t.setCompoundDrawablePadding(dp(8));
        t.setPadding(0, dp(6), 0, dp(6));
        t.setOnClickListener(l);
        return t;
    }

    void xpBox(String title, List<View> rows) {
        LinearLayout box = vl();
        LinearLayout.LayoutParams bl = lp(-1, -2, 0);
        bl.setMargins(dp(10), dp(12), dp(10), 0);
        TextView h = tv(title, 0xFF215DC6, 13);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFFFFF, 0xFFC6D3F7});
        float r = dp(4);
        hg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        h.setBackground(hg);
        box.addView(h);
        LinearLayout b = vl();
        b.setPadding(dp(10), dp(6), dp(10), dp(8));
        GradientDrawable bg = solid(0xFFD6DFF7, 0);
        bg.setStroke(dp(1), 0xFFFFFFFF);
        b.setBackground(bg);
        for (View v : rows) b.addView(v);
        box.addView(b);
        sideList.addView(box, bl);
    }

    // ================================================================ item interaction
    void onItemTap(int pos) {
        if (pos < 0 || pos >= items.size()) return;
        Item it = items.get(pos);
        if (selMode) {
            if (!sel.remove(it.path())) sel.add(it.path());
            onSelectionChanged();
            return;
        }
        openItem(it);
    }

    void onItemLong(int pos, View v) {
        if (pos < 0 || pos >= items.size()) return;
        Item it = items.get(pos);
        if (!sel.contains(it.path())) {
            if (!selMode) sel.clear();
            sel.add(it.path());
        }
        selMode = true;
        onSelectionChanged();
        showItemMenu(v);
    }

    void enterSelectMode() {
        selMode = true;
        updateStatus();
        toast("Tap items to select them");
    }

    void clearSelection() {
        sel.clear();
        selMode = false;
        onSelectionChanged();
    }

    void selectAll() {
        for (Item i : items) sel.add(i.path());
        selMode = true;
        onSelectionChanged();
    }

    void invertSel() {
        Set<String> n = new HashSet<>();
        for (Item i : items) if (!sel.contains(i.path())) n.add(i.path());
        sel.clear();
        sel.addAll(n);
        selMode = true;
        onSelectionChanged();
    }

    void openItem(Item it) {
        if (it.bin != null) { showProps(Collections.singletonList(it)); return; }
        if (it.isDir) { openDir(it.file); return; }
        int k = it.kind();
        if ((k == FileOps.K_TEXT || k == FileOps.K_CODE) && it.size < 5L * 1024 * 1024) { openEditor(it.file); return; }
        if (k == FileOps.K_IMAGE && !"svg".equals(FileOps.ext(it.name))) { openImage(it); return; }
        openExternal(it.file, false);
    }

    void openExternal(File f, boolean chooser) {
        String mime = FileOps.mimeOf(f.getName());
        if ("application/vnd.android.package-archive".equals(mime) && Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {
            toast("Allow Explorer to install apps, then tap the APK again.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(FileShareProvider.uriFor(f), mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(chooser ? Intent.createChooser(i, "Open with") : i);
        } catch (ActivityNotFoundException e) {
            try {
                Intent any = new Intent(Intent.ACTION_VIEW);
                any.setDataAndType(FileShareProvider.uriFor(f), "*/*");
                any.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(any, "Open with"));
            } catch (Exception e2) {
                toast("No app found to open this file.");
            }
        }
    }

    void share(List<Item> s) {
        ArrayList<Uri> uris = new ArrayList<>();
        String type = null;
        for (Item it : s) {
            if (it.isDir || it.bin != null) continue;
            uris.add(FileShareProvider.uriFor(it.file));
            String m = FileOps.mimeOf(it.name);
            type = type == null ? m : (type.equals(m) ? type : "*/*");
        }
        if (uris.isEmpty()) { toast("Select one or more files to share (folders can't be shared)."); return; }
        Intent i;
        if (uris.size() == 1) {
            i = new Intent(Intent.ACTION_SEND);
            i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        i.setType(type);
        ClipData cd = ClipData.newRawUri("", uris.get(0));
        for (int k = 1; k < uris.size(); k++) cd.addItem(new ClipData.Item(uris.get(k)));
        i.setClipData(cd);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(i, "Share")); }
        catch (Exception e) { toast("No app available to share with."); }
    }

    // ================================================================ menus
    void add(Menu m, int id, String title) { m.add(Menu.NONE, id, m.size(), title); }

    void showItemMenu(View anchor) {
        final List<Item> s = selItems();
        if (s.isEmpty()) return;
        final Item one = s.size() == 1 ? s.get(0) : null;
        PopupMenu pm = new PopupMenu(popupCtx(), anchor);
        Menu m = pm.getMenu();
        if (loc.type == LOC_BIN) {
            add(m, M_RESTORE, "Restore");
            add(m, M_DELPERM, "Delete permanently");
            add(m, M_SELALL, "Select all");
            add(m, M_PROPS, "Properties");
        } else {
            boolean anyFile = false;
            for (Item i : s) if (!i.isDir) anyFile = true;
            if (one != null) add(m, M_OPEN, "Open");
            if (one != null && !one.isDir) add(m, M_OPENWITH, "Open with…");
            if (loc.type == LOC_SEARCH && one != null) add(m, M_LOCATION, "Open file location");
            if (one != null && one.isDir) add(m, M_PIN, pins.contains(one.path()) ? "Unpin from Quick access" : "Pin to Quick access");
            if (one != null && one.isDir && !clip.isEmpty()) add(m, M_PASTE_INTO, "Paste into folder");
            if (anyFile) add(m, M_SHARE, "Share…");
            add(m, M_CUT, "Cut");
            add(m, M_COPY, "Copy");
            add(m, M_COPYTO, "Copy to folder…");
            add(m, M_MOVETO, "Move to folder…");
            if (one != null) add(m, M_RENAME, "Rename");
            add(m, M_DELETE, "Delete");
            add(m, M_DELPERM, "Delete permanently");
            add(m, M_SELALL, "Select all");
            add(m, M_PROPS, "Properties");
        }
        pm.setOnMenuItemClickListener(mi -> {
            onItemMenu(mi.getItemId(), s, one);
            return true;
        });
        pm.show();
    }

    void onItemMenu(int id, List<Item> s, Item one) {
        switch (id) {
            case M_OPEN: if (one != null) { clearSelection(); openItem(one); } break;
            case M_OPENWITH: if (one != null) openExternal(one.file, true); break;
            case M_LOCATION: if (one != null && one.file.getParentFile() != null) openDir(one.file.getParentFile()); break;
            case M_PIN: if (one != null) togglePin(one.file); break;
            case M_PASTE_INTO: if (one != null) paste(one.file); break;
            case M_SHARE: share(s); break;
            case M_CUT: setClip(true); break;
            case M_COPY: setClip(false); break;
            case M_COPYTO: copySelTo(); break;
            case M_MOVETO: moveSelTo(); break;
            case M_RENAME: renameSel(); break;
            case M_DELETE: deleteSel(false); break;
            case M_DELPERM: deleteSel(true); break;
            case M_SELALL: selectAll(); break;
            case M_PROPS: showProps(s); break;
            case M_RESTORE: restoreSel(); break;
            default: break;
        }
    }

    void showViewMenu(View anchor) {
        PopupMenu pm = new PopupMenu(popupCtx(), anchor);
        Menu m = pm.getMenu();
        String[][] v = {{V_LARGE, "Large icons"}, {V_TILES, "Tiles"}, {V_LIST, "List"}, {V_DETAILS, "Details"}};
        for (int i = 0; i < v.length; i++) {
            MenuItem mi = m.add(1, i, i, v[i][1]);
            mi.setCheckable(true);
            mi.setChecked(v[i][0].equals(viewMode));
        }
        m.setGroupCheckable(1, true, true);
        pm.setOnMenuItemClickListener(mi -> {
            viewMode = v[mi.getItemId()][0];
            prefs.edit().putString("view", viewMode).apply();
            applyViewMode();
            return true;
        });
        pm.show();
    }

    void showSortMenu(View anchor) {
        PopupMenu pm = new PopupMenu(popupCtx(), anchor);
        Menu m = pm.getMenu();
        final String[][] k = {{"name", "Name"}, {"date", "Date modified"}, {"type", "Type"}, {"size", "Size"}};
        for (int i = 0; i < k.length; i++) {
            MenuItem mi = m.add(1, i, i, k[i][1]);
            mi.setCheckable(true);
            mi.setChecked(k[i][0].equals(sortKey));
        }
        m.setGroupCheckable(1, true, true);
        MenuItem a = m.add(2, 10, 10, "Ascending");
        a.setCheckable(true);
        a.setChecked(sortAsc);
        MenuItem d = m.add(2, 11, 11, "Descending");
        d.setCheckable(true);
        d.setChecked(!sortAsc);
        m.setGroupCheckable(2, true, true);
        pm.setOnMenuItemClickListener(mi -> {
            int id = mi.getItemId();
            if (id == 10) sortAsc = true;
            else if (id == 11) sortAsc = false;
            else sortKey = k[id][0];
            prefs.edit().putString("sort", sortKey).putBoolean("asc", sortAsc).apply();
            sortItems();
            renderAll();
            return true;
        });
        pm.show();
    }

    void showThemeMenu(View anchor) {
        PopupMenu pm = new PopupMenu(popupCtx(), anchor);
        Menu m = pm.getMenu();
        for (int i = 0; i < Theme.ALL.length; i++) {
            MenuItem mi = m.add(1, i, i, Theme.ALL[i][1]);
            mi.setCheckable(true);
            mi.setChecked(Theme.ALL[i][0].equals(T.id));
        }
        m.setGroupCheckable(1, true, true);
        pm.setOnMenuItemClickListener(mi -> {
            applyTheme(Theme.ALL[mi.getItemId()][0]);
            return true;
        });
        pm.show();
    }

    void applyTheme(String id) {
        T = Theme.get(id);
        prefs.edit().putString("theme", id).apply();
        buildUi();
        renderAll();
    }

    void showMainMenu(View anchor) {
        PopupMenu pm = new PopupMenu(popupCtx(), anchor);
        Menu m = pm.getMenu();
        boolean inDir = loc.type == LOC_DIR;
        boolean hasSel = !sel.isEmpty();
        if (inDir) add(m, X_NEWFOLDER, "New folder");
        if (inDir) add(m, X_NEWFILE, "New text document");
        if (inDir && !clip.isEmpty()) add(m, X_PASTE, "Paste " + clip.size() + " item(s)");
        add(m, X_SELECT, "Select items");
        add(m, X_SELALL, "Select all");
        add(m, X_INVERT, "Invert selection");
        if (hasSel && loc.type != LOC_BIN) {
            add(m, X_COPYTO, "Copy to folder…");
            add(m, X_MOVETO, "Move to folder…");
            add(m, X_SHARE, "Share…");
        }
        add(m, X_PROPS, "Properties");
        if (inDir) add(m, X_PIN, pins.contains(loc.dir.getAbsolutePath()) ? "Unpin this folder" : "Pin this folder to Quick access");
        MenuItem h = m.add(Menu.NONE, X_HIDDEN, m.size(), "Show hidden files");
        h.setCheckable(true);
        h.setChecked(showHidden);
        if (T.softkeys) {
            add(m, X_VIEW, "Views…");
            add(m, X_SORT, "Sort by…");
            add(m, X_THEME, "Theme…");
        }
        add(m, X_PLACES, "Places panel");
        add(m, X_GOTO, "Go to location…");
        List<File> roots = storageRoots();
        for (int i = 0; i < roots.size(); i++) add(m, X_STORAGE + i, rootLabel(roots.get(i)));
        add(m, X_BIN, "Recycle Bin");
        add(m, X_EMPTY, "Empty Recycle Bin");
        add(m, X_REFRESH, "Refresh");
        add(m, X_ABOUT, "About");
        final View a = anchor;
        pm.setOnMenuItemClickListener(mi -> {
            onMainMenu(mi.getItemId(), a);
            return true;
        });
        pm.show();
    }

    void onMainMenu(int id, View anchor) {
        if (id >= X_STORAGE) {
            List<File> roots = storageRoots();
            int i = id - X_STORAGE;
            if (i < roots.size()) openDir(roots.get(i));
            return;
        }
        switch (id) {
            case X_NEWFOLDER: newFolder(); break;
            case X_NEWFILE: newFile(); break;
            case X_PASTE: paste(loc.dir); break;
            case X_SELECT: enterSelectMode(); break;
            case X_SELALL: selectAll(); break;
            case X_INVERT: invertSel(); break;
            case X_COPYTO: copySelTo(); break;
            case X_MOVETO: moveSelTo(); break;
            case X_SHARE: share(selItems()); break;
            case X_PROPS: showProps(selItems()); break;
            case X_PIN: togglePin(loc.dir); break;
            case X_HIDDEN:
                showHidden = !showHidden;
                prefs.edit().putBoolean("hidden", showHidden).apply();
                refresh();
                break;
            case X_VIEW: showViewMenu(anchor); break;
            case X_SORT: showSortMenu(anchor); break;
            case X_THEME: showThemeMenu(anchor); break;
            case X_PLACES: showSide(); break;
            case X_GOTO: editAddress(); break;
            case X_BIN: openBin(); break;
            case X_EMPTY: emptyBin(); break;
            case X_REFRESH: refresh(); break;
            case X_ABOUT:
                alert("About Explorer", "Retro Explorer 1.0\n\nA file manager with Windows XP, Vista, 7 and Snaptu-inspired themes.\n\n"
                        + "Tips:\n• Long-press an item to select it and see more actions\n• While selecting, tap items to add or remove them\n"
                        + "• Long-press a place in the Places panel to pin or unpin it\n• Back button: closes panels, clears selection, then goes back\n\n"
                        + "Fan-made tribute. Not affiliated with Microsoft or Snaptu.");
                break;
            default: break;
        }
    }

    // ================================================================ file operations
    void setClip(boolean cut) {
        List<Item> s = selItems();
        if (s.isEmpty() || loc.type == LOC_BIN) return;
        clip.clear();
        for (Item i : s) clip.add(i.file);
        clipCut = cut;
        toast(s.size() + (s.size() == 1 ? " item " : " items ") + (cut ? "cut" : "copied") + " — open a folder and tap Paste");
        clearSelection();
    }

    boolean clipHas(File f) {
        if (!clipCut) return false;
        for (File c : clip) if (c.equals(f)) return true;
        return false;
    }

    void paste(final File dest) {
        if (clip.isEmpty()) return;
        final List<File> src = new ArrayList<>(clip);
        final boolean cut = clipCut;
        if (cut) clip.clear();
        transfer(src, dest, cut);
    }

    void transfer(final List<File> src, final File dest, final boolean move) {
        runOp(move ? "Moving…" : "Copying…", errs -> {
            for (File f : src) {
                try {
                    if (f.isDirectory() && FileOps.isInside(dest, f)) {
                        errs.add("“" + f.getName() + "”: the destination folder is inside the source folder.");
                        continue;
                    }
                    File parent = f.getParentFile();
                    boolean same = parent != null && parent.equals(dest);
                    if (move) {
                        if (same) continue;
                        String n = FileOps.uniqueName(dest, f.getName(), false, f.isDirectory());
                        File to = new File(dest, n);
                        FileOps.move(f, to);
                        ui.post(() -> movePins(f, to));
                    } else {
                        String n = FileOps.uniqueName(dest, f.getName(), same, f.isDirectory());
                        FileOps.copy(f, new File(dest, n));
                    }
                } catch (Exception e) {
                    errs.add(f.getName() + ": " + e.getMessage());
                }
            }
        });
    }

    void copySelTo() {
        final List<Item> s = selItems();
        if (s.isEmpty()) return;
        pickFolder("Copy Items", "Copy here", d -> {
            List<File> fs = new ArrayList<>();
            for (Item i : s) fs.add(i.file);
            transfer(fs, d, false);
            clearSelection();
        });
    }

    void moveSelTo() {
        final List<Item> s = selItems();
        if (s.isEmpty()) return;
        pickFolder("Move Items", "Move here", d -> {
            List<File> fs = new ArrayList<>();
            for (Item i : s) fs.add(i.file);
            transfer(fs, d, true);
            clearSelection();
        });
    }

    void deleteSel(boolean permanent) {
        final List<Item> s = selItems();
        if (s.isEmpty()) return;
        final boolean inBin = loc.type == LOC_BIN;
        boolean outside = false;
        for (Item i : s) if (!inBin && !FileOps.isInside(i.file, root)) outside = true;
        final boolean perm = permanent || inBin || outside;
        String what = s.size() == 1 ? "“" + s.get(0).name + "”" : "these " + s.size() + " items";
        String msg = perm ? "Are you sure you want to permanently delete " + what + "?" + (outside && !permanent ? "\n\n(Items on an SD card can't go to the Recycle Bin.)" : "")
                : "Are you sure you want to move " + what + " to the Recycle Bin?";
        confirm(s.size() == 1 ? (s.get(0).isDir ? "Delete Folder" : "Delete File") : "Delete Multiple Items", msg, () -> {
            clearSelection();
            runOp("Deleting…", errs -> {
                for (Item it : s) {
                    try {
                        if (inBin) bin.delete(it.bin);
                        else if (perm) {
                            if (!FileOps.deleteRecursive(it.file)) errs.add(it.name + ": could not be deleted.");
                        } else bin.moveToBin(it.file);
                    } catch (Exception e) {
                        errs.add(it.name + ": " + e.getMessage());
                    }
                }
            });
        });
    }

    void restoreSel() {
        final List<Item> s = selItems();
        if (s.isEmpty() || loc.type != LOC_BIN) return;
        clearSelection();
        runOp("Restoring…", errs -> {
            for (Item it : s) {
                try { bin.restore(it.bin); }
                catch (Exception e) { errs.add(it.name + ": " + e.getMessage()); }
            }
        });
    }

    void emptyBin() {
        final int n = bin.list().size();
        if (n == 0) { toast("The Recycle Bin is already empty."); return; }
        confirm("Empty Recycle Bin", "Are you sure you want to permanently delete these " + n + " items?", () -> runOp("Emptying Recycle Bin…", errs -> bin.empty()));
    }

    void renameSel() {
        List<Item> s = selItems();
        if (s.size() != 1 || loc.type == LOC_BIN) return;
        final Item it = s.get(0);
        prompt("Rename", it.name, !it.isDir, "Rename", n -> {
            String e = FileOps.validName(n);
            if (e != null) return e;
            if (n.equals(it.name)) return null;
            File parent = it.file.getParentFile();
            if (parent == null) return "Can't rename this item.";
            File dst = new File(parent, n);
            if (dst.exists() && !n.equalsIgnoreCase(it.name)) return "There is already an item named “" + n + "” here.";
            if (!it.file.renameTo(dst)) return "This item couldn't be renamed.";
            movePins(it.file, dst);
            sel.clear();
            selMode = false;
            sel.add(dst.getAbsolutePath());
            refresh();
            return null;
        });
    }

    void newFolder() {
        if (loc.type != LOC_DIR) return;
        final File dir = loc.dir;
        prompt("New folder", FileOps.uniqueName(dir, "New folder", false, true), false, "Create", n -> {
            String e = FileOps.validName(n);
            if (e != null) return e;
            File f = new File(dir, n);
            if (f.exists()) return "There is already an item with that name.";
            if (!f.mkdir()) return "The folder couldn't be created here.";
            sel.clear();
            sel.add(f.getAbsolutePath());
            refresh();
            return null;
        });
    }

    void newFile() {
        if (loc.type != LOC_DIR) return;
        final File dir = loc.dir;
        prompt("New text document", FileOps.uniqueName(dir, "New Text Document.txt", false, false), true, "Create", n -> {
            String e = FileOps.validName(n);
            if (e != null) return e;
            File f = new File(dir, n);
            if (f.exists()) return "There is already an item with that name.";
            try {
                if (!f.createNewFile()) return "The file couldn't be created here.";
            } catch (IOException ex) {
                return ex.getMessage();
            }
            sel.clear();
            sel.add(f.getAbsolutePath());
            refresh();
            return null;
        });
    }

    void pickFolder(String title, String okLabel, final FileCb cb) {
        final File[] at = {loc.type == LOC_DIR ? loc.dir : root};
        Context dc = builder().getContext();
        LinearLayout col = new LinearLayout(dc);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(6), dp(20), 0);
        final TextView path = new TextView(dc);
        path.setTextSize(13);
        path.setAlpha(0.8f);
        path.setPadding(0, 0, 0, dp(6));
        col.addView(path);
        final ListView lv = new ListView(dc);
        col.addView(lv, new LinearLayout.LayoutParams(-1, dp(320)));
        final List<File> rows = new ArrayList<>();
        final ArrayAdapter<String> ad = new ArrayAdapter<>(dc, android.R.layout.simple_list_item_1, new ArrayList<String>());
        lv.setAdapter(ad);
        final Runnable draw = () -> {
            rows.clear();
            ad.clear();
            path.setText(at[0].getAbsolutePath());
            File base = rootFor(at[0]);
            File up = at[0].getParentFile();
            if (up != null && (base == null || !base.equals(at[0]))) {
                rows.add(up);
                ad.add("⬆   ..  (up one level)");
            }
            File[] fs = at[0].listFiles();
            if (fs != null) {
                Arrays.sort(fs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : fs) {
                    if (f.isDirectory() && !f.getName().startsWith(".")) {
                        rows.add(f);
                        ad.add("📁   " + f.getName());
                    }
                }
            }
            ad.notifyDataSetChanged();
        };
        lv.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < rows.size()) {
                at[0] = rows.get(pos);
                draw.run();
            }
        });
        final AlertDialog d = builder().setTitle(title).setView(col)
                .setPositiveButton(okLabel, (x, w) -> cb.on(at[0]))
                .setNegativeButton("Cancel", null)
                .setNeutralButton("New folder", null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                prompt("New folder", "New folder", false, "Create", n -> {
                    String e = FileOps.validName(n);
                    if (e != null) return e;
                    File nf = new File(at[0], n);
                    if (nf.exists()) return "There is already an item with that name.";
                    if (!nf.mkdirs()) return "The folder couldn't be created here.";
                    at[0] = nf;
                    draw.run();
                    return null;
                })));
        draw.run();
        d.show();
    }

    // ================================================================ properties
    void showProps(final List<Item> input) {
        final List<Item> list = new ArrayList<>(input);
        final boolean current = list.isEmpty();
        String title;
        final StringBuilder sb = new StringBuilder();
        if (current) {
            if (loc.type == LOC_BIN) {
                List<RecycleBin.Entry> es = bin.list();
                long sz = 0;
                for (RecycleBin.Entry e : es) sz += e.size;
                alert("Recycle Bin Properties", es.size() + " items\nSize: " + FileOps.formatSize(sz) + "\nLocation: " + bin.dir.getAbsolutePath());
                return;
            }
            list.add(new Item(loc.dir));
        }
        final Item one = list.size() == 1 ? list.get(0) : null;
        if (one != null) {
            File r = rootFor(one.file);
            boolean isRoot = r != null && r.equals(one.file);
            title = (isRoot ? rootLabel(r) : one.name) + " Properties";
            sb.append("Type:  ").append(isRoot ? "Local Disk" : FileOps.typeName(one.name, one.isDir)).append('\n');
            String location = one.bin != null ? new File(one.bin.orig).getParent() : one.file.getParent();
            sb.append(one.bin != null ? "Original location:  " : "Location:  ").append(location == null ? "—" : location).append('\n');
            if (!one.isDir) sb.append("Size:  ").append(FileOps.formatSize(one.size)).append(" (").append(String.format(Locale.US, "%,d", one.size)).append(" bytes)\n");
            sb.append(one.bin != null ? "Deleted:  " : "Modified:  ").append(FileOps.formatDate(one.mtime)).append('\n');
            if (one.bin == null) {
                sb.append("Attributes:  ").append(one.file.canRead() ? "Readable" : "Not readable")
                        .append(one.file.canWrite() ? ", Writable" : ", Read-only")
                        .append(one.name.startsWith(".") ? ", Hidden" : "").append('\n');
            }
            if (isRoot) {
                try {
                    StatFs st = new StatFs(one.file.getPath());
                    long total = st.getTotalBytes(), free = st.getAvailableBytes();
                    sb.append("Used space:  ").append(FileOps.formatSize(total - free)).append('\n')
                            .append("Free space:  ").append(FileOps.formatSize(free)).append('\n')
                            .append("Capacity:  ").append(FileOps.formatSize(total)).append('\n');
                } catch (Exception ignored) { }
            }
        } else {
            title = list.size() + " items Properties";
            int files = 0;
            for (Item i : list) if (!i.isDir) files++;
            sb.append("Contains:  ").append(files).append(" files, ").append(list.size() - files).append(" folders\n");
        }
        final boolean needCalc = one == null || one.isDir;
        final String base = sb.toString();
        AlertDialog.Builder b = builder().setTitle(title).setMessage(base + (needCalc ? "Size:  calculating…" : "")).setPositiveButton("OK", null);
        if (one != null && one.bin != null) {
            b.setNeutralButton("Restore", (x, w) -> {
                sel.clear();
                sel.add(one.path());
                restoreSel();
            });
        } else if (one != null && !one.isDir) {
            b.setNeutralButton("Open with…", (x, w) -> openExternal(one.file, true));
        }
        final AlertDialog d = b.create();
        d.show();
        if (needCalc) {
            io.execute(() -> {
                final long[] acc = new long[3];
                for (Item it : list) {
                    if (it.isDir) {
                        FileOps.dirSize(it.file, acc);
                        if (one == null) acc[2]++;
                    } else {
                        acc[0] += it.size;
                        acc[1]++;
                    }
                }
                ui.post(() -> {
                    if (d.isShowing())
                        d.setMessage(base + "Size:  " + FileOps.formatSize(acc[0]) + " (" + String.format(Locale.US, "%,d", acc[0]) + " bytes)\n"
                                + "Contains:  " + String.format(Locale.US, "%,d", acc[1]) + " files, " + String.format(Locale.US, "%,d", acc[2]) + " folders");
                });
            });
        }
    }

    // ================================================================ Notepad
    void openEditor(final File f) {
        io.execute(() -> {
            String txt = null, err = null;
            try { txt = FileOps.readText(f); } catch (Exception e) { err = e.getMessage(); }
            final String ft = txt, fe = err;
            ui.post(() -> {
                if (fe != null) alert("Notepad", fe);
                else showEditor(f, ft);
            });
        });
    }

    TextView barBtn(String label, View.OnClickListener l) {
        TextView b = tv(label, T.titleText, 14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setBackground(pressable(null, 0x33FFFFFF, dp(4)));
        b.setOnClickListener(l);
        return b;
    }

    void showEditor(final File f, String text) {
        final Dialog d = new Dialog(this, T.dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout col = vl();
        col.setBackgroundColor(T.dark ? 0xFF111111 : 0xFFFFFFFF);
        LinearLayout bar = hl();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(grad(T.title, 0));
        bar.setPadding(dp(12), 0, dp(4), 0);
        final TextView title = tv(f.getName() + " - Notepad", T.titleText, 15);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        bar.addView(title, lp(0, -2, 1));
        final EditText et = new EditText(this);
        et.setText(text);
        et.setTypeface(Typeface.MONOSPACE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setBackgroundColor(0);
        et.setTextColor(T.dark ? 0xFFEEEEEE : 0xFF000000);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setSingleLine(false);
        et.setHorizontallyScrolling(false);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        final boolean[] dirty = {false};
        final boolean[] wrap = {true};
        final Runnable save = () -> {
            try {
                FileOps.writeText(f, et.getText().toString());
                dirty[0] = false;
                title.setText(f.getName() + " - Notepad");
                toast("Saved");
                refresh();
            } catch (Exception e) {
                alert("Couldn't save", String.valueOf(e.getMessage()));
            }
        };
        final Runnable close = () -> {
            if (!dirty[0]) { d.dismiss(); return; }
            builder().setTitle("Notepad").setMessage("Do you want to save changes to " + f.getName() + "?")
                    .setPositiveButton("Save", (x, w) -> { save.run(); if (!dirty[0]) d.dismiss(); })
                    .setNegativeButton("Don't Save", (x, w) -> d.dismiss())
                    .setNeutralButton("Cancel", null).show();
        };
        bar.addView(barBtn("Wrap", v -> {
            wrap[0] = !wrap[0];
            et.setHorizontallyScrolling(!wrap[0]);
            v.setAlpha(wrap[0] ? 1f : 0.5f);
        }));
        bar.addView(barBtn("Save", v -> save.run()));
        bar.addView(barBtn("Close", v -> close.run()));
        col.addView(bar, lp(-1, dp(50), 0));
        col.addView(et, lp(-1, 0, 1));
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (!dirty[0]) {
                    dirty[0] = true;
                    title.setText("*" + f.getName() + " - Notepad");
                }
            }
        });
        d.setContentView(col);
        d.setOnKeyListener((di, keyCode, e) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) close.run();
                return true;
            }
            if (e.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_S && e.getAction() == KeyEvent.ACTION_UP) {
                save.run();
                return true;
            }
            return false;
        });
        d.show();
    }

    // ================================================================ Photo viewer
    void openImage(Item it) {
        final List<Item> imgs = new ArrayList<>();
        for (Item x : items) if (x.kind() == FileOps.K_IMAGE && !"svg".equals(FileOps.ext(x.name))) imgs.add(x);
        if (imgs.isEmpty()) imgs.add(it);
        final int[] idx = {Math.max(0, imgs.indexOf(it))};
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout col = vl();
        col.setBackgroundColor(0xFF000000);
        LinearLayout top = hl();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackground(grad(T.title, 0));
        top.setPadding(dp(12), 0, dp(4), 0);
        final TextView title = tv("", T.titleText, 14);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        top.addView(title, lp(0, -2, 1));
        top.addView(barBtn("Close", v -> d.dismiss()));
        col.addView(top, lp(-1, dp(48), 0));
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        col.addView(iv, lp(-1, 0, 1));
        final Runnable show = () -> {
            final Item x = imgs.get(idx[0]);
            title.setText(x.name + "   (" + (idx[0] + 1) + " of " + imgs.size() + ")");
            iv.setRotation(0);
            iv.setImageDrawable(null);
            io.execute(() -> {
                final Bitmap bm = FileOps.decodeSampled(x.file, 2048);
                ui.post(() -> {
                    if (imgs.get(idx[0]) != x) return;
                    if (bm != null) iv.setImageBitmap(bm);
                    else iv.setImageDrawable(new IconDrawable(FileOps.K_IMAGE, T.id, ""));
                });
            });
        };
        final Runnable prev = () -> { idx[0] = (idx[0] - 1 + imgs.size()) % imgs.size(); show.run(); };
        final Runnable next = () -> { idx[0] = (idx[0] + 1) % imgs.size(); show.run(); };
        LinearLayout bar = hl();
        bar.setGravity(Gravity.CENTER);
        bar.setBackground(grad(T.cmd, 0));
        bar.addView(viewerBtn("◀", v -> prev.run()));
        bar.addView(viewerBtn("↻ Rotate", v -> iv.setRotation((iv.getRotation() + 90) % 360)));
        bar.addView(viewerBtn("▶", v -> next.run()));
        bar.addView(viewerBtn("Share", v -> share(Collections.singletonList(imgs.get(idx[0])))));
        bar.addView(viewerBtn("Open with", v -> openExternal(imgs.get(idx[0]).file, true)));
        col.addView(bar, lp(-1, dp(52), 0));
        final float[] downX = {0};
        iv.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) downX[0] = e.getX();
            else if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = e.getX() - downX[0];
                if (dx > dp(60)) prev.run();
                else if (dx < -dp(60)) next.run();
            }
            return true;
        });
        d.setContentView(col);
        d.show();
        show.run();
    }

    TextView viewerBtn(String label, View.OnClickListener l) {
        TextView b = tv(label, T.cmdText, 14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setBackground(pressable(null, T.tbPressed, dp(T.tbRadius)));
        b.setOnClickListener(l);
        return b;
    }

    // ================================================================ icons & thumbnails
    void bindIcon(final ImageView iv, final Item it, boolean allowThumb) {
        final int k = it.kind();
        final String key = it.path();
        iv.setTag(key);
        iv.clearColorFilter();
        if (allowThumb && (k == FileOps.K_IMAGE || k == FileOps.K_VIDEO) && !thumbFailed.contains(key)) {
            Bitmap b = thumbs.get(key);
            if (b != null) { iv.setImageBitmap(b); return; }
            iv.setImageDrawable(new IconDrawable(k, T.id, FileOps.ext(it.name)));
            final File f = it.file;
            thumbIo.execute(() -> {
                Bitmap bm = null;
                try {
                    if (k == FileOps.K_VIDEO) bm = ThumbnailUtils.createVideoThumbnail(f.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
                    else bm = FileOps.decodeSampled(f, 192);
                } catch (Throwable ignored) { }
                final Bitmap fb = bm;
                ui.post(() -> {
                    if (fb == null) { thumbFailed.add(key); return; }
                    thumbs.put(key, fb);
                    if (key.equals(iv.getTag())) iv.setImageBitmap(fb);
                });
            });
            return;
        }
        iv.setImageDrawable(new IconDrawable(k, T.id, FileOps.ext(it.name)));
    }

    // ================================================================ adapter
    final class Holder {
        String mode;
        LinearLayout root;
        ImageView icon;
        TextView name, sub1, sub2;
    }

    final class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            Holder h = cv == null ? null : (Holder) cv.getTag();
            if (h == null || !viewMode.equals(h.mode)) {
                h = makeHolder(viewMode);
                cv = h.root;
                cv.setTag(h);
            }
            bind(h, items.get(pos));
            return cv;
        }
    }

    Holder makeHolder(String mode) {
        Holder h = new Holder();
        h.mode = mode;
        LinearLayout r = hl();
        h.root = r;
        h.icon = new ImageView(this);
        h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.name = tv("", T.contentText, 13);
        switch (mode) {
            case V_TILES: {
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(6), dp(6), dp(6), dp(6));
                r.addView(h.icon, dp(48), dp(48));
                LinearLayout tc = vl();
                tc.setPadding(dp(10), 0, 0, 0);
                h.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                tc.addView(h.name);
                h.sub1 = tv("", T.dimText, 12);
                h.sub1.setSingleLine(true);
                h.sub1.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                tc.addView(h.sub1);
                h.sub2 = tv("", T.dimText, 12);
                h.sub2.setSingleLine(true);
                tc.addView(h.sub2);
                r.addView(tc, lp(0, -2, 1));
                break;
            }
            case V_LIST:
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(6), dp(8), dp(6), dp(8));
                r.addView(h.icon, dp(26), dp(26));
                h.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                h.name.setPadding(dp(10), 0, 0, 0);
                r.addView(h.name, lp(0, -2, 1));
                break;
            case V_DETAILS:
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(8), dp(8), dp(12), dp(8));
                r.addView(h.icon, dp(24), dp(24));
                h.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                h.name.setPadding(dp(8), 0, dp(4), 0);
                r.addView(h.name, lp(0, -2, 1));
                h.sub1 = tv("", T.dimText, 12);
                h.sub1.setSingleLine(true);
                if (wide()) r.addView(h.sub1, lp(dp(140), -2, 0));
                h.sub2 = tv("", T.dimText, 12);
                h.sub2.setSingleLine(true);
                h.sub2.setGravity(Gravity.END);
                r.addView(h.sub2, lp(dp(80), -2, 0));
                break;
            default: {
                r.setOrientation(LinearLayout.VERTICAL);
                r.setGravity(Gravity.CENTER_HORIZONTAL);
                r.setPadding(dp(3), dp(6), dp(3), dp(6));
                int s = dp(T.softkeys ? 60 : 54);
                r.addView(h.icon, s, s);
                h.name.setGravity(Gravity.CENTER_HORIZONTAL);
                h.name.setMaxLines(2);
                h.name.setEllipsize(TextUtils.TruncateAt.END);
                h.name.setPadding(dp(3), dp(1), dp(3), dp(1));
                if (T.softkeys) h.name.setTypeface(Typeface.DEFAULT_BOLD);
                LinearLayout.LayoutParams nl = lp(-2, -2, 0);
                nl.topMargin = dp(4);
                r.addView(h.name, nl);
            }
        }
        r.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        return h;
    }

    void bind(Holder h, Item it) {
        h.name.setText(it.name);
        boolean s = sel.contains(it.path());
        boolean xpIcons = T.xpTasks && V_LARGE.equals(h.mode);
        if (xpIcons) {
            h.root.setBackground(null);
            h.name.setBackground(s ? solid(T.sel, 0) : null);
            h.name.setTextColor(s ? 0xFFFFFFFF : T.contentText);
        } else {
            if (s) {
                GradientDrawable g = solid(T.sel, dp(T.itemRadius));
                g.setStroke(dp(1), T.selBorder);
                h.root.setBackground(g);
            } else {
                h.root.setBackground(null);
            }
            h.name.setTextColor(s ? T.selText : T.contentText);
        }
        h.root.setAlpha(clipHas(it.file) ? 0.5f : 1f);
        if (V_TILES.equals(h.mode)) {
            String l1;
            if (it.bin != null) l1 = "From: " + new File(it.bin.orig).getParent();
            else if (loc.type == LOC_SEARCH) l1 = it.file.getParent();
            else l1 = FileOps.typeName(it.name, it.isDir);
            h.sub1.setText(l1);
            h.sub2.setText(it.isDir ? FileOps.formatDate(it.mtime) : FileOps.formatSize(it.size) + "   " + FileOps.formatDate(it.mtime));
            int dim = s && !T.xpTasks ? T.selText : T.dimText;
            if (s && T.xpTasks) dim = 0xFFDDE6FF;
            h.sub1.setTextColor(dim);
            h.sub2.setTextColor(dim);
        } else if (V_DETAILS.equals(h.mode)) {
            h.sub1.setText(FileOps.formatDate(it.mtime));
            h.sub2.setText(it.isDir ? "" : FileOps.formatSize(it.size));
            int dim = s ? (T.xpTasks ? 0xFFDDE6FF : T.selText) : T.dimText;
            h.sub1.setTextColor(dim);
            h.sub2.setTextColor(dim);
        }
        bindIcon(h.icon, it, V_LARGE.equals(h.mode) || V_TILES.equals(h.mode));
        if (xpIcons && s) h.icon.setColorFilter(0x55316AC5, PorterDuff.Mode.SRC_ATOP);
    }
}
