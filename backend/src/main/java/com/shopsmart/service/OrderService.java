package com.shopsmart.service;

import com.shopsmart.error.AppException;
import com.shopsmart.model.Order;
import com.shopsmart.model.OrderItem;
import com.shopsmart.model.User;
import com.shopsmart.repository.OrderItemRepository;
import com.shopsmart.repository.OrderRepository;
import com.shopsmart.repository.UserRepository;
import com.shopsmart.repository.OrderSearchRepository;
import com.shopsmart.repository.ProductRepository;
import com.shopsmart.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class OrderService {

    private static final Set<String> STATUSES = Set.of("PENDING", "PROCESSING", "SHIPPED");

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final UserRepository users;
    private final ProductRepository products;
    private final OrderSearchRepository search;

    public OrderService(OrderRepository orders,
                        OrderItemRepository items,
                        UserRepository users,
                        ProductRepository products,
                        OrderSearchRepository search) {
        this.orders = orders;
        this.items = items;
        this.users = users;
        this.products = products;
        this.search = search;
    }

    public List<User> listUsers() {
        return orders_sorted(users.findAll());
    }

    private List<User> orders_sorted(List<User> in) {
        in.sort(Comparator.comparing(u -> u.id));
        return in;
    }

    public record CartLine(String productId, int quantity) {}

    @Transactional
    public Map<String, Object> placeOrder(int userId, List<CartLine> lines) {
        User user = users.findById(userId)
                .orElseThrow(() -> new AppException(400, "Unknown user_id " + userId + "."));
        if (lines == null || lines.isEmpty()) throw new AppException(400, "Cart is empty.");

        // Snapshot live Mongo documents (title/price frozen at order time).
        record Snap(String productId, String title, int qty, BigDecimal price) {}
        List<Snap> snap = new ArrayList<>();
        for (CartLine line : lines) {
            if (line.productId() == null || line.productId().isBlank())
                throw new AppException(400, "Product null is unavailable.");
            Product product = products.byId(line.productId());
            if (product == null || !product.active)
                throw new AppException(400, "Product " + line.productId() + " is unavailable.");
            if (line.quantity() < 1 || line.quantity() > 99)
                throw new AppException(400, "Quantity must be between 1 and 99.");
            snap.add(new Snap(product.id, product.title, line.quantity(),
                    BigDecimal.valueOf(product.price)));
        }

        // ONE transaction: order row + item rows, all or nothing.
        Order order = new Order();
        order.userId = userId;
        order.orderDate = OffsetDateTime.now();
        order.status = "PENDING";
        BigDecimal total = BigDecimal.ZERO;
        for (Snap s : snap) total = total.add(s.price().multiply(BigDecimal.valueOf(s.qty())));
        order.totalAmount = total.setScale(2, RoundingMode.HALF_UP);
        order.updatedAt = OffsetDateTime.now();
        order = orders.save(order);
        List<OrderItem> saved = new ArrayList<>();
        for (Snap s : snap) {
            OrderItem item = new OrderItem();
            item.orderId = order.id;
            item.productId = s.productId();
            item.title = s.title();
            item.quantity = s.qty();
            item.unitPrice = s.price();
            saved.add(items.save(item));
        }
        order.items = saved;

        // Dual-write ES (Option A). ES failure never risks money: poller recovers.
        try {
            search.indexOrder(OrderService.toDoc(order, user));
        } catch (Exception ignored) {
        }
        Map<String, Object> view = orderView(order.id);
        if (view == null) throw new AppException(500, "Internal server error.");
        return view;
    }

    public Map<String, Object> orderView(int id) {
        Optional<Order> order = orders.findById(id);
        if (order.isEmpty()) return null;
        Order o = order.get();
        o.items = items.findByOrderIdOrderById(id);
        Optional<User> user = users.findById(o.userId);
        return toView(o, user.orElse(null));
    }

    public Map<String, Object> setStatus(int id, String status) {
        if (!STATUSES.contains(status))
            throw new AppException(400, "Status must be PENDING, PROCESSING or SHIPPED.");
        Optional<Order> existing = orders.findById(id);
        if (existing.isEmpty()) return null;
        Order order = existing.get();
        order.status = status;
        order.updatedAt = OffsetDateTime.now();
        orders.save(order);
        try {
            Map<String, Object> doc = buildDoc(id);
            if (doc != null) search.indexOrder(doc);
        } catch (Exception ignored) {
        }
        Map<String, Object> view = orderView(id);
        if (view != null) view.put("es_in_sync", true);
        return view;
    }

    public Map<String, Object> buildDoc(int orderId) {
        Optional<Order> order = orders.findById(orderId);
        if (order.isEmpty()) return null;
        Order o = order.get();
        o.items = items.findByOrderIdOrderById(orderId);
        Optional<User> user = users.findById(o.userId);
        return toDoc(o, user.orElse(null));
    }

    public boolean syncBadge(int orderId, String pgUpdatedAt) {
        try {
            Map<String, Object> doc = search.getDoc(orderId);
            if (doc == null || pgUpdatedAt == null) return false;
            java.time.OffsetDateTime pg = java.time.OffsetDateTime.parse(pgUpdatedAt);
            java.time.OffsetDateTime es = java.time.OffsetDateTime.parse(
                    String.valueOf(doc.get("updatedAt")));
            return Math.abs(java.time.Duration.between(pg, es).toMillis()) < 1000;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Map<String, Object>> recent(int limit) {
        limit = Math.min(100, Math.max(1, limit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Order o : orders.findAllByOrderByIdDesc(
                org.springframework.data.domain.PageRequest.of(0, limit))) {
            Map<String, Object> v = orderView(o.id);
            if (v != null) out.add(v);
        }
        return out;
    }

    public static Map<String, Object> toView(Order order, User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", order.id);
        m.put("user_id", order.userId);
        m.put("customer_name", user != null ? user.name : null);
        m.put("customer_email", user != null ? user.email : null);
        m.put("order_date", order.orderDate != null ? order.orderDate.toString() : null);
        m.put("status", order.status);
        m.put("total_amount", order.totalAmount != null ? order.totalAmount.doubleValue() : 0.0);
        m.put("updated_at", order.updatedAt != null ? order.updatedAt.toString() : null);
        List<Object> lines = new ArrayList<>();
        if (order.items != null) {
            for (OrderItem i : order.items) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("id", i.id);
                line.put("product_id", i.productId);
                line.put("title", i.title);
                line.put("quantity", i.quantity);
                line.put("unit_price", i.unitPrice != null ? i.unitPrice.doubleValue() : 0.0);
                lines.add(line);
            }
        }
        m.put("items", lines);
        return m;
    }

    public static Map<String, Object> toDoc(Order order, User user) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("orderId", order.id);
        doc.put("orderDate", order.orderDate != null ? order.orderDate.toString() : null);
        doc.put("status", order.status);
        doc.put("totalAmount", order.totalAmount != null ? order.totalAmount.doubleValue() : 0.0);
        doc.put("updatedAt", order.updatedAt != null ? order.updatedAt.toString() : null);
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("id", order.userId);
        customer.put("name", user != null ? user.name : "");
        customer.put("email", user != null ? user.email : "");
        doc.put("customer", customer);
        List<Object> lines = new ArrayList<>();
        if (order.items != null) {
            for (OrderItem i : order.items) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("productId", i.productId);
                line.put("title", i.title);
                line.put("quantity", i.quantity);
                line.put("unitPrice", i.unitPrice != null ? i.unitPrice.doubleValue() : 0.0);
                lines.add(line);
            }
        }
        doc.put("items", lines);
        return doc;
    }
}


