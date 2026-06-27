/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Play (#24, #150) — the game library / launcher front-end. Lists Windows
 * games dropped into the Circle Play folder with their cover art, and drives the
 * compatibility runtime through a clean contract (broadcast intent to the
 * runtime package). The runtime itself — Box64 + Wine/Proton + DXVK + FEX — is
 * the multi-week native drop; this is the front door it plugs into.
 *
 * Circle-native pass (#150): every game is OWNED, not licensed. Claiming a game
 * writes a device-rooted entitlement ({@link Ownership}) that needs no server to
 * prove and can be shared peer-to-peer over the mesh — own once, keep forever,
 * uncensorable. Your library never leaves the device.
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

import org.json.JSONObject;

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

    // Mesh share routes through AetherHandler (uncensorable peer-to-peer transfer).
    private static final String AETHER_PKG = "za.co.circleos.aetherhandler";
    private static final String ACTION_MESH_SHARE = "za.co.circleos.aether.action.SHARE";
    // Paid titles settle through the SDPKT wallet; drop-in games are free to claim.
    private static final String SDPKT_PKG = "za.co.circleos.sdpkt";
    private static final String ACTION_PAY = "za.co.circleos.sdpkt.action.PAY";

    private final Handler mUi = new Handler(Looper.getMainLooper());
    private LinearLayout mList;
    private File mGamesDir;
    private Ownership mOwn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mGamesDir = new File(getExternalFilesDir(null), "Games");
        mOwn = new Ownership(this);

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
        sub.setText("Games you own — forever. No account. No recall.");
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
        return isPkg(RUNTIME_PKG);
    }

    private boolean isPkg(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
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
        final String gameId = Ownership.gameId(g.name);

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

        TextView meta = new TextView(this);
        boolean owned = mOwn.isOwned(gameId);
        meta.setText(owned ? "✓ Owned · yours forever" : g.exe.getName());
        meta.setTextColor(owned ? ACCENT : MUTED);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        col.addView(meta);
        card.addView(col);

        // Ownership chip — claim it (own-once) or view the device-rooted receipt.
        final TextView chip = new TextView(this);
        styleChip(chip, owned);
        chip.setOnClickListener(v -> {
            if (mOwn.isOwned(gameId)) {
                showReceipt(g.name, gameId);
            } else {
                mOwn.claim(gameId, g.name, Ownership.SRC_CLAIMED);
                styleChip(chip, true);
                meta.setText("✓ Owned · yours forever");
                meta.setTextColor(ACCENT);
                toast("You own " + g.name + " — forever. Long-press to share over mesh.");
            }
        });
        card.addView(chip);

        TextView play = new TextView(this);
        play.setText("▶");
        play.setTextColor(ACCENT);
        play.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        play.setPadding(dp(12), 0, dp(8), 0);
        play.setOnClickListener(v -> play(g));
        card.addView(play);

        card.setOnClickListener(v -> play(g));
        card.setOnLongClickListener(v -> {
            if (mOwn.isOwned(gameId)) {
                shareOverMesh(g, gameId);
            } else {
                toast("Claim it first — then it's yours to share.");
            }
            return true;
        });
        return card;
    }

    private void styleChip(TextView chip, boolean owned) {
        chip.setText(owned ? "Receipt" : "Claim");
        chip.setTextColor(owned ? MUTED : ACCENT);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackgroundColor(owned ? 0xFF101010 : 0xFF101826);
    }

    private void showReceipt(String title, String gameId) {
        JSONObject rec = mOwn.get(gameId);
        String owner = mOwn.ownerFingerprint();
        String source = rec != null ? rec.optString("source", "claimed") : "claimed";
        String proof = rec != null ? rec.optString("proof", "") : mOwn.proof(gameId);
        String origin = rec != null ? rec.optString("origin", "") : "";
        StringBuilder msg = new StringBuilder();
        msg.append("This receipt lives on your device. No server can revoke it.\n\n");
        msg.append("Title:  ").append(title).append("\n");
        msg.append("Owner:  ").append(owner).append("   (your device / SDPKT identity)\n");
        msg.append("Source: ").append(source).append("\n");
        if (!origin.isEmpty()) msg.append("From:   ").append(origin).append("   (shared over mesh)\n");
        msg.append("\nProof:\n").append(proof);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("✓ Ownership receipt")
                .setMessage(msg.toString())
                .setPositiveButton("Done", null)
                .setNeutralButton("Share over mesh", (d, w) -> shareOverMeshById(title, gameId))
                .show();
    }

    private void shareOverMesh(Game g, String gameId) {
        shareOverMeshById(g.name, gameId, g.exe);
    }

    private void shareOverMeshById(String title, String gameId) {
        shareOverMeshById(title, gameId, null);
    }

    private void shareOverMeshById(String title, String gameId, File exe) {
        if (!isPkg(AETHER_PKG)) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Share over mesh")
                    .setMessage("AetherNet isn't available on this device yet. Once it is, you can hand a "
                            + "game and its ownership straight to a nearby Circle phone — no store, no internet.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        try {
            Intent i = new Intent(ACTION_MESH_SHARE).setPackage(AETHER_PKG);
            i.putExtra("kind", "circle-play-game");
            i.putExtra("game_id", gameId);
            i.putExtra("title", title);
            i.putExtra("owner", mOwn.ownerFingerprint());
            if (exe != null) i.putExtra("path", exe.getAbsolutePath());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast("Couldn't reach the mesh share");
        }
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
