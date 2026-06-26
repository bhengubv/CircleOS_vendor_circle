/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Play (#24) — the game library / launcher front-end. Lists Windows games
 * dropped into the Circle Play folder with their cover art, and drives the
 * compatibility runtime through a clean contract (broadcast intent to the
 * runtime package). The runtime itself — Box64 + Wine/Proton + DXVK + FEX — is
 * the multi-week native drop; this is the front door it plugs into.
 */
package za.co.circleos.circleplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CirclePlayActivity extends Activity {

    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;

    // The native runtime registers this package; the front-end drives it here.
    private static final String RUNTIME_PKG = "za.co.circleos.circleplay.runtime";
    private static final String ACTION_LAUNCH = "za.co.circleos.circleplay.LAUNCH";

    private final Handler mUi = new Handler(Looper.getMainLooper());
    private LinearLayout mList;
    private File mGamesDir;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mGamesDir = new File(getExternalFilesDir(null), "Games");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("Circle Play");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Your PC games, on your phone.");
        sub.setTextColor(MUTED);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setPadding(0, dp(6), 0, dp(18));
        root.addView(sub);

        root.addView(runtimeBanner());

        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        mList.setPadding(0, dp(8), 0, 0);
        root.addView(mList);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private View runtimeBanner() {
        boolean ready = runtimeInstalled();
        TextView t = new TextView(this);
        t.setText(ready ? "Runtime ready — tap a game to play."
                : "Compatibility runtime not installed yet. Your library still works; games run once the runtime drop lands.");
        t.setTextColor(ready ? ACCENT : MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setBackgroundColor(0xFF101826);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        return t;
    }

    private boolean runtimeInstalled() {
        try {
            getPackageManager().getPackageInfo(RUNTIME_PKG, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void reload() {
        mList.removeAllViews();
        List<Game> games = scan();
        if (games.isEmpty()) {
            mList.addView(emptyState());
            return;
        }
        for (Game g : games) mList.addView(gameCard(g));
    }

    private List<Game> scan() {
        List<Game> out = new ArrayList<>();
        try {
            if (mGamesDir != null && mGamesDir.isDirectory()) {
                File[] entries = mGamesDir.listFiles();
                if (entries != null) {
                    for (File e : entries) {
                        File exe = null, cover = null;
                        if (e.isDirectory()) {
                            File[] inner = e.listFiles();
                            if (inner != null) {
                                for (File f : inner) {
                                    String ln = f.getName().toLowerCase(Locale.US);
                                    if (ln.endsWith(".exe") && exe == null) exe = f;
                                    if ((ln.equals("cover.jpg") || ln.equals("cover.png"))) cover = f;
                                }
                            }
                            if (exe != null) out.add(new Game(e.getName(), exe, cover));
                        } else if (e.getName().toLowerCase(Locale.US).endsWith(".exe")) {
                            String base = e.getName().substring(0, e.getName().length() - 4);
                            File c1 = new File(mGamesDir, base + ".jpg");
                            File c2 = new File(mGamesDir, base + ".png");
                            out.add(new Game(base, e, c1.exists() ? c1 : (c2.exists() ? c2 : null)));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private View gameCard(Game g) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundColor(CARD);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setClickable(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(2);
        card.setLayoutParams(clp);

        ImageView cover = new ImageView(this);
        cover.setLayoutParams(new LinearLayout.LayoutParams(dp(64), dp(64)));
        cover.setBackgroundColor(0xFF222222);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (g.cover != null) loadCover(cover, g.cover);
        card.addView(cover);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(llp);
        TextView name = new TextView(this);
        name.setText(g.name);
        name.setTextColor(TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(name);
        TextView path = new TextView(this);
        path.setText(g.exe.getName());
        path.setTextColor(MUTED);
        path.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        col.addView(path);
        card.addView(col);

        TextView play = new TextView(this);
        play.setText("▶");
        play.setTextColor(ACCENT);
        play.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        play.setPadding(dp(12), 0, dp(8), 0);
        card.addView(play);

        card.setOnClickListener(v -> play(g));
        return card;
    }

    private void loadCover(ImageView iv, File file) {
        new Thread(() -> {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 4;
                Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), o);
                if (bmp != null) mUi.post(() -> iv.setImageBitmap(bmp));
            } catch (Throwable ignored) {
            }
        }, "cover").start();
    }

    private void play(Game g) {
        if (!runtimeInstalled()) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(g.name)
                    .setMessage("The Circle Play runtime (Box64 + Wine/Proton) isn't installed yet.\n\n"
                            + "Your library is ready — games will launch the moment the runtime drop lands.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        try {
            Intent i = new Intent(ACTION_LAUNCH).setPackage(RUNTIME_PKG);
            i.putExtra("exe", g.exe.getAbsolutePath());
            i.putExtra("title", g.name);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast("Couldn't start the runtime");
        }
    }

    private View emptyState() {
        TextView t = new TextView(this);
        String where = mGamesDir != null ? mGamesDir.getAbsolutePath() : "the Circle Play Games folder";
        t.setText("No games yet.\n\nDrop a game folder (with its .exe and an optional cover.jpg) into:\n\n"
                + where + "\n\nThen pull down to refresh.");
        t.setTextColor(MUTED);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding(dp(4), dp(36), dp(4), 0);
        return t;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
