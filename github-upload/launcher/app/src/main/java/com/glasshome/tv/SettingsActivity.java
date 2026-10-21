package com.glasshome.tv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** D-pad friendly list of options; OK cycles a value or opens a picker. */
public class SettingsActivity extends Activity {
    interface Row {
        String value();
        void click();
    }

    final List<TextView> values = new ArrayList<>();
    final List<Row> rows = new ArrayList<>();
    float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(96), px(48), px(96), px(48));

        TextView title = text(40, 300, Color.WHITE);
        title.setText(R.string.s_title);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.bottomMargin = px(24);
        list.addView(title, tl);

        add(list, R.string.s_dock, new Row() {
            public String value() { return ""; }
            public void click() {
                new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(R.string.s_dock)
                        .setMessage(R.string.s_dock_hint)
                        .setPositiveButton(R.string.ok, null)
                        .setNeutralButton(R.string.s_dock_reset, (d, w) -> Prefs.resetDock(SettingsActivity.this))
                        .show();
            }
        });
        add(list, R.string.s_wallpaper, new Row() {
            public String value() {
                String w = Prefs.wallpaper(SettingsActivity.this);
                for (int i = 0; i < Prefs.WALLPAPERS.length; i++)
                    if (Prefs.WALLPAPERS[i].equals(w)) return getString(Prefs.WALLPAPER_NAMES[i]);
                return w;
            }
            public void click() {
                String[] names = new String[Prefs.WALLPAPERS.length];
                for (int i = 0; i < names.length; i++) names[i] = getString(Prefs.WALLPAPER_NAMES[i]);
                pick(R.string.s_wallpaper, names, i -> Prefs.putStr(SettingsActivity.this, "wallpaper", Prefs.WALLPAPERS[i]));
            }
        });
        toggle(list, R.string.s_glow, "glow", true);
        add(list, R.string.s_columns, new Row() {
            public String value() { return String.valueOf(Prefs.columns(SettingsActivity.this)); }
            public void click() {
                int n = Prefs.columns(SettingsActivity.this);
                Prefs.setColumns(SettingsActivity.this, n >= 5 ? 3 : n + 1);
            }
        });
        add(list, R.string.s_labels, new Row() {
            public String value() {
                return getString(Prefs.bool(SettingsActivity.this, "labelsAlways", false) ? R.string.v_always : R.string.v_focus);
            }
            public void click() {
                Prefs.putBool(SettingsActivity.this, "labelsAlways", !Prefs.bool(SettingsActivity.this, "labelsAlways", false));
            }
        });
        add(list, R.string.s_clock, new Row() {
            final String[] modes = {"auto", "12", "24"};
            final int[] names = {R.string.v_auto, R.string.v_12h, R.string.v_24h};
            public String value() { return getString(names[idx()]); }
            int idx() {
                String m = Prefs.str(SettingsActivity.this, "clock", "auto");
                for (int i = 0; i < modes.length; i++) if (modes[i].equals(m)) return i;
                return 0;
            }
            public void click() { Prefs.putStr(SettingsActivity.this, "clock", modes[(idx() + 1) % modes.length]); }
        });
        toggle(list, R.string.s_greeting, "greeting", true);
        toggle(list, R.string.s_date, "date", true);
        add(list, R.string.s_wifi_name, new Row() {
            public String value() { return onOff(Prefs.bool(SettingsActivity.this, "wifiName", false) && hasLocation()); }
            public void click() {
                boolean on = !(Prefs.bool(SettingsActivity.this, "wifiName", false) && hasLocation());
                Prefs.putBool(SettingsActivity.this, "wifiName", on);
                // Android only reveals the network name to apps with location access
                if (on && !hasLocation()) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        });
        add(list, R.string.s_hidden, new Row() {
            public String value() {
                int n = Prefs.userHidden(SettingsActivity.this).size();
                return n == 0 ? getString(R.string.v_none) : String.valueOf(n);
            }
            public void click() { pickHidden(); }
        });
        add(list, R.string.s_about, new Row() {
            public String value() {
                try {
                    return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    return "";
                }
            }
            public void click() {}
        });

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.BLACK);
        sv.addView(list);
        setContentView(sv);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) { refresh(); }

    boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    String onOff(boolean b) { return getString(b ? R.string.v_on : R.string.v_off); }

    void toggle(LinearLayout list, int label, String key, boolean def) {
        add(list, label, new Row() {
            public String value() { return onOff(Prefs.bool(SettingsActivity.this, key, def)); }
            public void click() { Prefs.putBool(SettingsActivity.this, key, !Prefs.bool(SettingsActivity.this, key, def)); }
        });
    }

    interface IntConsumer { void accept(int i); }

    void pick(int title, String[] names, IntConsumer onPick) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setItems(names, (d, i) -> {
                    onPick.accept(i);
                    refresh();
                })
                .show();
    }

    void pickHidden() {
        Map<String, MainActivity.AppEntry> all = MainActivity.loadAll(this, Collections.emptySet());
        List<MainActivity.AppEntry> apps = new ArrayList<>(all.values());
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        Set<String> hidden = Prefs.userHidden(this);
        String[] names = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i).label;
            checked[i] = hidden.contains(apps.get(i).pkg);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.s_hidden)
                .setMultiChoiceItems(names, checked, (d, i, c) -> checked[i] = c)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    hidden.clear();
                    for (int i = 0; i < apps.size(); i++) if (checked[i]) hidden.add(apps.get(i).pkg);
                    Prefs.setHidden(this, hidden);
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    void add(LinearLayout list, int label, Row row) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPaddingRelative(px(24), px(14), px(24), px(14));
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0x2EFFFFFF);
        on.setStroke(Math.max(1, px(1)), 0x55FFFFFF);
        on.setCornerRadius(px(14));
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        r.setBackground(bg);

        TextView name = text(20, 400, Color.WHITE);
        name.setText(label);
        r.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text(20, 300, 0x99FFFFFF);
        r.addView(value);
        values.add(value);
        rows.add(row);
        r.setOnClickListener(v -> {
            row.click();
            refresh();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(4);
        list.addView(r, lp);
        value.setText(row.value());
        if (rows.size() == 1) r.post(r::requestFocus);
    }

    void refresh() {
        for (int i = 0; i < rows.size(); i++) values.get(i).setText(rows.get(i).value());
    }

    TextView text(float sp, int wght, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(MainActivity.loadFont(this, wght));
        t.setTextColor(color);
        return t;
    }

    int px(float v) { return Math.round(v * dp); }
}
