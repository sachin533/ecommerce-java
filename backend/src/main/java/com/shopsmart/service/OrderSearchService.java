package com.shopsmart.service;

import com.shopsmart.repository.OrderSearchRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderSearchService {

    private final OrderSearchRepository search;

    public OrderSearchService(OrderSearchRepository search) {
        this.search = search;
    }

    public Map<String, Object> search(String q, List<String> statuses,
                                      String dateFrom, String dateTo,
                                      Double minTotal, Double maxTotal,
                                      int page, int pageSize) throws Exception {
        OrderSearchRepository.SearchResult r = search.search(
                q == null ? "" : q, statuses == null ? List.of() : statuses,
                dateFrom, dateTo, minTotal, maxTotal, page, pageSize);
        List<Object> items = new ArrayList<>();
        for (Map<String, Object> d : r.items()) items.add(toSnake(d));
        Map<String, Object> aggs = new LinkedHashMap<>();
        aggs.put("total_revenue", r.totalRevenue());
        aggs.put("by_status", r.byStatus());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", r.total());
        out.put("page", r.page());
        out.put("page_size", r.pageSize());
        out.put("items", items);
        out.put("aggs", aggs);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toSnake(Map<String, Object> d) {
        Map<String, Object> customer = (Map<String, Object>) d.getOrDefault("customer", Map.of());
        List<Object> items = new ArrayList<>();
        Object rawItems = d.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> i = (Map<String, Object>) o;
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("product_id", i.get("productId"));
                line.put("title", i.get("title"));
                line.put("quantity", i.get("quantity"));
                line.put("unit_price", i.get("unitPrice"));
                items.add(line);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order_id", d.get("orderId"));
        out.put("order_date", d.get("orderDate"));
        out.put("status", d.get("status"));
        out.put("total_amount", d.get("totalAmount"));
        out.put("updated_at", d.get("updatedAt"));
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", customer.get("id"));
        c.put("name", customer.get("name"));
        c.put("email", customer.get("email"));
        out.put("customer", c);
        out.put("items", items);
        return out;
    }
}

