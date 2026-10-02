package com.shopsmart.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch order index via the low-level RestClient with hand-built
 * JSON (mirrors the .NET HttpClient approach). Screen 3 queries go ONLY here.
 */
@Repository
public class OrderSearchRepository {

    private final RestClient rest;
    private final String index;
    private final ObjectMapper mapper = new ObjectMapper();

    public static final String MAPPING_JSON = """
        {
          "mappings": {
            "properties": {
              "orderId": { "type": "integer" },
              "orderDate": { "type": "date" },
              "status": { "type": "keyword" },
              "totalAmount": { "type": "double" },
              "updatedAt": { "type": "date" },
              "customer": { "properties": {
                "id": { "type": "integer" },
                "name": { "type": "text",
                  "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } } },
                "email": { "type": "keyword" } } },
              "items": { "properties": {
                "productId": { "type": "keyword" },
                "title": { "type": "text",
                  "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } } },
                "quantity": { "type": "integer" },
                "unitPrice": { "type": "double" } } }
            }
          }
        }
        """;

    public OrderSearchRepository(@Value("${elastic.url:http://localhost:9200}") String url,
                                 @Value("${elastic.index:orders}") String index) throws Exception {
        java.net.URI u = new java.net.URI(url);
        this.rest = RestClient.builder(
                new HttpHost(u.getHost(), u.getPort(), u.getScheme())).build();
        this.index = index;
    }

    public boolean indexExists() throws Exception {
        var res = rest.performRequest(new Request("HEAD", "/" + index));
        int code = res.getStatusLine().getStatusCode();
        if (code == 200) return true;
        if (code == 404) return false;
        throw new RuntimeException("ES index check failed: " + code);
    }

    public void ensureIndex() throws Exception {
        if (indexExists()) return;
        Request req = new Request("PUT", "/" + index);
        req.setEntity(new StringEntity(MAPPING_JSON, ContentType.APPLICATION_JSON));
        var res = rest.performRequest(req);
        if (res.getStatusLine().getStatusCode() >= 300)
            throw new RuntimeException("ES index create failed");
    }

    public void deleteIndex() throws Exception {
        Request req = new Request("DELETE", "/" + index);
        var res = rest.performRequest(req);
        int code = res.getStatusLine().getStatusCode();
        if (code != 200 && code != 404) throw new RuntimeException("ES delete failed: " + code);
    }

    public void indexOrder(Map<String, Object> doc) throws Exception {
        Request req = new Request("PUT", "/" + index + "/_doc/" + doc.get("orderId") + "?refresh=wait_for");
        req.setEntity(new StringEntity(mapper.writeValueAsString(doc), ContentType.APPLICATION_JSON));
        var res = rest.performRequest(req);
        if (res.getStatusLine().getStatusCode() >= 300)
            throw new RuntimeException("ES index failed: " + res.getStatusLine());
    }

    public int bulkIndex(List<Map<String, Object>> docs) throws Exception {
        if (docs.isEmpty()) return 0;
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> doc : docs) {
            sb.append(mapper.writeValueAsString(
                    Map.of("index", Map.of("_index", index, "_id", doc.get("orderId"))))).append('\n');
            sb.append(mapper.writeValueAsString(doc)).append('\n');
        }
        Request req = new Request("POST", "/" + index + "/_bulk?refresh=wait_for");
        req.setEntity(new StringEntity(sb.toString(), ContentType.create("application/x-ndjson")));
        var res = rest.performRequest(req);
        if (res.getStatusLine().getStatusCode() >= 300)
            throw new RuntimeException("ES bulk failed: " + res.getStatusLine());
        return docs.size();
    }

    public Map<String, Object> getDoc(int orderId) throws Exception {
        Request req = new Request("GET", "/" + index + "/_doc/" + orderId);
        try {
            var res = rest.performRequest(req);
            JsonNode root = mapper.readTree(EntityUtils.toString(res.getEntity()));
            if (!root.path("found").asBoolean(false)) return null;
            return toMap(root.path("_source"));
        } catch (org.elasticsearch.client.ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) return null;
            throw e;
        }
    }

    public long count() throws Exception {
        Request req = new Request("GET", "/" + index + "/_count");
        var res = rest.performRequest(req);
        return mapper.readTree(EntityUtils.toString(res.getEntity())).path("count").asLong();
    }

    public record SearchResult(long total, int page, int pageSize,
                               List<Map<String, Object>> items,
                               double totalRevenue, Map<String, Long> byStatus) {}

    @SuppressWarnings("unchecked")
    public SearchResult search(String q, List<String> statuses, String dateFrom, String dateTo,
                               Double minTotal, Double maxTotal, int page, int pageSize) throws Exception {
        page = Math.max(1, page);
        pageSize = Math.min(100, Math.max(1, pageSize));
        List<String> clean = statuses.stream()
                .filter(s -> s.equals("PENDING") || s.equals("PROCESSING") || s.equals("SHIPPED"))
                .toList();

        List<Object> must = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("query", q.strip());
            mm.put("fields", List.of("customer.name^2", "items.title^2", "customer.email"));
            must.add(Map.of("multi_match", mm));
        }
        List<Object> filters = new ArrayList<>();
        if (!clean.isEmpty())
            filters.add(Map.of("terms", Map.of("status", clean)));
        Map<String, Object> dateRange = new LinkedHashMap<>();
        if (dateFrom != null && !dateFrom.isBlank()) dateRange.put("gte", dateFrom);
        if (dateTo != null && !dateTo.isBlank()) dateRange.put("lte", dateTo);
        if (!dateRange.isEmpty())
            filters.add(Map.of("range", Map.of("orderDate", dateRange)));
        Map<String, Object> totalRange = new LinkedHashMap<>();
        if (minTotal != null) totalRange.put("gte", minTotal);
        if (maxTotal != null) totalRange.put("lte", maxTotal);
        if (!totalRange.isEmpty())
            filters.add(Map.of("range", Map.of("totalAmount", totalRange)));

        Object query = (must.isEmpty() && filters.isEmpty())
                ? Map.of("match_all", Map.of())
                : Map.of("bool", Map.of("must", must, "filter", filters));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("from", (page - 1) * pageSize);
        body.put("size", pageSize);
        body.put("sort", List.of(
                Map.of("orderDate", Map.of("order", "desc")),
                Map.of("orderId", "asc")));
        Map<String, Object> aggs = new LinkedHashMap<>();
        aggs.put("total_revenue", Map.of("sum", Map.of("field", "totalAmount")));
        aggs.put("by_status", Map.of("terms", Map.of("field", "status")));
        body.put("aggs", aggs);

        Request req = new Request("POST", "/" + index + "/_search");
        req.setEntity(new StringEntity(mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
        var res = rest.performRequest(req);
        JsonNode root = mapper.readTree(EntityUtils.toString(res.getEntity()));
        JsonNode hits = root.path("hits");
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode h : hits.path("hits")) items.add(toMap(h.path("_source")));
        JsonNode ag = root.path("aggregations");
        double revenue = ag.path("total_revenue").path("value").asDouble(0.0);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (JsonNode b : ag.path("by_status").path("buckets"))
            byStatus.put(b.path("key").asText(), b.path("doc_count").asLong());
        return new SearchResult(hits.path("total").path("value").asLong(),
                page, pageSize, items, revenue, byStatus);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        return mapper.convertValue(node, Map.class);
    }
}

