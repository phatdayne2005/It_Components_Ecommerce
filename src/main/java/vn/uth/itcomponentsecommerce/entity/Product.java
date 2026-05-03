package vn.uth.itcomponentsecommerce.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_active", columnList = "active"),
        @Index(name = "idx_product_category", columnList = "category_id"),
        @Index(name = "idx_product_brand", columnList = "brand_id")
})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(unique = true, length = 60)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(unique = true, length = 220)
    private String slug;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Bài đánh giá chi tiết dạng HTML (biên tập viên / admin), hiển thị trên trang sản phẩm.
     * Khác với {@link #description} (mô tả ngắn dạng text).
     */
    @Column(name = "editorial_review", columnDefinition = "LONGTEXT")
    private String editorialReview;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "old_price", precision = 14, scale = 2)
    private BigDecimal oldPrice;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(nullable = false)
    private Integer sold = 0;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductSpecification> specifications = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEditorialReview() { return editorialReview; }
    public void setEditorialReview(String editorialReview) { this.editorialReview = editorialReview; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getOldPrice() { return oldPrice; }
    public void setOldPrice(BigDecimal oldPrice) { this.oldPrice = oldPrice; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getSold() { return sold; }
    public void setSold(Integer sold) { this.sold = sold; }
    public Long getViewCount() { return viewCount; }
    public void setViewCount(Long viewCount) { this.viewCount = viewCount; }
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Brand getBrand() { return brand; }
    public void setBrand(Brand brand) { this.brand = brand; }
    public List<ProductImage> getImages() { return images; }
    public void setImages(List<ProductImage> images) { this.images = images; }
    public List<ProductSpecification> getSpecifications() { return specifications; }
    public void setSpecifications(List<ProductSpecification> specifications) { this.specifications = specifications; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
