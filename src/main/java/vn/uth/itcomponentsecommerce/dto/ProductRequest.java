package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductRequest {

    @NotBlank
    private String name;

    private String slug;
    private String sku;
    private String shortDescription;
    private String description;
    /** HTML đánh giá chi tiết (do admin nhập); có thể chứa &lt;h2&gt;, &lt;p&gt;, &lt;img&gt;, … */
    private String editorialReview;

    @NotNull
    @PositiveOrZero
    private BigDecimal price;

    private BigDecimal oldPrice;

    @PositiveOrZero
    private Integer stock = 0;

    private Integer warrantyMonths;
    private String imageUrl;
    private boolean active = true;
    private Long categoryId;
    private Long brandId;

    private List<String> imageUrls = new ArrayList<>();
    private List<SpecItem> specifications = new ArrayList<>();

    public static class SpecItem {
        private String name;
        private String value;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
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
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public List<SpecItem> getSpecifications() { return specifications; }
    public void setSpecifications(List<SpecItem> specifications) { this.specifications = specifications; }
}
