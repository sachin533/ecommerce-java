package com.shopsmart.controller;

import com.shopsmart.service.SeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SeedService seed;

    public AdminController(SeedService seed) {
        this.seed = seed;
    }

    @PostMapping("/reindex")
    public ResponseEntity<?> reindex() throws Exception {
        return ResponseEntity.ok(Map.of("indexed", seed.reindex()));
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seed() throws Exception {
        return ResponseEntity.ok(seed.seedAll());
    }

    @PostMapping("/seed-catalog")
    public ResponseEntity<?> seedCatalog(@RequestParam(defaultValue = "600") int count) throws Exception {
        return ResponseEntity.ok(seed.seedCatalog(count));
    }
}

