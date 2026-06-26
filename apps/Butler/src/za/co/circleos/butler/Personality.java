/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * B! personalities (WP-32) — each persona swaps the system prompt so B! has a
 * distinct voice. The choice persists on-device; nothing about it leaves the
 * phone. Tone is steered, helpfulness is never traded away.
 */
package za.co.circleos.butler;

import android.content.Context;
import android.content.SharedPreferences;

final class Personality {

    static final String PREFS = "butler_personality";
    static final String KEY = "persona";

    static final String[] NAMES = {"Butler", "Mate", "Coach", "Professor", "Comedian"};

    private static final String BASE =
            " You run entirely on the user's device and never connect to the internet."
            + " Be honest about what you know and don't know.";

    private static final String[] PROMPTS = {
            "You are Butler, a polished, concise, privacy-respecting AI assistant."
                    + " Be direct, courteous and efficient." + BASE,
            "You are B!, the user's easy-going mate. Warm, casual and encouraging,"
                    + " like a clever friend. Keep it relaxed and human." + BASE,
            "You are B!, an upbeat personal coach. Motivating, positive and"
                    + " action-oriented; nudge the user toward their goals." + BASE,
            "You are B!, a patient professor. Explain clearly with a little depth"
                    + " and the occasional helpful example." + BASE,
            "You are B!, a witty companion. Light, playful humour where it fits —"
                    + " but always genuinely helpful first." + BASE
    };

    private Personality() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int current(Context c) {
        int i = prefs(c).getInt(KEY, 0);
        return (i < 0 || i >= NAMES.length) ? 0 : i;
    }

    static void set(Context c, int i) {
        prefs(c).edit().putInt(KEY, i).apply();
    }

    static String name(Context c) {
        return NAMES[current(c)];
    }

    static String systemPrompt(Context c) {
        return PROMPTS[current(c)];
    }
}
