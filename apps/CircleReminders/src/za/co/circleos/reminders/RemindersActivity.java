/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Reminders (WP-28) - simple time-based reminders with reliable exact
 * alarms + notifications. On-device only. Place/person triggers are follow-ups.
 */
package za.co.circleos.reminders;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;

public final class RemindersActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DIM    = 0xB3FFFFFF;

    private ReminderStore mStore;
    private List<ReminderStore.Reminder> mReminders;
    private LinearLayout mList;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mStore = new ReminderStore(this);
        ReminderReceiver.ensureChannel(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(44), dp(20), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Reminders");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView add = new TextView(this);
        add.setText("+ New");
        add.setTextColor(ACCENT);
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setPadding(dp(12), dp(8), dp(4), dp(8));
        add.setOnClickListener(v -> promptNew());
        header.addView(add);
        root.addView(header);

        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(12);
        root.addView(mList, llp);

        setContentView(scroll);

        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        mReminders = mStore.load();
        mList.removeAllViews();
        if (mReminders.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("No reminders. Tap “+ New” to add one.");
            t.setTextColor(DIM);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setPadding(0, dp(40), 0, 0);
            mList.addView(t);
            return;
        }
        for (ReminderStore.Reminder r : mReminders) mList.addView(card(r));
    }

    private View card(final ReminderStore.Reminder r) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(TILE);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        c.setClickable(true);
        c.setOnLongClickListener(v -> { confirmDelete(r); return true; });

        TextView t = new TextView(this);
        t.setText(TextUtils.isEmpty(r.text) ? "(reminder)" : r.text);
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(t);

        TextView when = new TextView(this);
        when.setText(DateUtils.getRelativeDateTimeString(this, r.time,
                DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
        when.setTextColor(ACCENT);
        when.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(4);
        c.addView(when, wlp);
        return c;
    }

    private void promptNew() {
        final EditText in = new EditText(this);
        in.setHint("Remind me to…");
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        in.setTextColor(TEXT);
        new AlertDialog.Builder(this)
                .setTitle("New reminder")
                .setView(in)
                .setPositiveButton("Set time", (d, w) -> {
                    String text = in.getText().toString().trim();
                    if (!TextUtils.isEmpty(text)) pickTime(text);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickTime(final String text) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, minute) -> {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() <= System.currentTimeMillis()) {
                c.add(Calendar.DAY_OF_YEAR, 1); // next occurrence of that time
            }
            schedule(text, c.getTimeInMillis());
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
    }

    private void schedule(String text, long time) {
        mReminders = mStore.load();
        int id = mStore.nextId(mReminders);
        ReminderStore.Reminder r = new ReminderStore.Reminder(id, text, time);
        mStore.add(mReminders, r);

        AlarmManager am = getSystemService(AlarmManager.class);
        if (am != null) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent(r));
            } catch (Throwable t) {
                am.set(AlarmManager.RTC_WAKEUP, time, pendingIntent(r));
            }
        }
        refresh();
        Toast.makeText(this, "Reminder set", Toast.LENGTH_SHORT).show();
    }

    private PendingIntent pendingIntent(ReminderStore.Reminder r) {
        Intent i = new Intent(this, ReminderReceiver.class);
        i.putExtra(ReminderReceiver.EXTRA_ID, r.id);
        i.putExtra(ReminderReceiver.EXTRA_TEXT, r.text);
        return PendingIntent.getBroadcast(this, r.id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void confirmDelete(final ReminderStore.Reminder r) {
        new AlertDialog.Builder(this)
                .setTitle("Delete reminder?")
                .setMessage(TextUtils.isEmpty(r.text) ? "(reminder)" : r.text)
                .setPositiveButton("Delete", (d, w) -> {
                    AlarmManager am = getSystemService(AlarmManager.class);
                    if (am != null) am.cancel(pendingIntent(r));
                    mStore.remove(mReminders, r.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
