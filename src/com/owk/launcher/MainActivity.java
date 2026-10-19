package com.owk.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_WALLPAPER = 1;
    private static final int COLUMNS = 6;

    static class App {
        String pkg;
        ComponentName component;
        String label;
        Drawable banner;
        Drawable icon;
    }

    private SharedPreferences prefs;
    private ImageView background;
    private ImageView gear;
    private ScrollView scroll;
    private LinearLayout grid;
    private final List<App> apps = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();
    private String signature = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("owk", MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);

        background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(background, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x80000000, 0x00000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, dp(200)));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setPadding(dp(30), dp(20), dp(30), 0);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        top.setPadding(0, 0, dp(12), 0);

        gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setPadding(dp(10), dp(10), dp(10), dp(10));
        gear.setFocusable(true);
        gear.setClickable(true);
        gear.setContentDescription("Settings");
        GradientDrawable halo = new GradientDrawable();
        halo.setShape(GradientDrawable.OVAL);
        halo.setColor(0x55FFFFFF);
        StateListDrawable gearBg = new StateListDrawable();
        gearBg.addState(new int[]{android.R.attr.state_focused}, halo);
        gear.setBackground(gearBg);
        gear.setOnClickListener(v -> showSettingsMenu());
        top.addView(gear, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("h:mm a");
        styleText(clock, 26);
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(-2, -2);
        clockLp.leftMargin = dp(18);
        top.addView(clock, clockLp);
        column.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("Apps");
        styleText(title, 22);
        title.setPadding(dp(12), 0, 0, 0);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.topMargin = dp(24);
        titleLp.bottomMargin = dp(6);
        column.addView(title, titleLp);

        scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(dp(12), dp(12), dp(12), dp(40));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        scroll.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        loadWallpaper();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(false, focusedPackage());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: jump back to the first app.
        scroll.smoothScrollTo(0, 0);
        focusIndex(0);
    }

    @Override
    public void onBackPressed() {
        // A launcher has nowhere to go back to.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            int i = cards.indexOf(getCurrentFocus());
            if (i >= 0 && i < apps.size()) {
                showAppMenu(apps.get(i).pkg);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void refresh(boolean force, String focusPkg) {
        loadApps();
        StringBuilder sig = new StringBuilder();
        for (App a : apps) sig.append(a.pkg).append(',');
        if (!force && sig.toString().equals(signature)) return;
        signature = sig.toString();
        buildGrid();
        focusIndex(Math.max(0, indexOf(focusPkg)));
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Set<String> hidden = prefs.getStringSet("hidden", Collections.<String>emptySet());
        Map<String, App> found = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai.packageName.equals(getPackageName()) || hidden.contains(ai.packageName)
                    || found.containsKey(ai.packageName)) continue;
            App a = new App();
            a.pkg = ai.packageName;
            a.component = new ComponentName(ai.packageName, ai.name);
            a.label = String.valueOf(ri.loadLabel(pm));
            a.banner = ai.loadBanner(pm);
            if (a.banner == null) a.banner = ai.applicationInfo.loadBanner(pm);
            a.icon = ri.loadIcon(pm);
            found.put(a.pkg, a);
        }

        apps.clear();
        for (String pkg : prefs.getString("order", "").split(",")) {
            App a = found.remove(pkg);
            if (a != null) apps.add(a);
        }
        List<App> rest = new ArrayList<>(found.values());
        Collections.sort(rest, (x, y) -> x.label.compareToIgnoreCase(y.label));
        apps.addAll(rest);
    }

    private void buildGrid() {
        grid.removeAllViews();
        cards.clear();
        int gap = dp(20);
        int avail = getResources().getDisplayMetrics().widthPixels - dp(30) * 2 - dp(12) * 2;
        int w = (avail - gap * (COLUMNS - 1)) / COLUMNS;
        int h = w * 9 / 16;

        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.bottomMargin = gap;
                grid.addView(row, rowLp);
            }
            View card = makeCard(apps.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            if (i % COLUMNS != COLUMNS - 1) lp.rightMargin = gap;
            row.addView(card, lp);
            cards.add(card);
        }
    }

    private View makeCard(App app) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(app.label);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(0xFF2B2F36);
        card.setBackground(bg);
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);

        if (app.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(app.banner);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            box.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView tv = new TextView(this);
            tv.setText(app.label);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(dp(8), dp(6), dp(8), 0);
            box.addView(tv, new LinearLayout.LayoutParams(-2, -2));
            card.addView(box, new FrameLayout.LayoutParams(-1, -1));
        }

        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(8));
        ring.setStroke(dp(3), Color.WHITE);
        StateListDrawable fg = new StateListDrawable();
        fg.addState(new int[]{android.R.attr.state_focused}, ring);
        card.setForeground(fg);

        card.setOnFocusChangeListener((v, focused) -> v.animate()
                .scaleX(focused ? 1.1f : 1f)
                .scaleY(focused ? 1.1f : 1f)
                .translationZ(focused ? dp(10) : 0)
                .setDuration(140)
                .start());
        final String pkg = app.pkg;
        card.setOnClickListener(v -> launch(pkg));
        card.setOnLongClickListener(v -> {
            showAppMenu(pkg);
            return true;
        });
        return card;
    }

    private void launch(String pkg) {
        int i = indexOf(pkg);
        if (i < 0) return;
        App app = apps.get(i);
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(app.component);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Can't open " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppMenu(String pkg) {
        int idx = indexOf(pkg);
        if (idx < 0) return;
        String[] items = {"Move left", "Move right", "Hide", "App info"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(apps.get(idx).label)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                        case 1:
                            int to = idx + (which == 0 ? -1 : 1);
                            if (to < 0 || to >= apps.size()) return;
                            Collections.swap(apps, idx, to);
                            saveOrder();
                            refresh(true, pkg);
                            break;
                        case 2:
                            Set<String> hidden = new HashSet<>(
                                    prefs.getStringSet("hidden", Collections.<String>emptySet()));
                            hidden.add(pkg);
                            prefs.edit().putStringSet("hidden", hidden).apply();
                            String next = idx + 1 < apps.size() ? apps.get(idx + 1).pkg
                                    : idx > 0 ? apps.get(idx - 1).pkg : null;
                            refresh(true, next);
                            break;
                        case 3:
                            safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", pkg, null)));
                            break;
                    }
                })
                .show();
    }

    private void showSettingsMenu() {
        String[] items = {"Android settings", "Change wallpaper", "Default wallpaper", "Show hidden apps"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Owk Launcher")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            safeStart(new Intent(Settings.ACTION_SETTINGS));
                            break;
                        case 1:
                            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("image/*");
                            try {
                                startActivityForResult(pick, REQ_WALLPAPER);
                            } catch (RuntimeException e) {
                                Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show();
                            }
                            break;
                        case 2:
                            new File(getFilesDir(), "wallpaper").delete();
                            loadWallpaper();
                            break;
                        case 3:
                            prefs.edit().remove("hidden").apply();
                            refresh(true, focusedPackage());
                            break;
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_WALLPAPER || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try {
            Bitmap bmp = decodeScaled(data.getData());
            try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "wallpaper"))) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
            }
            loadWallpaper();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't use that image", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap decodeScaled(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int target = Math.max(getResources().getDisplayMetrics().widthPixels, 1920);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            if (bmp == null) throw new Exception("Undecodable image");
            return bmp;
        }
    }

    private void loadWallpaper() {
        File f = new File(getFilesDir(), "wallpaper");
        if (f.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(f.getPath());
            if (bmp != null) {
                background.setImageBitmap(bmp);
                return;
            }
        }
        background.setImageResource(R.drawable.wallpaper);
    }

    private void saveOrder() {
        StringBuilder sb = new StringBuilder();
        for (App a : apps) sb.append(a.pkg).append(',');
        prefs.edit().putString("order", sb.toString()).apply();
    }

    private void safeStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    private String focusedPackage() {
        int i = cards.indexOf(getCurrentFocus());
        return i >= 0 && i < apps.size() ? apps.get(i).pkg : null;
    }

    private int indexOf(String pkg) {
        if (pkg == null) return -1;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).pkg.equals(pkg)) return i;
        }
        return -1;
    }

    private void focusIndex(int idx) {
        if (cards.isEmpty()) {
            gear.requestFocus();
            return;
        }
        View target = cards.get(Math.min(idx, cards.size() - 1));
        target.post(target::requestFocus);
    }

    private void styleText(TextView t, int sp) {
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
