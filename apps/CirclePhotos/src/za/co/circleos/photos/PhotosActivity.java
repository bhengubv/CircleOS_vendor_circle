/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Photos (WP-21) - the local photo hub: a fast thumbnail grid over the
 * on-device MediaStore, tap to view. Cloud/social sources attach via the
 * Circle bridge as a follow-up; the local hub is complete.
 */
package za.co.circleos.photos;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PhotosActivity extends Activity {

    private static final int BG   = 0xFF000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM  = 0xB3FFFFFF;
    private static final int REQ  = 1;

    private final List<Uri> mUris = new ArrayList<>();
    private final ExecutorService mExec = Executors.newFixedThreadPool(3);
    private GridView mGrid;
    private TextView mEmpty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(40), dp(16), 0);

        TextView title = new TextView(this);
        title.setText("Photos");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        mEmpty = new TextView(this);
        mEmpty.setTextColor(DIM);
        mEmpty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mEmpty.setPadding(0, dp(40), 0, 0);
        root.addView(mEmpty);

        mGrid = new GridView(this);
        mGrid.setNumColumns(3);
        mGrid.setHorizontalSpacing(dp(4));
        mGrid.setVerticalSpacing(dp(4));
        mGrid.setPadding(0, dp(12), 0, dp(12));
        mGrid.setScrollBarSize(0);
        mGrid.setOnItemClickListener((p, v, pos, id) -> view(mUris.get(pos)));
        root.addView(mGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        String perm = (Build.VERSION.SDK_INT >= 33)
                ? android.Manifest.permission.READ_MEDIA_IMAGES
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) load();
        else requestPermissions(new String[]{perm}, REQ);
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc == REQ && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) load();
        else mEmpty.setText("Photos needs media access to show your pictures.");
    }

    private void load() {
        mUris.clear();
        Uri coll = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Images.Media._ID};
        String sort = MediaStore.Images.Media.DATE_ADDED + " DESC";
        try (Cursor c = getContentResolver().query(coll, proj, null, null, sort)) {
            if (c != null) {
                int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int n = 0;
                while (c.moveToNext() && n < 300) {
                    mUris.add(ContentUris.withAppendedId(coll, c.getLong(idCol)));
                    n++;
                }
            }
        } catch (Throwable t) {
            mEmpty.setText("Couldn't read photos.");
            return;
        }
        if (mUris.isEmpty()) {
            mEmpty.setText("No photos yet.");
            return;
        }
        mEmpty.setVisibility(View.GONE);
        mGrid.setAdapter(new Adapter());
    }

    private void view(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Throwable t) {
            // ignore
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return mUris.size(); }
        @Override public Object getItem(int p) { return mUris.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            ImageView iv = (cv instanceof ImageView)
                    ? (ImageView) cv : new ImageView(PhotosActivity.this);
            int size = parent.getWidth() / 3 - dp(4);
            if (size <= 0) size = dp(110);
            iv.setLayoutParams(new GridView.LayoutParams(size, size));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFF161616);
            iv.setImageDrawable(null);

            final Uri uri = mUris.get(pos);
            iv.setTag(uri);
            final ImageView ref = iv;
            final int px = size;
            mExec.execute(() -> {
                try {
                    final Bitmap bm = getContentResolver()
                            .loadThumbnail(uri, new Size(px, px), null);
                    runOnUiThread(() -> {
                        if (uri.equals(ref.getTag())) ref.setImageBitmap(bm);
                    });
                } catch (Throwable t) {
                    // thumbnail failed; leave the placeholder
                }
            });
            return iv;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExec.shutdownNow();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
