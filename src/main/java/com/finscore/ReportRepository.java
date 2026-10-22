package com.finscore;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** SQLite storage: each report is saved as JSON text in the "reports" table. */
@Repository
public class ReportRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper om;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    public ReportRepository(JdbcTemplate jdbc, ObjectMapper om) { this.jdbc = jdbc; this.om = om; }

    @PostConstruct
    void init() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS reports(id TEXT PRIMARY KEY, name TEXT, year TEXT, file TEXT, "
                + "score INTEGER, data TEXT NOT NULL, created INTEGER)");
    }

    public void save(Map<String, Object> r) {
        long ts = r.get("ts") instanceof Number n ? n.longValue() : 0L;
        int score = r.get("score") instanceof Number n ? n.intValue() : 0;
        jdbc.update("INSERT OR REPLACE INTO reports VALUES(?,?,?,?,?,?,?)",
                String.valueOf(r.get("id")), String.valueOf(r.get("name")), String.valueOf(r.get("year")),
                String.valueOf(r.get("file")), score, om.writeValueAsString(r), ts);
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String s : jdbc.queryForList("SELECT data FROM reports ORDER BY created DESC", String.class)) out.add(parse(s));
        return out;
    }

    public Map<String, Object> get(String id) {
        List<String> rows = jdbc.queryForList("SELECT data FROM reports WHERE id=?", String.class, id);
        return rows.isEmpty() ? null : parse(rows.get(0));
    }

    public int delete(String id) { return jdbc.update("DELETE FROM reports WHERE id=?", id); }

    private Map<String, Object> parse(String s) {
        return om.readValue(s, MAP);
    }
}
