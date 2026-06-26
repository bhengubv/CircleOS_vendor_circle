/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Reader (WP-79) - a clean reading-view mini browser. Load a page,
 * tap "Reader" to extract the article text and restyle it for comfortable
 * reading (true-black, narrow column, large type) - which also strips the
 * scripts/ads/nav, so reading is quieter and less tracked.
 */
package za.co.circleos.reader;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class ReaderActivity extends Activity {

    private static final int BG     = 0xFF000000;
    private static final int TILE   = 0xFF161616;
    private static final int ACCENT = 0xFF2196F3;
    private static final int TEXT   = 0xFFFFFFFF;

    private EditText mUrl;
    private WebView mWeb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, dp(36), 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(6), dp(12), dp(6));

        mUrl = new EditText(this);
        mUrl.setHint("Enter a URL");
        mUrl.setSingleLine(true);
        mUrl.setTextColor(TEXT);
        mUrl.setHintTextColor(0x66FFFFFF);
        mUrl.setBackgroundColor(TILE);
        mUrl.setPadding(dp(12), dp(10), dp(12), dp(10));
        mUrl.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        mUrl.setImeOptions(EditorInfo.IME_ACTION_GO);
        mUrl.setOnEditorActionListener((v, actionId, ev) -> {
            go();
            return true;
        });
        bar.addView(mUrl, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reader = new TextView(this);
        reader.setText("Reader");
        reader.setTextColor(ACCENT);
        reader.setTypeface(Typeface.DEFAULT_BOLD);
        reader.setPadding(dp(12), dp(8), dp(8), dp(8));
        reader.setOnClickListener(v -> applyReader());
        bar.addView(reader);
        root.addView(bar);

        mWeb = new WebView(this);
        mWeb.setBackgroundColor(BG);
        WebSettings s = mWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setDomStorageEnabled(true);
        mWeb.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                v.loadUrl(r.getUrl().toString());
                return true;
            }
        });
        root.addView(mWeb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void go() {
        String u = mUrl.getText().toString().trim();
        if (u.isEmpty()) return;
        if (!u.contains("://")) u = "https://" + u;
        mWeb.loadUrl(u);
    }

    private void applyReader() {
        if (mWeb != null) mWeb.evaluateJavascript(READER_JS, null);
    }

    /** Mini-readability: keep the densest text block, drop the chrome, restyle. */
    private static final String READER_JS =
        "(function(){try{" +
        "var cands=document.querySelectorAll('article,main,section,div');" +
        "var best=document.body,score=0;" +
        "for(var i=0;i<cands.length;i++){" +
        "var ps=cands[i].querySelectorAll('p');var len=0;" +
        "for(var j=0;j<ps.length;j++){len+=(ps[j].innerText||'').length;}" +
        "if(len>score){score=len;best=cands[i];}}" +
        "var h1=document.querySelector('h1');" +
        "var title=(h1&&h1.innerText)||document.title||'';" +
        "var body='<h1>'+title+'</h1>'+best.innerHTML;" +
        "document.body.innerHTML=body;" +
        "var st=document.createElement('style');" +
        "st.textContent='html,body{background:#000 !important;color:#fff !important;}'+" +
        "'body{max-width:680px;margin:0 auto;padding:24px;font-size:19px;line-height:1.62;'+" +
        "'font-family:sans-serif;}img,video{max-width:100%;height:auto;}'+" +
        "'a{color:#2196F3;}h1{font-size:28px;line-height:1.25;margin:0 0 16px;}'+" +
        "'script,iframe,nav,header,footer,aside,form,button{display:none !important;}';" +
        "document.head.appendChild(st);" +
        "window.scrollTo(0,0);" +
        "}catch(e){}})();";

    @Override
    public void onBackPressed() {
        if (mWeb != null && mWeb.canGoBack()) mWeb.goBack();
        else super.onBackPressed();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
