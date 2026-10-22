package com.finscore;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

/** REST API - same routes as the Flask version, so the frontend works with either backend. */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {
    private static final String[] KEYS = {"np", "eq", "debt", "ocf", "cur", "prev"};
    private final PdfExtractor extractor;
    private final ScoringService scoring;
    private final ReportRepository repo;

    public ApiController(PdfExtractor extractor, ScoringService scoring, ReportRepository repo) {
        this.extractor = extractor; this.scoring = scoring; this.repo = repo;
    }

    static Map<String, Object> err(String msg) { return Map.of("error", msg); }

    /** Convert a JSON object like {"np":408,"eq":2400,...} into Map<String, Double> (missing -> null). */
    @SuppressWarnings("unchecked")
    static Map<String, Double> inputs(Object o) {
        Map<String, Double> m = new HashMap<>();
        Map<String, Object> src = o instanceof Map ? (Map<String, Object>) o : Map.of();
        for (String k : KEYS) m.put(k, src.get(k) instanceof Number n ? n.doubleValue() : null);
        return m;
    }

    static String[] statuses(Object o) {
        if (o instanceof List<?> l && l.size() == 4) {
            String[] s = new String[4];
            for (int i = 0; i < 4; i++) s[i] = String.valueOf(l.get(i));
            return s;
        }
        return null;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "service", "FinScore backend (Spring Boot)", "reports", repo.list().size());
    }

    /** PDF in -> extracted values + FinScore out. */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        String fn = file.getOriginalFilename();
        if (file.isEmpty() || fn == null || !fn.toLowerCase().endsWith(".pdf"))
            return ResponseEntity.badRequest().body(err("Please upload a PDF file."));
        try {
            byte[] data = file.getBytes();
            if (data.length < 5 || !new String(data, 0, 5).equals("%PDF-"))
                return ResponseEntity.badRequest().body(err("This file is not a valid PDF."));
            Map<String, Object> ex = extractor.extract(data);
            if (ex == null) return ResponseEntity.unprocessableEntity().body(err("No financial statements found (scanned PDFs need OCR)."));
            @SuppressWarnings("unchecked") Map<String, Object> v = (Map<String, Object>) ex.get("v");
            Map<String, Object> flat = new LinkedHashMap<>();
            for (String k : KEYS) flat.put(k, v.get(k) == null ? null : ((Map<?, ?>) v.get(k)).get("val"));
            @SuppressWarnings("unchecked") List<String> notes = (List<String>) ex.get("notes");
            notes.add("Processed on the FinScore server (Spring Boot + Apache PDFBox).");
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("extraction", ex);
            res.put("inputs", flat);
            res.put("result", scoring.calc(inputs(flat), null));
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error",
                    "Could not read this PDF (it may be password-protected or damaged).", "detail", String.valueOf(e.getMessage())));
        }
    }

    @PostMapping("/score")
    public Map<String, Object> score(@RequestBody Map<String, Object> body) {
        return scoring.calc(inputs(body.get("inp")), null);
    }

    @GetMapping("/reports")
    public List<Map<String, Object>> reports() { return repo.list(); }

    @PostMapping("/reports")
    public ResponseEntity<?> addReport(@RequestBody Map<String, Object> r) {
        if (r == null || r.get("id") == null) return ResponseEntity.badRequest().body(err("Report id missing"));
        Map<String, Double> inp = inputs(r.get("inp"));
        String[] st = statuses(r.get("st"));
        r.put("score", scoring.calc(inp, st == null ? scoring.validate(inp) : st).get("score"));
        repo.save(r);
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("ok", true); ok.put("id", r.get("id")); ok.put("score", r.get("score"));
        return ResponseEntity.ok(ok);
    }

    @GetMapping("/report/{id}")
    public ResponseEntity<?> one(@PathVariable String id) {
        Map<String, Object> r = repo.get(id);
        return r == null ? ResponseEntity.status(404).body(err("Not found")) : ResponseEntity.ok(r);
    }

    @DeleteMapping("/report/{id}")
    public Map<String, Object> delete(@PathVariable String id) { return Map.of("deleted", repo.delete(id)); }

    /** Body: {"ids":[...2-6], "reports":[...optional]} -> ranking + best company per metric. */
    @PostMapping("/compare")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> compare(@RequestBody Map<String, Object> body) {
        List<Object> ids = body.get("ids") instanceof List<?> l ? (List<Object>) l : List.of();
        if (ids.size() < 2 || ids.size() > 6) return ResponseEntity.badRequest().body(err("Pick between 2 and 6 reports."));
        Map<String, Map<String, Object>> sent = new HashMap<>();
        if (body.get("reports") instanceof List<?> rl)
            for (Object o : rl) if (o instanceof Map<?, ?> m && m.get("id") != null) sent.put(String.valueOf(m.get("id")), (Map<String, Object>) m);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object idObj : ids) {
            String id = String.valueOf(idObj);
            Map<String, Object> r = sent.containsKey(id) ? sent.get(id) : repo.get(id);
            if (r == null) continue;
            Map<String, Double> inp = inputs(r.get("inp"));
            String[] st = statuses(r.get("st"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id); row.put("name", r.get("name")); row.put("year", r.get("year"));
            row.putAll(scoring.calc(inp, st));
            rows.add(row);
        }
        if (rows.size() < 2) return ResponseEntity.status(404).body(err("Reports not found on the server."));
        rows.sort((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")));

        List<Map<String, Object>> best = new ArrayList<>();
        for (int j = 0; j < 4; j++) {
            int bs = -1; Object winner = null;
            for (Map<String, Object> row : rows) {
                Map<String, Object> m = ((List<Map<String, Object>>) row.get("metrics")).get(j);
                if ("Valid".equals(m.get("status")) && m.get("score") != null && (Integer) m.get("score") > bs) {
                    bs = (Integer) m.get("score"); winner = row.get("name");
                }
            }
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("metric", ScoringService.METRICS[j]); b.put("winner", winner);
            best.add(b);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ranking", rows); out.put("best_per_metric", best); out.put("winner", rows.get(0).get("name"));
        return ResponseEntity.ok(out);
    }
}
