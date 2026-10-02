package com.shopsmart.controller;

import com.shopsmart.error.AppException;
import com.shopsmart.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> place(@RequestBody(required = false) Map<String, Object> body) {
        try {
            if (body == null) throw new AppException(400, "Cart is empty.");
            Object uid = body.get("user_id");
            int userId;
            try {
                userId = Integer.parseInt(String.valueOf(uid));
            } catch (Exception e) {
                throw new AppException(400, "Unknown user_id " + uid + ".");
            }
            List<Map<String, Object>> raw = body.get("items") instanceof List<?> l
                    ? (List<Map<String, Object>>) (List<?>) l : List.of();
            List<OrderService.CartLine> lines = new java.util.ArrayList<>();
            for (Map<String, Object> r : raw) {
                Object q = r.get("quantity");
                int qty;
                try {
                    qty = Integer.parseInt(String.valueOf(q));
                } catch (Exception e) {
                    throw new AppException(400, "Quantity must be between 1 and 99.");
                }
                lines.add(new OrderService.CartLine(String.valueOf(r.get("product_id")), qty));
            }
            return ResponseEntity.status(201).body(service.placeOrder(userId, lines));
        } catch (AppException ex) {
            return ResponseEntity.status(ex.status).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> recent(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(service.recent(limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        int orderId;
        try {
            orderId = Integer.parseInt(id);
            if (orderId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid order id."));
        }
        Map<String, Object> view = service.orderView(orderId);
        if (view == null) return ResponseEntity.status(404).body(Map.of("message", "Order not found."));
        view.put("es_in_sync", service.syncBadge(orderId, (String) view.get("updated_at")));
        return ResponseEntity.ok(view);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> setStatus(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        int orderId;
        try {
            orderId = Integer.parseInt(id);
            if (orderId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid order id."));
        }
        try {
            String status = body != null && body.get("status") != null ? String.valueOf(body.get("status")) : "";
            Map<String, Object> view = service.setStatus(orderId, status);
            if (view == null) return ResponseEntity.status(404).body(Map.of("message", "Order not found."));
            view.put("es_in_sync", true);
            return ResponseEntity.ok(view);
        } catch (AppException ex) {
            return ResponseEntity.status(ex.status).body(Map.of("message", ex.getMessage()));
        }
    }
}

