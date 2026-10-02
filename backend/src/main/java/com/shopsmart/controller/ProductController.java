package com.shopsmart.controller;

import com.shopsmart.model.Product;
import com.shopsmart.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    // Paged envelope when page/limit/search given, else legacy array.
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        if (page == null && limit == null && (search == null || search.isBlank()))
            return ResponseEntity.ok(products.listAll(category, tag));
        return ResponseEntity.ok(products.list(category, tag, search, page == null ? 1 : page, limit == null ? 20 : limit));
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return products.categories();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        Product p = products.byId(id);
        return p == null
                ? ResponseEntity.status(404).body(Map.of("message", "Product not found."))
                : ResponseEntity.ok(p);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> dto) {
        String sku = str(dto.get("sku")), title = str(dto.get("title")), category = str(dto.get("category"));
        if (sku.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "sku is required."));
        if (title.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "title is required."));
        if (category.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "category is required."));
        Product p = new Product();
        p.sku = sku; p.title = title;
        p.description = str(dto.getOrDefault("description", ""));
        p.price = num(dto.get("price"));
        p.category = category;
        p.tags = strList(dto.get("tags"));
        p.attributes = objMap(dto.get("attributes"));
        p.variants = listOfMaps(dto.get("variants"));
        p.imageUrl = dto.get("imageUrl") != null ? String.valueOf(dto.get("imageUrl")) : null;
        p.active = dto.get("active") == null || Boolean.parseBoolean(String.valueOf(dto.get("active")));
        p.updatedAt = java.time.LocalDateTime.now();
        return ResponseEntity.status(201).body(products.create(p));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, Object> dto) {
        Product p = products.update(id, dto != null ? dto : new HashMap<>());
        return p == null
                ? ResponseEntity.status(404).body(Map.of("message", "Product not found."))
                : ResponseEntity.ok(p);
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static double num(Object v) {
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0.0;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object v) {
        if (v instanceof List<?> l) {
            List<String> out = new java.util.ArrayList<>();
            for (Object o : l) out.add(String.valueOf(o));
            return out;
        }
        return new java.util.ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
            return out;
        }
        return new java.util.LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object v) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        if (v instanceof List<?> l)
            for (Object o : l) if (o instanceof Map<?, ?> m) out.add(objMap(m));
        return out;
    }
}

