package com.shopsmart.service;

import com.shopsmart.repository.OrderItemRepository;
import com.shopsmart.repository.OrderRepository;
import com.shopsmart.repository.UserRepository;
import com.shopsmart.repository.OrderSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OrderSyncService {

    private static final Logger log = LoggerFactory.getLogger(OrderSyncService.class);

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final UserRepository users;
    private final OrderSearchRepository search;
    private final int lookbackSeconds;

    public OrderSyncService(OrderRepository orders,
                            OrderItemRepository items,
                            UserRepository users,
                            OrderSearchRepository search,
                            @Value("${sync.lookback-seconds:120}") int lookbackSeconds) {
        this.orders = orders;
        this.items = items;
        this.users = users;
        this.search = search;
        this.lookbackSeconds = lookbackSeconds;
    }

    // Option B — polling: upsert ES docs for Postgres orders changed recently.
    @Scheduled(fixedRateString = "${sync.poll-seconds:15000}")
    public void pollOnce() {
        try {
            OffsetDateTime since = OffsetDateTime.now().minusSeconds(lookbackSeconds);
            List<Integer> ids = orders.findIdsUpdatedSince(since);
            List<Map<String, Object>> docs = new ArrayList<>();
            for (Integer id : ids) {
                Map<String, Object> doc = buildDoc(id);
                if (doc != null) docs.add(doc);
            }
            if (!docs.isEmpty()) {
                search.bulkIndex(docs);
                log.info("Poller synced {} orders", docs.size());
            }
        } catch (Exception ex) {
            log.warn("Poller tick failed: {}", ex.toString());
        }
    }

    public Map<String, Object> buildDoc(int orderId) {
        var order = orders.findById(orderId);
        if (order.isEmpty()) return null;
        var o = order.get();
        o.items = items.findByOrderIdOrderById(orderId);
        var user = users.findById(o.userId).orElse(null);
        return OrderService.toDoc(o, user);
    }
}


