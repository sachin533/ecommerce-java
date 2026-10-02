package com.shopsmart.repository;

import com.shopsmart.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    @Query("SELECT o.id FROM ShopOrder o WHERE o.updatedAt >= :since ORDER BY o.id")
    List<Integer> findIdsUpdatedSince(OffsetDateTime since);

    @Query("SELECT o.id FROM ShopOrder o ORDER BY o.id")
    List<Integer> findAllIds();

    List<Order> findAllByOrderByIdDesc(org.springframework.data.domain.Pageable pageable);
}

