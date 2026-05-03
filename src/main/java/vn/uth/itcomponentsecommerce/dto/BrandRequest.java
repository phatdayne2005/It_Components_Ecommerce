package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.NotBlank;

public class BrandRequest {

    @NotBlank
    private String name;

    private String slug;
    private String logoUrl;
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
