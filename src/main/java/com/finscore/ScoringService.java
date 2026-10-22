package com.finscore;

import org.springframework.stereotype.Service;
import java.util.*;

/** Rule-based FinScore logic (Java version of scoring.py / calc() in the frontend). */
@Service
public class ScoringService {
    static final String[] METRICS = {"Profitability (ROE)", "Debt & safety (D/E)", "Cash flow strength", "Revenue growth"};

    static int clamp(long v) { return (int) Math.max(0, Math.min(100, v)); }

    /** Mark each metric Valid / Missing / Flagged. */
    public String[] validate(Map<String, Double> i) {
        Double np = i.get("np"), eq = i.get("eq"), debt = i.get("debt"), ocf = i.get("ocf"), cur = i.get("cur"), prev = i.get("prev");
        String[] st = {"Valid", "Valid", "Valid", "Valid"};
        if (np == null || eq == null) st[0] = "Missing";
        if (debt == null || eq == null) st[1] = "Missing";
        if (ocf == null) st[2] = "Missing";
        if (cur == null || prev == null) st[3] = "Missing";
        if (st[0].equals("Valid") && (eq <= 0 || Math.abs(np / eq) > 2)) st[0] = "Flagged";
        if (st[1].equals("Valid") && (eq <= 0 || debt < 0)) st[1] = "Flagged";
        if (st[3].equals("Valid") && (prev <= 0 || cur < 0)) st[3] = "Flagged";
        return st;
    }

    /** Ratios, per-metric scores and the overall FinScore (0-100). */
    public Map<String, Object> calc(Map<String, Double> i, String[] st) {
        if (st == null) st = validate(i);
        Double np = i.get("np"), eq = i.get("eq"), debt = i.get("debt"), ocf = i.get("ocf"), cur = i.get("cur"), prev = i.get("prev");
        Double roe = (np != null && eq != null && eq > 0) ? np / eq * 100 : null;
        Double de = (debt != null && eq != null && eq > 0) ? debt / eq : null;
        Double g = (cur != null && prev != null && prev > 0) ? (cur - prev) / prev * 100 : null;

        Integer[] sc = new Integer[4];
        sc[0] = roe == null ? null : clamp(Math.round(roe / 25 * 100));
        sc[1] = de == null ? null : clamp(Math.round(100 - de / 2 * 100));
        if (ocf == null) sc[2] = null;
        else if (np != null && np > 0) sc[2] = clamp(Math.round(40 + ocf / np * 50));
        else sc[2] = ocf > 0 ? 60 : 25;
        sc[3] = g == null ? null : clamp(Math.round(50 + g * 3));

        List<Map<String, Object>> metrics = new ArrayList<>();
        int sum = 0, n = 0;
        for (int k = 0; k < 4; k++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", METRICS[k]);
            m.put("status", st[k]);
            m.put("score", sc[k]);
            metrics.add(m);
            if (st[k].equals("Valid") && sc[k] != null) { sum += sc[k]; n++; }
        }
        int score = n == 0 ? 0 : (int) Math.round(sum / (double) n);
        String label = score >= 80 ? "Strong fundamental strength"
                : score >= 55 ? "Moderate fundamental strength" : "Limited fundamental strength";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score", score);
        out.put("label", label);
        out.put("roe", roe);
        out.put("de", de);
        out.put("growth", g);
        out.put("metrics", metrics);
        return out;
    }
}
