package com.finscore;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/**
 * Reads an annual-report PDF with Apache PDFBox and finds net profit, equity, debt,
 * operating cash flow and revenue (Java version of extractor.py / extractPdf() in the frontend).
 */
@Component
public class PdfExtractor {

    record Unit(double u, String n, boolean d) {}
    record Word(double x0, double x1, double y, String t) {}
    static class Page {
        int p; List<List<String>> lines; Unit un; boolean fs, cons;
    }
    record Pick(int sc, double[] vals, int p, Page page) {}

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Map<String, Double> UNITS = Map.of("crore", 1.0, "lakh", .01, "million", .1, "billion", 100.0, "thousand", 1e-4);
    private static final Pattern UNIT_RX = Pattern.compile("(?:₹|rs\\.?|inr|in)\\s*(?:in\\s*)?(crores?|lakhs?|millions?|billions?|thousands?)\\b", CI);
    private static final Pattern NUM_RX = Pattern.compile("^\\(?-?[\\d,]+(?:\\.\\d+)?\\)?$");
    private static final Pattern STMT_RX = Pattern.compile("balance sheet|statement of profit|statements? of cash|cash flows?|income statement|profit and loss|statement of financial position", CI);
    private static final Pattern ONE_CELL = Pattern.compile("^([A-Za-z][^\\d(]*?[A-Za-z)\\]])\\s+(\\(?-?[\\d,]+(?:\\.\\d+)?\\)?(?:\\s+\\(?-?[\\d,]+(?:\\.\\d+)?\\)?)*)$");

    /** Collects every word of a page with its x / y position. */
    static class Collector extends PDFTextStripper {
        final List<Word> words = new ArrayList<>();
        Collector() throws IOException { setSortByPosition(true); }

        @Override
        protected void writeString(String text, List<TextPosition> pos) throws IOException {
            StringBuilder sb = new StringBuilder();
            double x0 = 0, x1 = 0, y = 0;
            for (TextPosition tp : pos) {
                String u = tp.getUnicode();
                if (u == null || u.isBlank()) { flush(sb, x0, x1, y); continue; }
                if (sb.length() == 0) { x0 = tp.getXDirAdj(); y = tp.getYDirAdj(); }
                x1 = tp.getXDirAdj() + tp.getWidthDirAdj();
                sb.append(u);
            }
            flush(sb, x0, x1, y);
        }
        private void flush(StringBuilder sb, double x0, double x1, double y) {
            if (sb.length() > 0) { words.add(new Word(x0, x1, y, sb.toString())); sb.setLength(0); }
        }
    }

    // ---------- number helpers ----------
    static Double pnum(String t) {
        t = t.trim();
        if (t.matches("[-–—]")) return 0.0;
        if (!NUM_RX.matcher(t).matches()) return null;
        String digits = t.replaceAll("[^\\d.]", "");
        if (digits.isEmpty()) return null;
        try {
            double v = Double.parseDouble(digits);
            return (t.startsWith("(") || t.startsWith("-")) ? -v : v;
        } catch (NumberFormatException e) { return null; }
    }

    static List<Double> rowVals(List<String> line) {
        List<Double> nums = new ArrayList<>();
        for (int i = 1; i < line.size(); i++)
            for (String tok : line.get(i).split("\\s+")) { Double v = pnum(tok); if (v != null) nums.add(v); }
        if (nums.size() >= 3 && nums.get(0) == Math.floor(nums.get(0)) && Math.abs(nums.get(0)) < 100) nums.remove(0); // drop "Note no."
        return nums;
    }

    // ---------- page -> lines of cells ----------
    static List<List<String>> toLines(List<Word> words) {
        List<List<Word>> rows = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (Word w : words) {
            int found = -1;
            for (int k = rows.size() - 1; k >= 0; k--) if (Math.abs(ys.get(k) - w.y()) < 2.5) { found = k; break; }
            if (found < 0) { rows.add(new ArrayList<>(List.of(w))); ys.add(w.y()); } else rows.get(found).add(w);
        }
        Integer[] order = new Integer[rows.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> ys.get(i)));

