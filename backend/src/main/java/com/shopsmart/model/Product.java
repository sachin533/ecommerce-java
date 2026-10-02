package com.shopsmart.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document("products")
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Product {
    @Id
    public String id;
    public String sku;
    public String title;
    public String description;
    public double price;
    public String category;
    public List<String> tags = new ArrayList<>();
    public Map<String, Object> attributes = new HashMap<>();
    public List<Map<String, Object>> variants = new ArrayList<>();
    public String imageUrl;
    public boolean active = true;
    @Field("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    public LocalDateTime updatedAt;
}

