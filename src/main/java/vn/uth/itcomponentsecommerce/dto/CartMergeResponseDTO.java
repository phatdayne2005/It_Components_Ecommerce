package vn.uth.itcomponentsecommerce.dto;

import vn.uth.itcomponentsecommerce.entity.Cart;

import java.util.ArrayList;
import java.util.List;

public class CartMergeResponseDTO {

    private Cart cart;
    private boolean hasWarning;
    private List<String> warnings = new ArrayList<>();

    public Cart getCart() { return cart; }
    public void setCart(Cart cart) { this.cart = cart; }
    public boolean isHasWarning() { return hasWarning; }
    public void setHasWarning(boolean hasWarning) { this.hasWarning = hasWarning; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
