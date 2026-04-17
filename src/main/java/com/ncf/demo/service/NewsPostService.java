package com.ncf.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncf.demo.entity.NewsPost;
import com.ncf.demo.repository.NewsPostRepository;
import com.ncf.demo.web.dto.NewsPostCreateRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class NewsPostService {
    private final NewsPostRepository newsPostRepository;
    private final ObjectMapper objectMapper;

    public NewsPostService(NewsPostRepository newsPostRepository, ObjectMapper objectMapper) {
        this.newsPostRepository = newsPostRepository;
        this.objectMapper = objectMapper;
    }

    public NewsPost create(NewsPostCreateRequest request) {
        NewsPost post = new NewsPost();
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setVisibility(request.visibility() != null ? request.visibility() : "ALL");
        post.setPublisherId(request.publisherId());
        post.setPublisherName(request.publisherName());
        post.setPublishTime(request.publishTime() != null ? request.publishTime() : Instant.now());
        post.setCreatedAt(Instant.now());
        if (request.attachments() != null && !request.attachments().isEmpty()) {
            try {
                post.setAttachments(objectMapper.writeValueAsString(request.attachments()));
            } catch (JsonProcessingException e) {
                post.setAttachments("[]");
            }
        } else {
            post.setAttachments("[]");
        }
        return newsPostRepository.save(post);
    }

    public List<NewsPost> list() {
        return newsPostRepository.findAllByOrderByCreatedAtDesc();
    }

    public NewsPost getById(Long id) {
        return newsPostRepository.findById(id)
                .orElseThrow(() -> new com.ncf.demo.common.BizException(404, "动态不存在"));
    }

    public NewsPost update(Long id, NewsPostCreateRequest request) {
        NewsPost post = getById(id);
        post.setTitle(request.title());
        post.setContent(request.content());
        if (request.visibility() != null) post.setVisibility(request.visibility());
        if (request.publisherName() != null) post.setPublisherName(request.publisherName());
        if (request.publishTime() != null) post.setPublishTime(request.publishTime());
        if (request.attachments() != null) {
            try {
                post.setAttachments(objectMapper.writeValueAsString(request.attachments()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                post.setAttachments("[]");
            }
        }
        return newsPostRepository.save(post);
    }

    public void delete(Long id) {
        newsPostRepository.deleteById(id);
    }
}
