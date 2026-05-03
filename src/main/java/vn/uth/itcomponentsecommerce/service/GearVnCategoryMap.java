package vn.uth.itcomponentsecommerce.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapping cứng giữa GearVN collection handle (Shopify) và category slug local
 * (đã seed trong {@code DataSeeder.seedCategories}). Các handle dưới đây đều đã
 * được verify bằng endpoint {@code /collections/{handle}/products.json}.
 *
 * Thứ tự khai báo cũng là thứ tự hiển thị trong dropdown UI admin.
 */
public final class GearVnCategoryMap {

    private GearVnCategoryMap() {}

    public record CollectionOption(String handle, String label, String localCategorySlug) {}

    private static final List<CollectionOption> OPTIONS = List.of(
            new CollectionOption("cpu-bo-vi-xu-ly",        "CPU",        "cpu"),
            new CollectionOption("mainboard-bo-mach-chu",  "Mainboard",  "mainboard"),
            new CollectionOption("ram-pc",                 "RAM",        "ram"),
            new CollectionOption("vga-card-man-hinh",      "VGA",        "vga"),
            new CollectionOption("ssd-o-cung-the-ran",     "SSD",        "ssd"),
            new CollectionOption("hdd-o-cung-pc",          "HDD",        "hdd"),
            new CollectionOption("psu-nguon-may-tinh",     "PSU",        "psu"),
            new CollectionOption("case-thung-may-tinh",    "Case",       "case"),
            new CollectionOption("tan-nhiet-khi",          "Tản nhiệt",  "tan-nhiet"),
            new CollectionOption("man-hinh",               "Màn hình",   "man-hinh"),
            new CollectionOption("ban-phim-may-tinh",      "Bàn phím",   "ban-phim"),
            new CollectionOption("chuot-may-tinh",         "Chuột",      "chuot")
    );

    private static final Map<String, CollectionOption> BY_HANDLE;
    static {
        Map<String, CollectionOption> m = new LinkedHashMap<>();
        for (CollectionOption o : OPTIONS) m.put(o.handle(), o);
        BY_HANDLE = Map.copyOf(m);
    }

    public static List<CollectionOption> options() {
        return OPTIONS;
    }

    public static CollectionOption byHandle(String handle) {
        return handle == null ? null : BY_HANDLE.get(handle);
    }

    public static boolean isSupported(String handle) {
        return handle != null && BY_HANDLE.containsKey(handle);
    }
}
