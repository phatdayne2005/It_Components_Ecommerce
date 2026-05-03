package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.BrandRequest;
import vn.uth.itcomponentsecommerce.entity.Brand;
import vn.uth.itcomponentsecommerce.service.BrandService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/brands")
public class BrandAdminController {

    private final BrandService service;

    public BrandAdminController(BrandService service) {
        this.service = service;
    }

    @GetMapping
    public List<Brand> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Brand get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<Brand> create(@Valid @RequestBody BrandRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public Brand update(@PathVariable Long id, @Valid @RequestBody BrandRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
