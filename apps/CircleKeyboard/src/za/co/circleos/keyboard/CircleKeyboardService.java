/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Keyboard (WP-33) — a programmatic on-screen IME in true-black Metro.
 * QWERTY + symbols, one-shot/locking shift, and a predictive suggestion strip
 * backed by an on-device word list. Gesture ("swipe") typing is the depth
 * follow-up; tap + prediction is the codeable core.
 */
package za.co.circleos.keyboard;

import android.inputmethodservice.InputMethodService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public final class CircleKeyboardService extends InputMethodService {

    private static final int BG = 0xFF0A0A0A;
    private static final int KEY = 0xFF1C1C1C;
    private static final int KEY_SPECIAL = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0A6;

    private static final String[] ROW1 = {"q","w","e","r","t","y","u","i","o","p"};
    private static final String[] ROW2 = {"a","s","d","f","g","h","j","k","l"};
    private static final String[] ROW3 = {"z","x","c","v","b","n","m"};
    private static final String[] SYM1 = {"1","2","3","4","5","6","7","8","9","0"};
    private static final String[] SYM2 = {"@","#","$","_","&","-","+","(",")"};
    private static final String[] SYM3 = {"*","\"","'",":",";","!","?"};

    private boolean mShift = true;     // auto-capitalize first letter
    private boolean mCapsLock = false;
    private boolean mSymbols = false;
    private long mLastShiftTap = 0;

    private LinearLayout mRoot;
    private LinearLayout mSuggestStrip;
    private final StringBuilder mComposing = new StringBuilder();

    @Override
    public View onCreateInputView() {
        mRoot = new LinearLayout(this);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setBackgroundColor(BG);
        mRoot.setPadding(dp(3), dp(4), dp(3), dp(8));
        rebuild();
        return mRoot;
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        mComposing.setLength(0);
        mSymbols = false;
        mCapsLock = false;
        mShift = true;
        if (mRoot != null) rebuild();
    }

    private void rebuild() {
        if (mRoot == null) return;
        mRoot.removeAllViews();

        mSuggestStrip = new LinearLayout(this);
        mSuggestStrip.setOrientation(LinearLayout.HORIZONTAL);
        mSuggestStrip.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        mRoot.addView(mSuggestStrip);
        updateSuggestions();

        if (mSymbols) {
            mRoot.addView(buildCharRow(SYM1, false));
            mRoot.addView(buildCharRow(SYM2, false));
            mRoot.addView(buildSymRow3());
        } else {
            mRoot.addView(buildCharRow(ROW1, false));
            mRoot.addView(buildCharRow(ROW2, true));
            mRoot.addView(buildLetterRow3());
        }
        mRoot.addView(buildBottomRow());
    }

    /* ── row builders ── */

    private LinearLayout buildCharRow(String[] keys, boolean indent) {
        LinearLayout row = newRow();
        if (indent) row.setPadding(dp(20), 0, dp(20), 0);
        boolean up = !mSymbols && (mShift || mCapsLock);
        for (String k : keys) {
            row.addView(charKey(up ? k.toUpperCase() : k, k, 1f));
        }
        return row;
    }

    private LinearLayout buildLetterRow3() {
        LinearLayout row = newRow();
        row.addView(specialKey(mCapsLock ? "⇪" : "⇧", 1.5f, KEY_SPECIAL,
                (mShift || mCapsLock) ? ACCENT : TEXT, this::onShift));
        boolean up = mShift || mCapsLock;
        for (String k : ROW3) row.addView(charKey(up ? k.toUpperCase() : k, k, 1f));
        row.addView(specialKey("⌫", 1.5f, KEY_SPECIAL, TEXT, this::onDelete));
        return row;
    }

    private LinearLayout buildSymRow3() {
        LinearLayout row = newRow();
        for (String k : SYM3) row.addView(charKey(k, k, 1f));
        row.addView(specialKey("⌫", 1.6f, KEY_SPECIAL, TEXT, this::onDelete));
        return row;
    }

    private LinearLayout buildBottomRow() {
        LinearLayout row = newRow();
        row.addView(specialKey(mSymbols ? "ABC" : "?123", 1.6f, KEY_SPECIAL, TEXT, this::onSymbols));
        row.addView(charKey(",", ",", 1f));
        row.addView(specialKey("space", 4f, KEY, MUTED, this::onSpace));
        row.addView(charKey(".", ".", 1f));
        row.addView(specialKey("↵", 1.6f, ACCENT, TEXT, this::onEnter));
        return row;
    }

    /* ── key factories ── */

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(4);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView charKey(String display, String base, float weight) {
        TextView k = baseKey(display, KEY, TEXT, 18);
        k.setLayoutParams(weighted(weight));
        k.setOnClickListener(v -> onChar(base));
        return k;
    }

    private TextView specialKey(String label, float weight, int bg, int fg, Runnable onClick) {
        TextView k = baseKey(label, bg, fg, 15);
        k.setLayoutParams(weighted(weight));
        k.setOnClickListener(v -> onClick.run());
        return k;
    }

    private TextView baseKey(String label, int bg, int fg, int sp) {
        TextView k = new TextView(this);
        k.setText(label);
        k.setTextColor(fg);
        k.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        k.setGravity(Gravity.CENTER);
        k.setBackgroundColor(bg);
        k.setClickable(true);
        return k;
    }

    private LinearLayout.LayoutParams weighted(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, w);
        lp.leftMargin = dp(2);
        lp.rightMargin = dp(2);
        return lp;
    }

    /* ── input handling ── */

    private void onChar(String base) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String out = (!mSymbols && (mShift || mCapsLock)) ? base.toUpperCase() : base;
        ic.commitText(out, 1);
        if (isWordChar(base)) mComposing.append(out); else mComposing.setLength(0);
        if (mShift && !mCapsLock) {
            mShift = false;
            rebuild();
        } else {
            updateSuggestions();
        }
    }

    private void onDelete() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText("", 1);
        } else {
            ic.deleteSurroundingText(1, 0);
        }
        if (mComposing.length() > 0) mComposing.deleteCharAt(mComposing.length() - 1);
        updateSuggestions();
    }

    private void onShift() {
        long now = System.currentTimeMillis();
        if (now - mLastShiftTap < 320) {
            mCapsLock = true;
            mShift = true;
        } else {
            mShift = !mShift;
            mCapsLock = false;
        }
        mLastShiftTap = now;
        rebuild();
    }

    private void onSymbols() {
        mSymbols = !mSymbols;
        rebuild();
    }

    private void onSpace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.commitText(" ", 1);
        mComposing.setLength(0);
        updateSuggestions();
    }

    private void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        int action = ei != null ? (ei.imeOptions & EditorInfo.IME_MASK_ACTION)
                : EditorInfo.IME_ACTION_UNSPECIFIED;
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
                && (ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)) {
            ic.performEditorAction(action);
        } else {
            ic.commitText("\n", 1);
        }
        mComposing.setLength(0);
        updateSuggestions();
    }

    /* ── suggestions ── */

    private void updateSuggestions() {
        if (mSuggestStrip == null) return;
        mSuggestStrip.removeAllViews();
        List<String> sugg = Words.suggest(mComposing.toString(), mShift || mCapsLock, 3);
        for (String w : sugg) {
            TextView s = new TextView(this);
            s.setText(w);
            s.setTextColor(TEXT);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            s.setGravity(Gravity.CENTER);
            s.setClickable(true);
            s.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            final String word = w;
            s.setOnClickListener(v -> pickSuggestion(word));
            mSuggestStrip.addView(s);
        }
    }

    private void pickSuggestion(String w) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (mComposing.length() > 0) ic.deleteSurroundingText(mComposing.length(), 0);
        ic.commitText(w + " ", 1);
        mComposing.setLength(0);
        updateSuggestions();
    }

    private boolean isWordChar(String s) {
        if (s.length() != 1) return false;
        char c = s.charAt(0);
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
