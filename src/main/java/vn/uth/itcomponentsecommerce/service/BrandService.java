package vn.uth.itcomponentsecommerce.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import vn.uth.itcomponentsecommerce.dto.BrandRequest;
import vn.uth.itcomponentsecommerce.entity.Brand;
import vn.uth.itcomponentsecommerce.repository.BrandRepository;

import java.util.List;

@Service
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    public List<Brand> findAll() {
        return brandRepository.findAll();
    }

    public Brand findById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Brand id " + id + " không tồn tại"));
    }

    public Brand create(BrandRequest req) {
        Brand b = new Brand();
        apply(b, req);
        return brandRepository.save(b);
    }

    public Brand update(Long id, BrandRequest req) {
        Brand b = findById(id);
        apply(b, req);
        return brandRepository.save(b);
    }

    public void delete(Long id) {
        if (!brandRepository.existsById(id))
            throw new EntityNotFoundException("Brand id " + id + " không tồn tại");
        brandRepository.deleteById(id);
    }

    private void apply(Brand b, BrandRequest req) {
        b.setName(req.getName());
        b.setLogoUrl(req.getLogoUrl());
        b.setDescription(req.getDescription());
        String slug = (req.getSlug() == null || req.getSlug().isBlank())
                ? SlugUtil.toSlug(req.getName())
                : SlugUtil.toSlug(req.getSlug());
        b.setSlug(slug);
    }
}
