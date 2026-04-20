package com.ncf.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncf.demo.common.BizException;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.Family;
import com.ncf.demo.entity.NewsPost;
import com.ncf.demo.entity.UserAccount;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.FamilyRepository;
import com.ncf.demo.repository.NewsPostRepository;
import com.ncf.demo.repository.UserRepository;
import com.ncf.demo.security.SecurityUtil;
import com.ncf.demo.web.dto.NewsPostCreateRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NewsPostService {
    private final NewsPostRepository newsPostRepository;
    private final ObjectMapper objectMapper;
    private final FamilyRepository familyRepository;
    private final ClientUserRepository clientUserRepository;
    private final UserRepository userRepository;

    public NewsPostService(
            NewsPostRepository newsPostRepository,
            ObjectMapper objectMapper,
            FamilyRepository familyRepository,
            ClientUserRepository clientUserRepository,
            UserRepository userRepository
    ) {
        this.newsPostRepository = newsPostRepository;
        this.objectMapper = objectMapper;
        this.familyRepository = familyRepository;
        this.clientUserRepository = clientUserRepository;
        this.userRepository = userRepository;
    }

    public NewsPost create(NewsPostCreateRequest request) {
        NewsPost post = new NewsPost();
        applyRequest(post, request, true);
        return newsPostRepository.save(post);
    }

    public List<NewsPost> list() {
        String role = SecurityUtil.currentRole();
        if (!"GUARDIAN".equals(role)) {
            return newsPostRepository.findAllByOrderByCreatedAtDesc();
        }
        Long guardianId = SecurityUtil.currentUserId();
        Set<Long> familyIds = familyRepository.findByGuardiansId(guardianId).stream()
                .map(Family::getId)
                .collect(Collectors.toSet());
        return newsPostRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(post -> isVisibleToGuardian(post, familyIds))
                .collect(Collectors.toList());
    }

    public NewsPost getById(Long id) {
        NewsPost post = newsPostRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "动态不存在"));
        String role = SecurityUtil.currentRole();
        if ("GUARDIAN".equals(role)) {
            Long guardianId = SecurityUtil.currentUserId();
            Set<Long> familyIds = familyRepository.findByGuardiansId(guardianId).stream()
                    .map(Family::getId)
                    .collect(Collectors.toSet());
            if (!isVisibleToGuardian(post, familyIds)) {
                throw new BizException(403, "无权查看该动态");
            }
        }
        return post;
    }

    public NewsPost update(Long id, NewsPostCreateRequest request) {
        NewsPost post = getById(id);
        applyRequest(post, request, false);
        return newsPostRepository.save(post);
    }

    public void delete(Long id) {
        newsPostRepository.deleteById(id);
    }

    private void applyRequest(NewsPost post, NewsPostCreateRequest request, boolean create) {
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setVisibility(request.visibility() != null ? request.visibility() : "ALL");
        post.setCategory(request.category() != null ? request.category() : post.getCategory());
        post.setTargetScope(normalizeTargetScope(request.targetScope()));
        Family targetFamily = resolveTargetFamily(request);
        post.setTargetFamilyId(targetFamily != null ? targetFamily.getId() : request.targetFamilyId());
        post.setTargetFamilyName(targetFamily != null ? targetFamily.getName() : request.targetFamilyName());
        post.setPublisherId(request.publisherId() != null ? request.publisherId() : SecurityUtil.currentUserId());
        post.setPublisherName(resolvePublisherName(request.publisherName()));
        post.setPublishTime(request.publishTime() != null ? request.publishTime() : (create ? Instant.now() : post.getPublishTime()));
        if (create && post.getCreatedAt() == null) {
            post.setCreatedAt(Instant.now());
        }
        if (request.attachments() != null) {
            try {
                post.setAttachments(objectMapper.writeValueAsString(request.attachments()));
            } catch (JsonProcessingException e) {
                post.setAttachments("[]");
            }
        } else if (create) {
            post.setAttachments("[]");
        }
    }

    private String normalizeTargetScope(String targetScope) {
        if (targetScope == null || targetScope.isBlank()) {
            return "ALL";
        }
        String normalized = targetScope.trim().toUpperCase(Locale.ROOT);
        return Objects.equals(normalized, "FAMILY") ? "FAMILY" : "ALL";
    }

    private Family resolveTargetFamily(NewsPostCreateRequest request) {
        if (!"FAMILY".equals(normalizeTargetScope(request.targetScope()))) {
            return null;
        }
        if (request.targetFamilyId() != null) {
            return familyRepository.findById(request.targetFamilyId())
                    .orElseThrow(() -> new BizException(404, "目标家庭不存在"));
        }
        if (request.targetFamilyName() != null && !request.targetFamilyName().isBlank()) {
            return familyRepository.findFirstByNameIgnoreCase(request.targetFamilyName().trim())
                    .orElseThrow(() -> new BizException(404, "目标家庭不存在"));
        }
        throw new BizException(400, "请指定目标家庭");
    }

    private String resolvePublisherName(String requestedPublisherName) {
        if (requestedPublisherName != null && !requestedPublisherName.isBlank()) {
            return requestedPublisherName.trim();
        }
        Long uid = SecurityUtil.currentUserId();
        if (uid == null) {
            return "系统";
        }
        return clientUserRepository.findById(uid)
                .map(ClientUser::getName)
                .or(() -> userRepository.findById(uid).map(UserAccount::getUsername))
                .orElse("系统");
    }

    private boolean isVisibleToGuardian(NewsPost post, Set<Long> familyIds) {
        String targetScope = normalizeTargetScope(post.getTargetScope());
        if ("ALL".equals(targetScope)) {
            return true;
        }
        return post.getTargetFamilyId() != null && familyIds.contains(post.getTargetFamilyId());
    }
}
