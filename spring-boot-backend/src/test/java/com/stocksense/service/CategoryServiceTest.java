package com.stocksense.service;

import com.stocksense.entity.ProductCategory;
import com.stocksense.repository.ProductCategoryRepository;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService Tests")
class CategoryServiceTest {

    @Mock ProductCategoryRepository categoryRepository;
    @InjectMocks CategoryService    categoryService;

    private ProductCategory category;

    @BeforeEach
    void setUp() {
        category = new ProductCategory();
        category.setId(1L);
        category.setName("Beverages");
        category.setDescription("Drinks and beverages");
        category.setIsActive(true);
    }

    @Test
    @DisplayName("create: saves category when name is unique")
    void create_uniqueName_savesCategory() {
        when(categoryRepository.existsByName("Beverages")).thenReturn(false);
        when(categoryRepository.save(any())).thenReturn(category);

        ProductCategory result = categoryService.create("Beverages", "Drinks", null);

        assertThat(result.getName()).isEqualTo("Beverages");
        verify(categoryRepository).save(any(ProductCategory.class));
    }

    @Test
    @DisplayName("create: throws when category name already exists")
    void create_duplicateName_throwsException() {
        when(categoryRepository.existsByName("Beverages")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create("Beverages", "Desc", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("findById: returns category when it exists")
    void findById_existingId_returnsCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        ProductCategory result = categoryService.findById(1L);
        assertThat(result.getName()).isEqualTo("Beverages");
    }

    @Test
    @DisplayName("findById: throws when category does not exist")
    void findById_notFound_throwsException() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> categoryService.findById(99L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("updateWithImage removeImage=true: clears imagePath to null")
    void updateWithImage_removeTrue_setsImageNull() {
        category.setImagePath("uploads/old.png");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductCategory result = categoryService.updateWithImage(1L, "Beverages", "Desc", null, true);

        assertThat(result.getImagePath()).isNull();
    }

    @Test
    @DisplayName("updateWithImage with new image: sets new imagePath")
    void updateWithImage_newImage_setsImagePath() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductCategory result = categoryService.updateWithImage(
                1L, "Beverages", "Desc", "uploads/new.png", false);

        assertThat(result.getImagePath()).isEqualTo("uploads/new.png");
    }

    @Test
    @DisplayName("delete: sets isActive=false (soft delete)")
    void delete_setsInactive() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        categoryService.delete(1L);

        verify(categoryRepository).save(argThat(c -> Boolean.FALSE.equals(c.getIsActive())));
    }
}
