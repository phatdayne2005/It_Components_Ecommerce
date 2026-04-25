package vn.uth.itcomponentsecommerce.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 200)
    private String alt;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    public ProductImage() {}

    public ProductImage(Product product, String url, Integer sortOrder) {
        this.product = product;
        this.url = url;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getAlt() { return alt; }
    public void setAlt(String alt) { this.alt = alt; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
