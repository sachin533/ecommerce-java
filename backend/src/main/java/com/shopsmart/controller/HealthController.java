package com.shopsmart.controller;

import com.shopsmart.repository.OrderSearchRepository;
import com.shopsmart.repository.OrderItemRepository;
import com.shopsmart.repository.OrderRepository;
import com.shopsmart.repository.UserRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final UserRepository users;
    private final MongoTemplate mongo;
    private final OrderSearchRepository search;

    public HealthController(UserRepository users,
                            MongoTemplate mongo,
                            OrderSearchRepository search) {
        this.users = users;
        this.mongo = mongo;
        this.search = search;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, String> stores = new LinkedHashMap<>();
        try {
            users.count();
            stores.put("postgres", "up");
        } catch (Exception e) {
            stores.put("postgres", "down");
        }
        try {
            mongo.executeCommand(new org.bson.Document("ping", 1));
            stores.put("mongodb", "up");
        } catch (Exception e) {
            stores.put("mongodb", "down");
        }
        try {
            stores.put("elasticsearch", search.count() >= 0 ? "up" : "down");
        } catch (Exception e) {
            stores.put("elasticsearch", "down");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("stores", stores);
        return ResponseEntity.ok(body);
    }
}


