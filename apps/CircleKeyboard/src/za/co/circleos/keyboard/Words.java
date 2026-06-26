/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * On-device word list for Circle Keyboard predictions. Ordered roughly by
 * frequency so the most common completion ranks first. Kept compact and
 * dependency-free; a larger/personalised dictionary is a follow-up.
 */
package za.co.circleos.keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Words {

    private Words() {}

    // ~250 of the most common English words, frequency-ordered.
    private static final String[] DICT = {
        "the","be","to","of","and","a","in","that","have","it","for","not","on","with","he",
        "as","you","do","at","this","but","his","by","from","they","we","say","her","she","or",
        "an","will","my","one","all","would","there","their","what","so","up","out","if","about",
        "who","get","which","go","me","when","make","can","like","time","no","just","him","know",
        "take","people","into","year","your","good","some","could","them","see","other","than",
        "then","now","look","only","come","its","over","think","also","back","after","use","two",
        "how","our","work","first","well","way","even","new","want","because","any","these","give",
        "day","most","us","is","are","was","were","been","has","had","please","thanks","thank",
        "hello","hey","yes","okay","sure","sorry","love","great","really","right","here","there",
        "today","tomorrow","tonight","morning","afternoon","evening","week","weekend","month",
        "home","house","phone","call","text","message","email","send","share","photo","video",
        "money","wallet","pay","send","bank","account","balance","data","wifi","signal","network",
        "circle","privacy","secure","mesh","offline","online","download","update","install","app",
        "friend","family","mother","father","brother","sister","child","children","people","person",
        "where","why","again","always","never","maybe","probably","actually","something","someone",
        "everything","anything","nothing","everyone","anyone","through","before","between","around",
        "should","might","must","need","feel","find","keep","leave","start","stop","help","please",
        "morning","meeting","minute","second","hour","later","soon","early","late","ready","done",
        "working","coming","going","talking","looking","trying","making","getting","running",
        "good","better","best","nice","cool","awesome","amazing","perfect","wonderful","beautiful"
    };

    static List<String> suggest(String prefix, boolean caps, int max) {
        List<String> out = new ArrayList<>();
        if (prefix == null) return out;
        String p = prefix.trim().toLowerCase(Locale.US);
        if (p.isEmpty()) return out;
        for (String w : DICT) {
            if (w.length() > p.length() && w.startsWith(p)) {
                out.add(caps ? capitalize(w) : w);
                if (out.size() >= max) break;
            }
        }
        return out;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
