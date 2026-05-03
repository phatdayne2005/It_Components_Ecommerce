package vn.uth.itcomponentsecommerce.dto;

import java.util.List;

/**
 * Một nhóm bộ lọc trên sidebar, ví dụ: "Socket" -> [(LGA1700, 12), (AM5, 7)].
 */
public record SpecFacet(String name, List<FacetValue> values) {

    public record FacetValue(String value, long count) {}
}
