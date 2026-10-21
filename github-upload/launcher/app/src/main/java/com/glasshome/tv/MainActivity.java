package com.glasshome.tv;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    static final float FOCUS_SCALE = 1.08f;
    static final int T_HEADER = 0, T_TILE = 1, T_FILLER = 2;

    static class AppEntry {
        String pkg, label;
        Drawable banner, icon;
        Intent launch;
        Integer color;
    }

    static class Pill {
        final TextView view;
        final Runnable action;

        Pill(TextView view, Runnable action) {
            this.view = view;
            this.action = action;
        }
    }

    final List<AppEntry> tiles = new ArrayList<>();
    int dockCount, cols, prefsVersion;
    boolean rtl, labelsAlways, glowOn, showWifiName;
    String clockMode;

    FrameLayout root;
    RecyclerView rv;
    GridLayoutManager glm;
    GlassView glass;
    Adapter adapter;
    int scrollY;

    // focus state: either a pill in the top bar or a tile
    boolean inTop;
    int topIdx, focusTile, lastDock;
    boolean selectDown, longFired;

    // header views (recreated if the header is recycled)
    TextView greetingV, clockV, dateV, heroV;
    final List<Pill> pills = new ArrayList<>();
    TextView wifiPill;
    View glowV;
    GradientDrawable glow;
    int glowColor = 0xFF303030;
    ValueAnimator glowAnim;

    float dp;
    int screenW, screenH, sidePad, gap, cardH, topPad, labelH, tileItemH, headerH;
    final Map<Integer, Typeface> fonts = new HashMap<>();

    final BroadcastReceiver timeRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { bindHeaderText(); }
    };
    final BroadcastReceiver netRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { bindHeaderText(); }
    };
    final BroadcastReceiver pkgRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reloadApps(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefsVersion = Prefs.version(this);
        cols = Prefs.columns(this);
        labelsAlways = Prefs.bool(this, "labelsAlways", false);
        glowOn = Prefs.bool(this, "glow", true);
        showWifiName = Prefs.bool(this, "wifiName", false)
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        clockMode = Prefs.str(this, "clock", "auto");
        rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        dp = getResources().getDisplayMetrics().density;
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;
        sidePad = px(72);
        gap = px(cols >= 5 ? 22 : 28);
        topPad = px(14);
        labelH = px(34);
        int cardW = (screenW - 2 * sidePad - (cols - 1) * gap) / cols;
        cardH = cardW * 9 / 16;
        tileItemH = topPad + cardH + labelH;
        headerH = screenH - tileItemH - px(44);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        applyWallpaper();

        rv = new RecyclerView(this);
        rv.setPadding(sidePad - gap / 2, 0, sidePad - gap / 2, px(80));
        rv.setClipToPadding(false);
        rv.setClipChildren(false);
        rv.setFocusable(false);
        rv.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        rv.setItemAnimator(null);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        glm = new GridLayoutManager(this, cols) {
            @Override
            protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State s, @NonNull int[] extra) {
                extra[0] = screenH;
                extra[1] = screenH;
            }
        };
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int pos) {
                int t = adapter.getItemViewType(pos);
                if (t == T_HEADER) return cols;
                if (t == T_FILLER) return cols - dockCount;
                return 1;
            }
        });
        rv.setLayoutManager(glm);
        rv.addItemDecoration(new DockBand());
        adapter = new Adapter();
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                scrollY += dy;
                glass.refresh();
            }
        });
        rv.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> glass.post(glass::refresh));
        root.addView(rv, new FrameLayout.LayoutParams(-1, -1));

        glass = new GlassView(this);
        root.addView(glass, new FrameLayout.LayoutParams(-1, -1));

        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        setContentView(root);
        root.requestFocus();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(pkgRx, pf);

        reloadApps();
        restoreFocus();
        rv.post(() -> applyFocus(false));
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(pkgRx);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Prefs.version(this) != prefsVersion) {
            recreate();
            return;
        }
        IntentFilter tf = new IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeRx, tf);
        IntentFilter nf = new IntentFilter();
        nf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        nf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(netRx, nf);
        registered = true;
        bindHeaderText();
    }

    boolean registered;

    @Override
    protected void onPause() {
        if (registered) {
            unregisterReceiver(timeRx);
            unregisterReceiver(netRx);
            registered = false;
        }
        SharedPreferences.Editor e = getPreferences(MODE_PRIVATE).edit();
        e.putBoolean("inTop", inTop).putInt("topIdx", topIdx);
        if (focusTile < tiles.size()) e.putString("pkg", tiles.get(focusTile).pkg);
        if (lastDock < tiles.size()) e.putString("dock", tiles.get(lastDock).pkg);
        e.apply();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        rv.post(() -> applyFocus(false));
    }

    // ---------------------------------------------------------------- apps

    void reloadApps() {
        String focusPkg = focusTile < tiles.size() ? tiles.get(focusTile).pkg : null;
        PackageManager pm = getPackageManager();
        Set<String> hidden = Prefs.hidden(this);
        Map<String, AppEntry> all = loadAll(this, hidden);
        tiles.clear();
        for (String pkg : Prefs.dock(this)) {
            AppEntry e = all.remove(pkg);
            if (e != null && tiles.size() < cols) tiles.add(e);
        }
        dockCount = tiles.size();
        List<AppEntry> rest = new ArrayList<>(all.values());
        Collections.sort(rest, (a, b) -> a.label.compareToIgnoreCase(b.label));
        tiles.addAll(rest);
        focusTile = Math.max(0, Math.min(indexOf(focusPkg, 0), tiles.size() - 1));
        lastDock = Math.min(lastDock, Math.max(0, dockCount - 1));
        adapter.notifyDataSetChanged();
        rv.post(() -> applyFocus(false));
    }

    /** Every launchable app except this one and the hidden set, keyed by package. */
    static Map<String, AppEntry> loadAll(Context ctx, Set<String> hidden) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, AppEntry> all = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg) || all.containsKey(pkg)) continue;
                AppEntry e = new AppEntry();
                e.pkg = pkg;
                e.label = String.valueOf(ri.loadLabel(pm));
                e.banner = ri.activityInfo.loadBanner(pm);
                if (e.banner == null) e.banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                e.icon = ri.loadIcon(pm);
                e.launch = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                all.put(pkg, e);
            }
        }
        return all;
    }

    int indexOf(String pkg, int def) {
        if (pkg == null) return def;
        for (int i = 0; i < tiles.size(); i++) if (tiles.get(i).pkg.equals(pkg)) return i;
        return def;
    }

    void restoreFocus() {
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        inTop = p.getBoolean("inTop", false) || tiles.isEmpty();
        topIdx = p.getInt("topIdx", 0);
        focusTile = indexOf(p.getString("pkg", null), 0);
        lastDock = Math.min(indexOf(p.getString("dock", null), 0), Math.max(0, dockCount - 1));
    }

    void launch(AppEntry e) {
        try {
            startActivity(e.launch);
        } catch (Exception ex) {
            Intent i = getPackageManager().getLaunchIntentForPackage(e.pkg);
            if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ex) {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    // ---------------------------------------------------------------- tile menu

    void showTileMenu(int t) {
        AppEntry e = tiles.get(t);
        boolean inDock = t < dockCount;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        List<String> dock = new ArrayList<>();
        for (int i = 0; i < dockCount; i++) dock.add(tiles.get(i).pkg);
        if (inDock) {
            // "left"/"right" are visual, so they swap in right-to-left layouts
            int towardsLeft = rtl ? 1 : -1;
            if (t + towardsLeft >= 0 && t + towardsLeft < dockCount) {
                labels.add(getString(R.string.move_left));
                actions.add(() -> moveInDock(dock, t, t + towardsLeft));
            }
            if (t - towardsLeft >= 0 && t - towardsLeft < dockCount) {
                labels.add(getString(R.string.move_right));
                actions.add(() -> moveInDock(dock, t, t - towardsLeft));
            }
            labels.add(getString(R.string.remove_dock));
            actions.add(() -> {
                dock.remove(e.pkg);
                saveDock(dock, 0);
            });
        } else {
            labels.add(getString(R.string.add_dock));
            actions.add(() -> {
                if (dock.size() >= cols) dock.remove(dock.size() - 1);
                dock.add(e.pkg);
                saveDock(dock, dock.size() - 1);
            });
            labels.add(getString(R.string.hide_app));
            actions.add(() -> {
                Set<String> h = Prefs.userHidden(this);
                h.add(e.pkg);
                Prefs.setHidden(this, h);
                prefsVersion = Prefs.version(this);
                reloadApps();
            });
        }
        labels.add(getString(R.string.app_info));
        actions.add(() -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg))));

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(e.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    void moveInDock(List<String> dock, int from, int to) {
        Collections.swap(dock, from, to);
        saveDock(dock, to);
    }

    void saveDock(List<String> dock, int focus) {
        Prefs.setDock(this, dock);
        prefsVersion = Prefs.version(this);
        reloadApps();
        if (dockCount > 0) {
            inTop = false;
            focusTile = Math.min(focus, dockCount - 1);
            lastDock = focusTile;
        }
        rv.post(() -> applyFocus(true));
    }

    // ---------------------------------------------------------------- inputs

    /** HDMI / AV / tuner inputs registered with the TV input framework (empty on streaming sticks). */
    List<TvInputInfo> hardwareInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_LIVE_TV)) return out;
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo i : tim.getTvInputList()) {
            if (i.isPassthroughInput() || i.getType() == TvInputInfo.TYPE_TUNER) out.add(i);
        }
        return out;
    }

    void showInputs() {
        List<TvInputInfo> inputs = hardwareInputs();
        String[] names = new String[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            TvInputInfo in = inputs.get(i);
            names[i] = in.getType() == TvInputInfo.TYPE_TUNER ? getString(R.string.live_tv)
                    : String.valueOf(in.loadLabel(this));
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.inputs)
                .setItems(names, (d, which) -> {
                    TvInputInfo in = inputs.get(which);
                    Uri uri = in.isPassthroughInput() ? TvContract.buildChannelUriForPassthroughInput(in.getId())
                            : TvContract.buildChannelsUriForInput(in.getId());
                    open(new Intent(Intent.ACTION_VIEW, uri));
                })
                .show();
    }

    // ---------------------------------------------------------------- navigation

    int rowOf(int t) { return t < dockCount ? 0 : (dockCount > 0 ? 1 : 0) + (t - dockCount) / cols; }
    int colOf(int t) { return t < dockCount ? t : (t - dockCount) % cols; }
    int rowCount() { return rowOf(tiles.size() - 1) + 1; }

    int tileAt(int r, int c) {
        if (dockCount > 0 && r == 0) return Math.min(c, dockCount - 1);
        int start = dockCount + (r - (dockCount > 0 ? 1 : 0)) * cols;
        int size = Math.min(cols, tiles.size() - start);
        return start + Math.min(c, size - 1);
    }

    boolean hasFiller() { return dockCount > 0 && dockCount < cols; }

    int adapterPos(int t) {
        if (t < dockCount) return 1 + t;
        return 1 + dockCount + (hasFiller() ? 1 : 0) + (t - dockCount);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int k = ev.getKeyCode();
        boolean nav = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || isSelect(k) || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU;
        if (!nav) return super.dispatchKeyEvent(ev);
        if (rtl && k == KeyEvent.KEYCODE_DPAD_LEFT) k = KeyEvent.KEYCODE_DPAD_RIGHT;
        else if (rtl && k == KeyEvent.KEYCODE_DPAD_RIGHT) k = KeyEvent.KEYCODE_DPAD_LEFT;

        if (isSelect(k)) {
            // short press opens, long press shows the tile menu
            if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                if (ev.getRepeatCount() == 0) {
                    selectDown = true;
                    longFired = false;
                } else if (selectDown && !longFired) {
                    longFired = true;
                    if (!inTop && !tiles.isEmpty()) showTileMenu(focusTile);
                }
            } else if (ev.getAction() == KeyEvent.ACTION_UP) {
                if (selectDown && !longFired) onSelect();
                selectDown = false;
            }
            return true;
        }
        if (ev.getAction() == KeyEvent.ACTION_DOWN) onNav(k, ev.getRepeatCount() > 0);
        return true;
    }

    void onSelect() {
        if (inTop) {
            if (topIdx < pills.size()) pills.get(topIdx).action.run();
        } else if (!tiles.isEmpty()) {
            launch(tiles.get(focusTile));
        }
    }

    void onNav(int k, boolean repeat) {
        int prevTile = focusTile;
        boolean prevTop = inTop;
        if (inTop) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) topIdx = Math.max(0, topIdx - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) topIdx = Math.min(pills.size() - 1, topIdx + 1);
            else if ((k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_BACK) && !tiles.isEmpty()) {
                inTop = false;
                focusTile = dockCount > 0 ? lastDock : 0;
            }
        } else if (!tiles.isEmpty()) {
            int r = rowOf(focusTile), c = colOf(focusTile);
            if (k == KeyEvent.KEYCODE_DPAD_UP) {
                if (r == 0) inTop = true;
                else focusTile = tileAt(r - 1, c);
            } else if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (r < rowCount() - 1) focusTile = tileAt(r + 1, c);
            } else if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (c > 0) focusTile--;
            } else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (focusTile + 1 < tiles.size() && rowOf(focusTile + 1) == r) focusTile++;
            } else if (k == KeyEvent.KEYCODE_BACK) {
                if (r > 0 && dockCount > 0) focusTile = lastDock;
            } else if (k == KeyEvent.KEYCODE_MENU && !repeat) {
                showTileMenu(focusTile);
            }
        }
        if (prevTile != focusTile || prevTop != inTop || isHorizontal(k)) {
            setTileFocused(prevTile, false, true);
            applyFocus(true);
        }
    }

    static boolean isSelect(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
    }

    static boolean isHorizontal(int k) {
        return k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    void applyFocus(boolean animate) {
        if (tiles.isEmpty()) inTop = true;
        if (focusTile >= tiles.size()) focusTile = Math.max(0, tiles.size() - 1);
        if (!pills.isEmpty()) topIdx = Math.max(0, Math.min(topIdx, pills.size() - 1));
        for (int t = 0; t < tiles.size(); t++) setTileFocused(t, !inTop && t == focusTile, animate);
        updatePills();

        boolean inDock = !inTop && dockCount > 0 && focusTile < dockCount;
        if (inDock) lastDock = focusTile;
        if (inTop || inDock) {
            if (scrollY != 0) scroll(-scrollY, animate);
        } else {
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(adapterPos(focusTile));
            int desired = (int) (screenH * 0.30f);
            if (vh != null) scroll(vh.itemView.getTop() - desired, animate);
            else {
                glm.scrollToPositionWithOffset(adapterPos(focusTile), desired);
                rv.post(() -> scrollY = rv.computeVerticalScrollOffset());
            }
        }
        updateHero(animate);
        View pill = inTop && topIdx < pills.size() ? pills.get(topIdx).view : null;
        glass.moveTo(inTop ? pillTarget(pill) : tileTarget(focusTile), animate);
    }

    void scroll(int dy, boolean animate) {
        if (dy == 0) return;
        if (animate) rv.smoothScrollBy(0, dy, new DecelerateInterpolator(1.6f), 320);
        else rv.scrollBy(0, dy);
    }

    void setTileFocused(int t, boolean f, boolean animate) {
        if (t < 0 || t >= tiles.size()) return;
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(adapterPos(t));
        if (!(vh instanceof TileVH)) return;
        TileVH h = (TileVH) vh;
        float s = f ? FOCUS_SCALE : 1f;
        float a = f ? 1f : labelsAlways ? 0.55f : 0f;
        if (animate) {
            h.card.animate().scaleX(s).scaleY(s).setDuration(240).setInterpolator(new DecelerateInterpolator()).start();
            h.label.animate().alpha(a).setDuration(200).start();
        } else {
            h.card.setScaleX(s);
            h.card.setScaleY(s);
            h.label.setAlpha(a);
        }
    }

    final int[] rootLoc = new int[2], vLoc = new int[2];

    GlassView.Target tileTarget(int t) {
        return new GlassView.Target() {
            @Override public boolean get(RectF out) {
                RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(adapterPos(t));
                if (!(vh instanceof TileVH)) return false;
                TileVH h = (TileVH) vh;
                glass.getLocationInWindow(rootLoc);
                h.itemView.getLocationInWindow(vLoc);
                float l = vLoc[0] - rootLoc[0] + h.card.getLeft(), tp = vLoc[1] - rootLoc[1] + h.card.getTop();
                float w = h.card.getWidth(), ht = h.card.getHeight(), cx = l + w / 2, cy = tp + ht / 2;
                float hw = w * FOCUS_SCALE / 2 + px(5), hh = ht * FOCUS_SCALE / 2 + px(5);
                out.set(cx - hw, cy - hh, cx + hw, cy + hh);
                return true;
            }
            @Override public float radius() { return px(17); }
        };
    }

    GlassView.Target pillTarget(View pill) {
        return new GlassView.Target() {
            @Override public boolean get(RectF out) {
                if (pill == null || !pill.isAttachedToWindow()) return false;
                glass.getLocationInWindow(rootLoc);
                pill.getLocationInWindow(vLoc);
                float l = vLoc[0] - rootLoc[0], t = vLoc[1] - rootLoc[1];
                out.set(l - px(3), t - px(3), l + pill.getWidth() + px(3), t + pill.getHeight() + px(3));
                return true;
            }
            @Override public float radius() { return pill == null ? 0 : pill.getHeight() / 2f + px(3); }
        };
    }

    void updatePills() {
        for (int i = 0; i < pills.size(); i++) {
            pills.get(i).view.setTextColor(inTop && topIdx == i ? Color.WHITE : 0xB3FFFFFF);
        }
    }

    void updateHero(boolean animate) {
        if (heroV == null) return;
        if (dockCount == 0) {
            heroV.setText("");
            glowV.setAlpha(0f);
            return;
        }
        AppEntry e = tiles.get(lastDock);
        heroV.setText(e.label);
        boolean inDock = !inTop && focusTile < dockCount;
        heroV.animate().alpha(inDock ? 1f : 0.45f).setDuration(220).start();
        if (!glowOn) {
            glowV.setAlpha(0f);
            return;
        }
        glowV.animate().alpha(inDock ? 1f : 0.35f).setDuration(300).start();
        if (e.color == null) e.color = sampleColor(e.banner != null ? e.banner : e.icon);
        int target = e.color;
        if (glowAnim != null) glowAnim.cancel();
        if (!animate) {
            setGlow(target);
            return;
        }
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, target);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(a -> setGlow((Integer) a.getAnimatedValue()));
        glowAnim.start();
    }

    void setGlow(int c) {
        glowColor = c;
        if (glow != null) glow.setColors(new int[]{withAlpha(c, 0x80), withAlpha(c, 0x30), withAlpha(c, 0)});
    }

    static int withAlpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    /** Average of the banner's saturated pixels, brightened, for the ambient glow. */
    static int sampleColor(Drawable d) {
        if (d == null || d.getConstantState() == null) return 0xFF808080;
        Drawable copy = d.getConstantState().newDrawable().mutate();
        Bitmap b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        copy.setBounds(0, 0, 32, 18);
        copy.draw(new Canvas(b));
        float[] hsv = new float[3];
        double r = 0, g = 0, bl = 0, wsum = 0;
        for (int y = 0; y < 18; y++) for (int x = 0; x < 32; x++) {
            int p = b.getPixel(x, y);
            if (Color.alpha(p) < 128) continue;
            Color.colorToHSV(p, hsv);
            double w = hsv[1] * hsv[2] + 0.02;
            r += Color.red(p) * w; g += Color.green(p) * w; bl += Color.blue(p) * w; wsum += w;
        }
        b.recycle();
        if (wsum == 0) return 0xFF808080;
        int avg = Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (bl / wsum));
        Color.colorToHSV(avg, hsv);
        if (hsv[1] > 0.15f) hsv[1] = Math.max(hsv[1], 0.6f);
        hsv[2] = Math.max(hsv[2], 0.9f);
        return Color.HSVToColor(hsv);
    }

    // ---------------------------------------------------------------- header

    void bindHeaderText() {
        if (clockV == null) return;
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        greetingV.setText(h < 5 ? R.string.greet_night : h < 12 ? R.string.greet_morning
                : h < 18 ? R.string.greet_afternoon : R.string.greet_evening);
        boolean h24 = clockMode.equals("24") || (clockMode.equals("auto") && DateFormat.is24HourFormat(this));
        clockV.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now.getTime()));
        dateV.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now.getTime()));
        if (wifiPill != null) wifiPill.setText(networkName());
    }

    String networkName() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) return getString(R.string.offline);
        if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) return getString(R.string.ethernet);
        if (!showWifiName) return getString(R.string.wifi);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wi = wm.getConnectionInfo();
        String s = wi == null ? null : wi.getSSID();
        if (s == null || s.contains("unknown ssid") || s.equals("0x")) return getString(R.string.wifi);
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) s = s.substring(1, s.length() - 1);
        return s;
    }

    View buildHeader() {
        FrameLayout h = new FrameLayout(this);
        h.setLayoutParams(new RecyclerView.LayoutParams(-1, headerH));
        h.setClipChildren(false);

        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(px(150));
        glowV = new View(this);
        glowV.setBackground(glow);
        glowV.setScaleX(2.4f);
        setGlow(glowColor);
        // a circle stretched into a soft ellipse centred on the hero title
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(px(300), px(300), Gravity.BOTTOM | Gravity.START);
        gl.setMarginStart(gap / 2 - px(20));
        gl.bottomMargin = -px(90);
        h.addView(glowV, gl);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greetingV = text(22, 400, 0xB3FFFFFF);
        clockV = text(92, 200, Color.WHITE);
        dateV = text(22, 300, 0x99FFFFFF);
        greetingV.setVisibility(Prefs.bool(this, "greeting", true) ? View.VISIBLE : View.GONE);
        dateV.setVisibility(Prefs.bool(this, "date", true) ? View.VISIBLE : View.GONE);
        left.addView(greetingV);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.topMargin = px(4);
        cl.bottomMargin = px(6);
        left.addView(clockV, cl);
        left.addView(dateV);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(screenW / 2, -2, Gravity.TOP | Gravity.START);
        ll.setMarginStart(gap / 2);
        ll.topMargin = px(44);
        h.addView(left, ll);

        LinearLayout row = new LinearLayout(this);
        pills.clear();
        if (!hardwareInputs().isEmpty()) {
            pills.add(new Pill(pill(R.drawable.ic_input, getString(R.string.inputs)), this::showInputs));
        }
        wifiPill = pill(R.drawable.ic_wifi, getString(R.string.wifi));
        pills.add(new Pill(wifiPill, () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        pills.add(new Pill(pill(R.drawable.ic_customize, getString(R.string.customize)),
                () -> open(new Intent(this, SettingsActivity.class))));
        pills.add(new Pill(pill(R.drawable.ic_settings, getString(R.string.settings)),
                () -> open(new Intent(Settings.ACTION_SETTINGS))));
        for (int i = 0; i < pills.size(); i++) {
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) sp.setMarginStart(px(14));
            row.addView(pills.get(i).view, sp);
        }
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        pl.setMarginEnd(gap / 2);
        pl.topMargin = px(48);
        h.addView(row, pl);

        heroV = text(50, 300, Color.WHITE);
        heroV.setSingleLine(true);
        heroV.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        hl.setMarginStart(gap / 2);
        hl.bottomMargin = px(30);
        h.addView(heroV, hl);

        bindHeaderText();
        updatePills();
        updateHero(false);
        return h;
    }

    TextView pill(int icon, String label) {
        TextView t = text(17, 400, 0xB3FFFFFF);
        t.setText(label);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x24FFFFFF);
        bg.setStroke(Math.max(1, px(1)), 0x1FFFFFFF);
        bg.setCornerRadius(px(40));
        t.setBackground(bg);
        t.setPaddingRelative(px(18), px(10), px(20), px(10));
        t.setGravity(Gravity.CENTER_VERTICAL);
        Drawable d = getDrawable(icon);
        d.setBounds(0, 0, px(20), px(20));
        t.setCompoundDrawablesRelative(d, null, null, null);
        t.setCompoundDrawablePadding(px(10));
        return t;
    }

    TextView text(float sp, int wght, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(font(wght));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    Typeface font(int wght) {
        Typeface tf = fonts.get(wght);
        if (tf != null) return tf;
        tf = loadFont(this, wght);
        fonts.put(wght, tf);
        return tf;
    }

    /** Outfit (variable weight) for Latin text; Arabic glyphs fall back to the system font. */
    static Typeface loadFont(Context c, int wght) {
        Typeface tf;
        try {
            tf = new Typeface.Builder(c.getAssets(), "outfit.ttf").setFontVariationSettings("'wght' " + wght).build();
        } catch (Exception e) {
            tf = null;
        }
        return tf != null ? tf : Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    void applyWallpaper() {
        String name = Prefs.wallpaper(this);
        GradientDrawable g = Prefs.wallpaperDrawable(name);
        if (g != null) {
            root.setBackground(g);
            return;
        }
        // a custom image bundled in res/drawable-nodpi, named in config.xml
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        if (id == 0) return;
        ImageView wp = new ImageView(this);
        wp.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wp.setImageResource(id);
        root.addView(wp, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x33000000, 0xCC000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    }

    int px(float v) { return Math.round(v * dp); }

    // ---------------------------------------------------------------- list

    class TileVH extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView img;
        final TextView label;

        TileVH(LinearLayout v, FrameLayout card, ImageView img, TextView label) {
            super(v);
            this.card = card;
            this.img = img;
            this.label = label;
        }
    }

    class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() {
            return 1 + tiles.size() + (hasFiller() ? 1 : 0);
        }

        @Override public int getItemViewType(int pos) {
            if (pos == 0) return T_HEADER;
            if (hasFiller() && pos == dockCount + 1) return T_FILLER;
            return T_TILE;
        }

        int tileOf(int pos) {
            return pos <= dockCount ? pos - 1 : pos - 1 - (hasFiller() ? 1 : 0);
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == T_HEADER) return new RecyclerView.ViewHolder(buildHeader()) {};
            if (type == T_FILLER) {
                View f = new View(MainActivity.this);
                f.setLayoutParams(new RecyclerView.LayoutParams(-1, tileItemH));
                return new RecyclerView.ViewHolder(f) {};
            }
            LinearLayout item = new LinearLayout(MainActivity.this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setLayoutParams(new RecyclerView.LayoutParams(-1, tileItemH));
            item.setPadding(gap / 2, topPad, gap / 2, 0);
            item.setClipChildren(false);
            item.setClipToPadding(false);

            FrameLayout card = new FrameLayout(MainActivity.this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF1C1C1E);
            bg.setCornerRadius(px(12));
            card.setBackground(bg);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(12));
                }
            });
            card.setClipToOutline(true);
            ImageView img = new ImageView(MainActivity.this);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
            item.addView(card, new LinearLayout.LayoutParams(-1, cardH));

            TextView label = text(16, 400, Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setAlpha(0f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = px(14);
            item.addView(label, lp);
            return new TileVH(item, card, img, label);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int pos) {
            if (vh.getItemViewType() == T_HEADER) {
                bindHeaderText();
                updatePills();
                updateHero(false);
                return;
            }
            if (!(vh instanceof TileVH)) return;
            int t = tileOf(pos);
            AppEntry e = tiles.get(t);
            TileVH h = (TileVH) vh;
            if (e.banner != null) {
                h.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                h.img.setPadding(0, 0, 0, 0);
                h.img.setImageDrawable(e.banner);
            } else {
                h.img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                h.img.setPadding(0, cardH / 4, 0, cardH / 4);
                h.img.setImageDrawable(e.icon);
            }
            h.label.setText(e.label);
            setTileFocusedNow(h, !inTop && t == focusTile);
        }

        void setTileFocusedNow(TileVH h, boolean f) {
            h.card.setScaleX(f ? FOCUS_SCALE : 1f);
            h.card.setScaleY(f ? FOCUS_SCALE : 1f);
            h.label.setAlpha(f ? 1f : labelsAlways ? 0.55f : 0f);
        }
    }

    /** Slightly lighter rounded band behind the dock row; extra space above the grid. */
    class DockBand extends RecyclerView.ItemDecoration {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF band = new RectF();

        DockBand() { paint.setColor(0xD9111113); }

        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State s) {
            if (dockCount == 0) return;
            float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View v = parent.getChildAt(i);
                int pos = parent.getChildAdapterPosition(v);
                if (pos < 1 || pos > dockCount) continue;
                top = Math.min(top, v.getTop() + v.getTranslationY());
                bottom = Math.max(bottom, v.getBottom() + v.getTranslationY());
            }
            if (top == Float.MAX_VALUE) return;
            band.set(sidePad - px(26), top - px(10), screenW - sidePad + px(26), bottom + px(4));
            c.drawRoundRect(band, px(28), px(28), paint);
        }

        @Override
        public void getItemOffsets(@NonNull Rect out, @NonNull View v, @NonNull RecyclerView parent, @NonNull RecyclerView.State s) {
            int pos = parent.getChildAdapterPosition(v);
            if (pos == RecyclerView.NO_POSITION || adapter.getItemViewType(pos) != T_TILE) return;
            int t = adapter.tileOf(pos);
            if (dockCount > 0 && t >= dockCount && t < dockCount + cols) out.top = px(40);
        }
    }
}
