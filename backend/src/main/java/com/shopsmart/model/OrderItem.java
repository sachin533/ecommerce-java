package com.shopsmart.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@JsonInclude(JsonInclude.Include.ALWAYS)
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;
    @Column(name = "order_id")
    public Integer orderId;
    @Column(name = "product_id")
    public String productId;
    public String title;
    public Integer quantity;
    @Column(name = "unit_price")
    public BigDecimal unitPrice;
}

