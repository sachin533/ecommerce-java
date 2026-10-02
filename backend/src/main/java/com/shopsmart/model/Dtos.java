package com.shopsmart.model;

import java.util.List;
import java.util.Map;

public class Dtos {
    public record CartLine(String productId, int quantity) {}
    public record PlaceOrderRequest(int userId, List<CartLine> items) {}
    public record StatusRequest(String status) {}
    public record SearchRequest(
            String q, List<String> statuses,
            String date_from, String date_to,
            Double min_total, Double max_total,
            Integer page, Integer page_size) {}
    public record ProductDto(
            String sku, String title, String description, Double price,
            String category, List<String> tags, Map<String, Object> attributes,
            List<Map<String, Object>> variants, String imageUrl, Boolean active) {}
}

