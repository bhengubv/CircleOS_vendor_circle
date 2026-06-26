/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle People (WP-18 People Hub) — one card per person, drawn from the
 * on-device contacts. Tap a card to open the contact; one-tap call or SMS.
 * No cloud, no social scraping: just your people, on your device.
 */
package za.co.circleos.people;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PeopleActivity extends Activity {

    private static final int BG      = 0xFF000000;
    private static final int TILE    = 0xFF161616;
    private static final int SLATE   = 0xFF2C3E50; // avatar circle (brand slate)
    private static final int ACCENT  = 0xFF2196F3;
    private static final int TEXT    = 0xFFFFFFFF;
    private static final int DIM      = 0xB3FFFFFF;
    private static final int REQ_CONTACTS = 1;

    private LinearLayout mList;

    static final class Person {
        long id;
        String name;
        String number;
        String lookup;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(44), dp(20), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("People");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(12);
        root.addView(mList, llp);

        setContentView(scroll);

        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            loadPeople();
        } else {
            requestPermissions(
                    new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == REQ_CONTACTS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadPeople();
            } else {
                emptyState("People needs the Contacts permission to show your people.\n"
                        + "Grant it in Settings → Privacy, then reopen People.");
            }
        }
    }

    private void loadPeople() {
        List<Person> people = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        final Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        final String[] proj = {
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
        };
        final String sort = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                + " COLLATE NOCASE ASC";
        try (Cursor c = getContentResolver().query(uri, proj, null, null, sort)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    if (!seen.add(id)) continue; // one card per person
                    String name = c.getString(1);
                    if (TextUtils.isEmpty(name)) continue;
                    Person p = new Person();
                    p.id = id;
                    p.name = name;
                    p.number = c.getString(2);
                    p.lookup = c.getString(3);
                    people.add(p);
                }
            }
        } catch (Throwable t) {
            emptyState("Couldn't read your contacts.");
            return;
        }

        mList.removeAllViews();
        if (people.isEmpty()) {
            emptyState("No people yet. Add a contact and they'll appear here.");
            return;
        }
        for (Person p : people) mList.addView(buildCard(p));
    }

    private View buildCard(final Person p) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundColor(TILE);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        card.setClickable(true);
        card.setOnClickListener(v -> openContact(p));

        // Initial avatar — slate circle, white initial
        TextView avatar = new TextView(this);
        avatar.setText(initialOf(p.name));
        avatar.setTextColor(TEXT);
        avatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackgroundColor(SLATE);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline o) {
                o.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        int sz = dp(44);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(sz, sz);
        alp.rightMargin = dp(12);
        card.addView(avatar, alp);

        TextView name = new TextView(this);
        name.setText(p.name);
        name.setTextColor(TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!TextUtils.isEmpty(p.number)) {
            card.addView(actionGlyph("☎", v -> dial(p)));     // call
            card.addView(actionGlyph("✉", v -> message(p)));  // sms
        }
        return card;
    }

    private TextView actionGlyph(String glyph, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextColor(ACCENT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        t.setClickable(true);
        t.setOnClickListener(click);
        return t;
    }

    private void openContact(Person p) {
        try {
            Uri u = ContactsContract.Contacts.getLookupUri(p.id, p.lookup);
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Throwable t) {
            toast("Can't open contact");
        }
    }

    private void dial(Person p) {
        if (TextUtils.isEmpty(p.number)) return;
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.number)));
        } catch (Throwable t) {
            toast("No dialer available");
        }
    }

    private void message(Person p) {
        if (TextUtils.isEmpty(p.number)) return;
        try {
            startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + p.number)));
        } catch (Throwable t) {
            toast("No messaging app available");
        }
    }

    private void emptyState(String msg) {
        mList.removeAllViews();
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextColor(DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(40), 0, 0);
        mList.addView(t);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static String initialOf(String name) {
        if (TextUtils.isEmpty(name)) return "?";
        String s = name.trim();
        return s.isEmpty() ? "?" : s.substring(0, 1).toUpperCase();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
