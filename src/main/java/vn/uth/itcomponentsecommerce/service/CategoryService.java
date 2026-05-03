package vn.uth.itcomponentsecommerce.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import vn.uth.itcomponentsecommerce.dto.CategoryRequest;
import vn.uth.itcomponentsecommerce.entity.Category;
import vn.uth.itcomponentsecommerce.repository.CategoryRepository;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category id " + id + " không tồn tại"));
    }

    public Category create(CategoryRequest req) {
        Category c = new Category();
        apply(c, req);
        return categoryRepository.save(c);
    }

    public Category update(Long id, CategoryRequest req) {
        Category c = findById(id);
        apply(c, req);
        return categoryRepository.save(c);
    }

    public void delete(Long id) {
        if (!categoryRepository.existsById(id))
            throw new EntityNotFoundException("Category id " + id + " không tồn tại");
        categoryRepository.deleteById(id);
    }

    private void apply(Category c, CategoryRequest req) {
        c.setName(req.getName());
        c.setDescription(req.getDescription());
        c.setIcon(req.getIcon());
        String slug = (req.getSlug() == null || req.getSlug().isBlank())
                ? SlugUtil.toSlug(req.getName())
                : SlugUtil.toSlug(req.getSlug());
        c.setSlug(slug);
    }
}
