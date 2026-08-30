package com.stocksense.service;

import com.stocksense.entity.ProductCategory;
import com.stocksense.repository.ProductCategoryRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ProductCategoryRepository categoryRepository;

    public List<ProductCategory> findAll()       { return categoryRepository.findAll(); }
    public List<ProductCategory> findAllActive() { return categoryRepository.findByIsActiveTrue(); }

    public ProductCategory findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found: " + id));
    }

    @Transactional
    public ProductCategory create(String name, String description, String imagePath) {
        if (categoryRepository.existsByName(name))
            throw new RuntimeException("Category already exists: " + name);
        ProductCategory cat = new ProductCategory();
        cat.setName(name);
        cat.setDescription(description);
        cat.setImagePath(imagePath);
        return categoryRepository.save(cat);
    }

    /**
     * Update category.
     * @param removeImage if true, clears the image (reverts to default icon)
     * @param newImagePath if not null and removeImage=false, replaces the image
     */
    @Transactional
    public ProductCategory updateWithImage(Long id, String name, String description,
                                           String newImagePath, boolean removeImage) {
        ProductCategory cat = findById(id);
        cat.setName(name);
        cat.setDescription(description);

        if (removeImage) {
            cat.setImagePath(null);       // ← clear → default icon shown
        } else if (newImagePath != null) {
            cat.setImagePath(newImagePath); // ← replace with new image
        }
        // else: leave imagePath unchanged

        return categoryRepository.save(cat);
    }

    @Transactional
    public void delete(Long id) {
        ProductCategory cat = findById(id);
        cat.setIsActive(false);
        categoryRepository.save(cat);
    }
}
