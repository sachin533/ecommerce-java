package com.shopsmart.controller;

import com.shopsmart.service.OrderSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final OrderSearchService search;

    public SearchController(OrderSearchService search) {
        this.search = search;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> search(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) body = Map.of();
        String q = body.get("q") != null ? String.valueOf(body.get("q")) : "";
        List<String> statuses = body.get("statuses") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        try {
            return ResponseEntity.ok(search.search(
                    q, statuses,
                    strOrNull(body.get("date_from")), strOrNull(body.get("date_to")),
                    dblOrNull(body.get("min_total")), dblOrNull(body.get("max_total")),
                    intOr(body.get("page"), 1), intOr(body.get("page_size"), 20)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Internal server error."));
        }
    }

    private static String strOrNull(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Double dblOrNull(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int intOr(Object v, int def) {
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }
}

