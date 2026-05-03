package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class MergeCartRequest {

    @Valid
    @NotEmpty(message = "items are required")
    private List<CartItemDTO> items = new ArrayList<>();

    public List<CartItemDTO> getItems() { return items; }
    public void setItems(List<CartItemDTO> items) { this.items = items; }
}
