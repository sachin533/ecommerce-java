package com.shopsmart.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "ShopOrder")
@Table(name = "orders")
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;
    @Column(name = "user_id")
    public Integer userId;
    @Column(name = "order_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    public OffsetDateTime orderDate;
    public String status;
    @Column(name = "total_amount")
    public BigDecimal totalAmount;
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    public OffsetDateTime updatedAt;
    @Transient
    public List<OrderItem> items = new ArrayList<>();
}

