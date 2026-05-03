package vn.uth.itcomponentsecommerce.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO truy vấn catalog: tìm kiếm + lọc đa điều kiện.
 *
 * Map {@code specs} chứa các bộ lọc theo thuộc tính kỹ thuật, ví dụ:
 *   specs = { "Socket" -> ["LGA1700", "AM5"], "Bus" -> ["6000 MHz"] }
 *
 * Cùng một spec name -> các value sẽ AND theo OR (IN list).
 * Khác spec name -> AND giữa các name (mỗi name là một điều kiện độc lập).
 */
public class CatalogQuery {

    private String q;
    private Long categoryId;
    private String categorySlug;
    private List<Long> brandIds = new ArrayList<>();
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Boolean inStock;
    private Map<String, List<String>> specs = new LinkedHashMap<>();

    public boolean hasKeyword() {
        return q != null && !q.isBlank();
    }

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategorySlug() { return categorySlug; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }
    public List<Long> getBrandIds() { return brandIds; }
    public void setBrandIds(List<Long> brandIds) {
        this.brandIds = (brandIds == null) ? new ArrayList<>() : brandIds;
    }
    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
    public Boolean getInStock() { return inStock; }
    public void setInStock(Boolean inStock) { this.inStock = inStock; }
    public Map<String, List<String>> getSpecs() { return specs; }
    public void setSpecs(Map<String, List<String>> specs) {
        this.specs = (specs == null) ? new LinkedHashMap<>() : specs;
    }
}