        List<List<String>> lines = new ArrayList<>();
        for (int idx : order) {
            List<Word> r = rows.get(idx);
            r.sort(Comparator.comparingDouble(Word::x0));
            List<String> cells = new ArrayList<>();
            Double end = null;
            for (Word w : r) {
                if (end != null && !cells.isEmpty() && w.x0() - end < 8)
                    cells.set(cells.size() - 1, cells.get(cells.size() - 1) + (w.x0() - end > 1.2 ? " " : "") + w.t());
                else cells.add(w.t());
                end = w.x1();
            }
            List<String> clean = new ArrayList<>();
            for (String c : cells) { String s = c.replaceAll("\\s+", " ").trim(); if (!s.isEmpty()) clean.add(s); }
            if (clean.size() == 1) {
                Matcher m = ONE_CELL.matcher(clean.get(0));
                if (m.find()) clean = new ArrayList<>(List.of(m.group(1), m.group(2)));
            }
            if (!clean.isEmpty()) lines.add(clean);
        }
        return lines;
    }

    // ---------- label search ----------
    static Pick pick(List<Page> pages, String rx, String skip, int need) {
        Pattern p = Pattern.compile(rx, CI), s = skip == null ? null : Pattern.compile(skip, CI);
        Pick best = null;
        for (Page P : pages)
            for (List<String> l : P.lines) {
                String lab = l.get(0);
                if (!p.matcher(lab).find() || (s != null && s.matcher(lab).find())) continue;
                List<Double> n = rowVals(l);
                if (n.size() < need) continue;
                int sc = (P.fs ? 2 : 0) + (P.cons ? 1 : 0);
                if (best == null || sc > best.sc()) {
                    double[] v = new double[need];
                    for (int i = 0; i < need; i++) v[i] = Math.round(n.get(i) * P.un.u() * 100) / 100.0;
                    best = new Pick(sc, v, P.p, P);
                }
            }
        return best;
    }

    static Map<String, Object> val(double v, int pg) {
        Map<String, Object> m = new LinkedHashMap<>(); m.put("val", v); m.put("pg", pg); return m;
    }

    // ---------- main ----------
    public Map<String, Object> extract(byte[] pdf) throws IOException {
        List<Page> pages = new ArrayList<>();
        Unit un = new Unit(1, "crore", false);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int total = Math.min(doc.getNumberOfPages(), 800);
            for (int i = 1; i <= total; i++) {
                Collector col = new Collector();
                col.setStartPage(i); col.setEndPage(i);
                try { col.getText(doc); } catch (Exception e) { continue; }
                List<List<String>> lines = toLines(col.words);
                for (List<String> c : lines) {
                    String t = String.join(" ", c);
                    if (t.length() < 100) {
                        Matcher m = UNIT_RX.matcher(t);
                        if (m.find()) {
                            String n = m.group(1).toLowerCase().replaceAll("s$", "");
                            un = new Unit(UNITS.get(n), n, true);
                            break;
                        }
                    }
                }
                StringBuilder head = new StringBuilder();
                for (int k = 0; k < Math.min(12, lines.size()); k++) head.append(String.join(" ", lines.get(k))).append(' ');
                Page P = new Page();
                P.p = i; P.lines = lines; P.un = un;
                P.fs = STMT_RX.matcher(head).find();
                P.cons = Pattern.compile("consolidated", CI).matcher(head).find();
                pages.add(P);
            }
        }

        List<String> notes = new ArrayList<>();
        Pick np = pick(pages, "^(?:(?:profit|net profit|net income)\\b.*(?:for the (?:year|period)|after tax)|profit after tax|net profit$|net income$)",
                "before|attributable|per share|comprehensive|discontinu|margin|%", 1);
        Pick eq = pick(pages, "^(?:total equity(?!\\s+and)|total shareholders['’]?\\s*(?:funds|equity)|shareholders['’]?\\s*(?:funds|equity)|net worth|total equity attributable)", "liabilit", 1);
        Pick ocf = pick(pages, "^(?:net cash.*operating activities|net cash (?:inflow|outflow|generated|provided).*operat|cash (?:flows? )?(?:generated|provided) (?:from|by) operating activities)", "before|preceding", 1);
        Pick rev = pick(pages, "^(?:total )?revenue from operations", "other", 2);
        if (rev == null) rev = pick(pages, "^(?:net sales|total revenue|revenue$|income from operations|sales\\b)", "other|%", 2);
        if (rev == null) rev = pick(pages, "^total income", null, 2);

        Map<String, Object> debt = null;
        Pick dt = pick(pages, "^total (?:debt|borrowings?)\\b", null, 1);
        if (dt != null) debt = val(dt.vals()[0], dt.p());
        else if (eq != null) {
            Page P = eq.page();
            Pattern inc = Pattern.compile("^(?:(?:non-?current|current|short[- ]term|long[- ]term)\\s+)?borrowings?\\b|^current maturities of (?:long[- ]term )?(?:debt|borrowings)", CI);
            Pattern exc = Pattern.compile("cost|repay|proceeds|paid|lease", CI);
            Set<String> seen = new HashSet<>();
            double sum = 0; boolean hit = false;
            for (List<String> l : P.lines) {
                if (!inc.matcher(l.get(0)).find() || exc.matcher(l.get(0)).find()) continue;
                List<Double> n = rowVals(l);
                if (n.isEmpty()) continue;
                if (!seen.add(l.get(0) + "|" + n.get(0))) continue;
                sum += n.get(0) * P.un.u(); hit = true;
            }
            debt = val(Math.round(sum * 100) / 100.0, P.p);
            if (!hit) notes.add("No borrowings line was found on the balance sheet page, so total debt is treated as nil. Please verify.");
        }
        if (np == null && eq == null && debt == null && ocf == null && rev == null) return null;

        List<Pick> sel = new ArrayList<>();
        for (Pick x : new Pick[]{np, eq, ocf, rev}) if (x != null) sel.add(x);
        long nc = sel.stream().filter(x -> x.page().cons).count();
        notes.add(0, (nc * 2 >= sel.size() && nc > 0) ? "Statements used: consolidated."
                : "Statements used: standalone or not labelled; consolidated pages were not found for every value.");
        boolean anyUnit = sel.stream().anyMatch(x -> x.page().un.d());
        if (!anyUnit) notes.add("No unit label (such as ₹ in crore) was found near the statements; values are assumed to be in ₹ crore.");
        else {
            Set<String> o = new TreeSet<>();
            for (Pick x : sel) if (x.page().un.d() && x.page().un.u() != 1) o.add(x.page().un.n());
            notes.add(o.isEmpty() ? "Values are reported in ₹ crore." : "Values reported in " + String.join(", ", o) + " were converted to ₹ crore.");
        }

        // company name + year from the first 5 pages
        StringBuilder first = new StringBuilder();
        String name = null;
        Pattern nameRx = Pattern.compile("([A-Z][A-Za-z0-9&.,'’\\- ]{2,50}?\\s(?:Limited|Ltd\\.?))(?![A-Za-z])");
        Pattern badName = Pattern.compile("annual report|notes?\\b|board|statement|auditor|certif|report of", CI);
        for (int i = 0; i < Math.min(5, pages.size()); i++)
            for (List<String> c : pages.get(i).lines) {
                String t = String.join(" ", c);
                first.append(t).append('\n');
                if (name == null) {
                    Matcher m = nameRx.matcher(t);
                    if (m.find() && !badName.matcher(m.group(1)).find()) name = m.group(1).trim();
                }
            }
        if (name != null && name.equals(name.toUpperCase())) {
            StringBuilder sb = new StringBuilder();
            for (String w : name.toLowerCase().split(" ")) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            name = sb.toString().trim();
        }
        String year = null;
        Matcher ym = Pattern.compile("annual report\\s*(?:for\\s*)?(?:fy\\s*)?(20\\d\\d)(?:\\s*[-–]\\s*(\\d\\d))?", CI).matcher(first);
        if (ym.find()) year = ym.group(1) + (ym.group(2) != null ? "-" + ym.group(2) : "");
        else {
            Matcher y2 = Pattern.compile("\\b(20\\d\\d)\\s*[-–]\\s*(\\d\\d)\\b").matcher(first);
            if (y2.find()) year = y2.group(1) + "-" + y2.group(2);
        }

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("np", np == null ? null : val(np.vals()[0], np.p()));
        v.put("eq", eq == null ? null : val(eq.vals()[0], eq.p()));
        v.put("debt", debt);
        v.put("ocf", ocf == null ? null : val(ocf.vals()[0], ocf.p()));
        v.put("cur", rev == null ? null : val(rev.vals()[0], rev.p()));
        v.put("prev", rev == null ? null : val(rev.vals()[1], rev.p()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("year", year);
        out.put("notes", notes);
        out.put("v", v);
        return out;
    }
}
