package com.eclakehouse.catalog.category;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eclakehouse.common.web.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    public record CategoryRequest(@NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "[a-z0-9_-]{1,120}") String slug,
            UUID parentId) {
    }

    public record CategoryResponse(UUID id, String name, String slug, UUID parentId) {
        static CategoryResponse of(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getParentId());
        }
    }

    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAll(Sort.by("name")).stream().map(CategoryResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CategoryResponse create(@Valid @RequestBody CategoryRequest req) {
        if (categories.existsBySlug(req.slug())) {
            throw ApiException.conflict("Category slug already exists");
        }
        if (req.parentId() != null && !categories.existsById(req.parentId())) {
            throw ApiException.badRequest("Parent category does not exist");
        }
        return CategoryResponse.of(categories.save(new Category(req.name(), req.slug(), req.parentId())));
    }
}
