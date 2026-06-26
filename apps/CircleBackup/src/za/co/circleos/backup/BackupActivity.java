/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Backup (WP-23) - back up your contacts to the Circle cloud bridge and
 * restore them. (Files/photos sync extend on the same bridge as a follow-up.)
 */
package za.co.circleos.backup;

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BackupActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;
    private static final int REQ    = 1;

    private TextView mStatus;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Backup");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView body = new TextView(this);
        body.setText("Keep a copy of your contacts in your Circle cloud, encrypted to your "
                + "account. Files and photos sync on the same backup, coming next.");
        body.setTextColor(DIM);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(8);
        root.addView(body, blp);

        root.addView(button("Back up contacts", () -> ensureThen(true), dp(28)));
        root.addView(button("Restore contacts", () -> ensureThen(false), dp(12)));

        mStatus = new TextView(this);
        mStatus.setTextColor(ACCENT);
        mStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(24);
        root.addView(mStatus, slp);

        setContentView(scroll);
    }

    private boolean mPendingBackup;

    private void ensureThen(boolean backup) {
        mPendingBackup = backup;
        String[] perms = backup
                ? new String[]{android.Manifest.permission.READ_CONTACTS}
                : new String[]{android.Manifest.permission.WRITE_CONTACTS,
                               android.Manifest.permission.READ_CONTACTS};
        ArrayList<String> need = new ArrayList<>();
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        }
        if (need.isEmpty()) run(backup);
        else requestPermissions(need.toArray(new String[0]), REQ);
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc != REQ) return;
        boolean ok = g.length > 0;
        for (int v : g) ok &= (v == PackageManager.PERMISSION_GRANTED);
        if (ok) run(mPendingBackup);
        else mStatus.setText("Contacts permission is required.");
    }

    private void run(boolean backup) {
        mStatus.setText(backup ? "Backing up…" : "Restoring…");
        new Thread(() -> {
            try {
                if (backup) {
                    JSONArray arr = readContacts();
                    BackupBridge.putContacts(arr);
                    final int n = arr.length();
                    runOnUiThread(() -> mStatus.setText("Backed up " + n + " contacts."));
                } else {
                    JSONArray arr = BackupBridge.getContacts();
                    int n = writeContacts(arr);
                    final int fn = n;
                    runOnUiThread(() -> mStatus.setText("Restored " + fn + " contacts."));
                }
            } catch (Exception e) {
                runOnUiThread(() -> mStatus.setText("Couldn't reach the backup cloud."));
            }
        }).start();
    }

    private JSONArray readContacts() throws Exception {
        JSONArray out = new JSONArray();
        Set<Long> seen = new LinkedHashSet<>();
        String[] proj = {
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
        };
        try (Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    if (!seen.add(id)) continue;
                    String name = c.getString(1);
                    if (TextUtils.isEmpty(name)) continue;
                    JSONObject o = new JSONObject();
                    o.put("name", name);
                    o.put("number", c.getString(2));
                    out.put(o);
                }
            }
        }
        return out;
    }

    private int writeContacts(JSONArray arr) throws Exception {
        int n = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name", "");
            String number = o.optString("number", "");
            if (TextUtils.isEmpty(name)) continue;
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build());
            if (!TextUtils.isEmpty(number)) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build());
            }
            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                n++;
            } catch (Throwable t) {
                // skip a contact that fails to insert
            }
        }
        return n;
    }

    private TextView button(String label, Runnable onClick, int topMargin) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFF000000);
        t.setBackgroundColor(ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(14));
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        t.setLayoutParams(lp);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
