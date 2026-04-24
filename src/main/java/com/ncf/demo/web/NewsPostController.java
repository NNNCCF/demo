package com.ncf.demo.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsPostController {
    private final NewsPostService newsPostService;
    private final ObjectMapper objectMapper;

    record NewsPostResponse(
            Long id,
            String title,
            String content,
            String visibility,
            String category,
            String targetScope,
            Long targetFamilyId,
            String targetFamilyName,
            Long publisherId,
            String publisherName,
            Instant publishTime,
            List<String> attachments,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public NewsPostController(NewsPostService newsPostService, ObjectMapper objectMapper) {
        this.newsPostService = newsPostService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<NewsPostResponse>> list() {
        return ApiResponse.ok(newsPostService.list().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<NewsPostResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(toResponse(newsPostService.getById(id)));
    }

    @PostMapping
    public ApiResponse<NewsPostResponse> create(@RequestBody @Valid NewsPostCreateRequest request) {
        return ApiResponse.ok(toResponse(newsPostService.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<NewsPostResponse> update(@PathVariable Long id, @RequestBody @Valid NewsPostCreateRequest request) {
        return ApiResponse.ok(toResponse(newsPostService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        newsPostService.delete(id);
        return ApiResponse.ok(null);
    }

    private NewsPostResponse toResponse(NewsPost post) {
        return new NewsPostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getVisibility(),
                post.getCategory(),
                post.getTargetScope(),
                post.getTargetFamilyId(),
                post.getTargetFamilyName(),
                post.getPublisherId(),
                post.getPublisherName(),
                post.getPublishTime(),
                parseAttachments(post.getAttachments()),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private List<String> parseAttachments(String rawAttachments) {
        if (rawAttachments == null || rawAttachments.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawAttachments, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ignored) {
            try {
                String singleAttachment = objectMapper.readValue(rawAttachments, String.class);
                return singleAttachment == null || singleAttachment.isBlank()
                        ? List.of()
                        : List.of(singleAttachment);
            } catch (JsonProcessingException ignoredAgain) {
                return List.of(rawAttachments);
            }
        }
    }
}
