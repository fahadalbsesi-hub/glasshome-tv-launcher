package com.glasshome.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All user customisation, stored in one SharedPreferences file. */
final class Prefs {
    static final String[] WALLPAPERS = {"black", "midnight", "aurora", "sunset", "ocean", "graphite"};
    static final int[] WALLPAPER_NAMES = {R.string.wp_black, R.string.wp_midnight, R.string.wp_aurora,
            R.string.wp_sunset, R.string.wp_ocean, R.string.wp_graphite};

    private Prefs() {}

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("glass", Context.MODE_PRIVATE); }

    /** Bumped on every change so the home screen knows to rebuild. */
    static int version(Context c) { return sp(c).getInt("version", 0); }

    private static SharedPreferences.Editor edit(Context c) {
        SharedPreferences p = sp(c);
        return p.edit().putInt("version", p.getInt("version", 0) + 1);
    }

    static List<String> dock(Context c) {
        String s = sp(c).getString("dock", null);
        if (s == null) return new ArrayList<>(Arrays.asList(c.getResources().getStringArray(R.array.dock_packages)));
        List<String> out = new ArrayList<>();
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setDock(Context c, List<String> pkgs) { edit(c).putString("dock", TextUtils.join(",", pkgs)).apply(); }

    static void resetDock(Context c) { edit(c).remove("dock").apply(); }

    static Set<String> hidden(Context c) {
        Set<String> s = new HashSet<>(Arrays.asList(c.getResources().getStringArray(R.array.hidden_packages)));
        s.addAll(sp(c).getStringSet("hidden", new HashSet<>()));
        return s;
    }

    static Set<String> userHidden(Context c) { return new HashSet<>(sp(c).getStringSet("hidden", new HashSet<>())); }

    static void setHidden(Context c, Set<String> pkgs) { edit(c).putStringSet("hidden", new HashSet<>(pkgs)).apply(); }

    static String str(Context c, String key, String def) { return sp(c).getString(key, def); }

    static void putStr(Context c, String key, String v) { edit(c).putString(key, v).apply(); }

    static boolean bool(Context c, String key, boolean def) { return sp(c).getBoolean(key, def); }

    static void putBool(Context c, String key, boolean v) { edit(c).putBoolean(key, v).apply(); }

    static int columns(Context c) { return sp(c).getInt("columns", 4); }

    static void setColumns(Context c, int n) { edit(c).putInt("columns", n).apply(); }

    static String wallpaper(Context c) {
        String def = c.getString(R.string.wallpaper);
        return str(c, "wallpaper", def.isEmpty() ? "black" : def);
    }

    /** Gradient for a built-in wallpaper, or null for pitch black. */
    static GradientDrawable wallpaperDrawable(String name) {
        int[] colors;
        switch (name) {
            case "midnight": colors = new int[]{0xFF0B1026, 0xFF05070F, 0xFF000000}; break;
            case "aurora": colors = new int[]{0xFF06261F, 0xFF0A1A2E, 0xFF000000}; break;
            case "sunset": colors = new int[]{0xFF2A0B18, 0xFF1A0A06, 0xFF000000}; break;
            case "ocean": colors = new int[]{0xFF03203A, 0xFF02101E, 0xFF000000}; break;
            case "graphite": colors = new int[]{0xFF1E1E22, 0xFF101012, 0xFF000000}; break;
            default: return null;
        }
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
    }
}
