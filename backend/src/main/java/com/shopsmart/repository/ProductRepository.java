package com.shopsmart.repository;

import com.shopsmart.model.Product;
import com.mongodb.client.result.DeleteResult;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Repository
public class ProductRepository {

    private final MongoTemplate mongo;

    public ProductRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    private Criteria baseFilter(String category, String tag, boolean includeInactive) {
        Criteria c = new Criteria();
        if (!includeInactive) c.and("active").is(true);
        if (category != null && !category.isBlank()) c.and("category").is(category.trim());
        if (tag != null && !tag.isBlank()) c.and("tags").is(tag.trim());
        return c;
    }

    public record PagedResult(List<Product> products, int currentPage, int pageSize,
                              long totalProducts, int totalPages, boolean hasNextPage) {}

    public PagedResult list(String category, String tag, String search, int page, int limit) {
        page = Math.max(1, page);
        limit = Math.min(100, Math.max(1, limit));
        // Title OR description match (same as .NET), stable title+id ordering.
        Query q = buildQuery(category, tag, search);
        long total = mongo.count(q, Product.class);
        q.with(org.springframework.data.domain.Sort.by("title").ascending().and(Sort.by("id")));
        q.skip((long) (page - 1) * limit).limit(limit);
        List<Product> items = mongo.find(q, Product.class);
        int totalPages = (int) Math.ceil(total / (double) limit);
        return new PagedResult(items, page, limit, total, totalPages, page < totalPages);
    }

    private Query buildQuery(String category, String tag, String search) {
        Criteria filter = baseFilter(category, tag, false);
        if (search != null && !search.isBlank()) {
            String s = search.trim();
            filter.orOperator(
                    Criteria.where("title").regex(s, "i"),
                    Criteria.where("description").regex(s, "i"));
        }
        return new Query(filter);
    }

    public List<Product> listAll(String category, String tag) {
        Query q = new Query(baseFilter(category, tag, false));
        q.with(org.springframework.data.domain.Sort.by("title").ascending().and(Sort.by("id")));
        return mongo.find(q, Product.class);
    }

    public Product byId(String id) {
        if (!ObjectId.isValid(id)) return null;
        return mongo.findById(id, Product.class);
    }

    public Product create(Product p) {
        if (!ObjectId.isValid(p.id != null ? p.id : "")) p.id = new ObjectId().toHexString();
        if (p.tags == null) p.tags = List.of();
        if (p.attributes == null) p.attributes = new HashMap<>();
        if (p.variants == null) p.variants = List.of();
        p.active = true;
        if (p.updatedAt == null) p.updatedAt = LocalDateTime.now();
        return mongo.save(p);
    }

    public Product update(String id, Map<String, Object> fields) {
        if (!ObjectId.isValid(id)) return null;
        Update update = new Update().set("updated_at", LocalDateTime.now());
        for (String k : new String[]{"sku","title","description","price","category",
                "tags","attributes","variants","imageUrl","active"}) {
            if (fields.containsKey(k) && fields.get(k) != null) update.set(k, fields.get(k));
        }
        var res = mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(id))), update, Product.class);
        return res.getMatchedCount() == 0 ? null : byId(id);
    }

    public List<String> categories() {
        return mongo.getCollection("products").distinct("category", String.class)
                .into(new java.util.ArrayList<>()).stream().sorted().toList();
    }

    public long countActive() {
        return mongo.count(new Query(Criteria.where("active").is(true)), Product.class);
    }

    public long deleteBySkuPrefix(String prefix) {
        DeleteResult r = mongo.remove(
                new Query(Criteria.where("sku").regex("^" + Pattern.quote(prefix))),
                Product.class);
        return r.getDeletedCount();
    }
}


