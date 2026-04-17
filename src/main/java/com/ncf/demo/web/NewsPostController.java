package com.ncf.demo.web;

import com.ncf.demo.entity.NewsPost;
import com.ncf.demo.service.NewsPostService;
import com.ncf.demo.web.dto.NewsPostCreateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsPostController {
    private final NewsPostService newsPostService;

    public NewsPostController(NewsPostService newsPostService) {
        this.newsPostService = newsPostService;
    }

    @GetMapping
    public ApiResponse<List<NewsPost>> list() {
        return ApiResponse.ok(newsPostService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<NewsPost> getById(@PathVariable Long id) {
        return ApiResponse.ok(newsPostService.getById(id));
    }

    @PostMapping
    public ApiResponse<NewsPost> create(@RequestBody @Valid NewsPostCreateRequest request) {
        return ApiResponse.ok(newsPostService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<NewsPost> update(@PathVariable Long id, @RequestBody @Valid NewsPostCreateRequest request) {
        return ApiResponse.ok(newsPostService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        newsPostService.delete(id);
        return ApiResponse.ok(null);
    }
}
