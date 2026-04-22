package com.ncf.demo.web;

import com.ncf.demo.common.BizException;
import com.ncf.demo.entity.ClientUser;
import com.ncf.demo.entity.FeedbackSubmission;
import com.ncf.demo.entity.UserAccount;
import com.ncf.demo.repository.ClientUserRepository;
import com.ncf.demo.repository.FeedbackSubmissionRepository;
import com.ncf.demo.repository.UserRepository;
import com.ncf.demo.security.SecurityUtil;
import com.ncf.demo.web.dto.FeedbackSaveRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/feedbacks")
public class FeedbackAdminController {
    private static final List<String> VALID_STATUSES = List.of("NEW", "IN_PROGRESS", "RESOLVED");

    private final FeedbackSubmissionRepository feedbackRepo;
    private final ClientUserRepository clientUserRepo;
    private final UserRepository userRepo;

    public FeedbackAdminController(
            FeedbackSubmissionRepository feedbackRepo,
            ClientUserRepository clientUserRepo,
            UserRepository userRepo
    ) {
        this.feedbackRepo = feedbackRepo;
        this.clientUserRepo = clientUserRepo;
        this.userRepo = userRepo;
    }

    record FeedbackItemResponse(
            Long id,
            Long submitterId,
            String submitterRole,
            String submitterName,
            String type,
            String content,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    record FeedbackStatusRequest(@NotBlank String status) {}

    @GetMapping
    public ApiResponse<List<FeedbackItemResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String submitterRole,
            @RequestParam(required = false) String keyword
    ) {
        String normalizedStatus = normalizeOptional(status);
        String normalizedType = normalizeOptional(type);
        String normalizedRole = normalizeOptional(submitterRole);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        List<FeedbackItemResponse> data = feedbackRepo.findByDeletedFalse(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                .stream()
                .filter(item -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(item.getStatus()))
                .filter(item -> normalizedType == null || normalizedType.equalsIgnoreCase(item.getType()))
                .filter(item -> normalizedRole == null || normalizedRole.equalsIgnoreCase(item.getSubmitterRole()))
                .map(this::toResponse)
                .filter(item -> normalizedKeyword.isEmpty() || matchesKeyword(item, normalizedKeyword))
                .toList();

        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResponse<FeedbackItemResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(toResponse(findActiveFeedback(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FeedbackItemResponse> create(@RequestBody @Valid FeedbackSaveRequest body) {
        FeedbackSubmission feedback = new FeedbackSubmission();
        applyRequest(feedback, body, true);
        feedbackRepo.save(feedback);
        return ApiResponse.ok(toResponse(feedback));
    }

    @PutMapping("/{id}")
    public ApiResponse<FeedbackItemResponse> update(@PathVariable Long id, @RequestBody @Valid FeedbackSaveRequest body) {
        FeedbackSubmission feedback = findActiveFeedback(id);
        applyRequest(feedback, body, false);
        feedbackRepo.save(feedback);
        return ApiResponse.ok(toResponse(feedback));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        FeedbackSubmission feedback = findActiveFeedback(id);
        feedback.setDeleted(true);
        feedback.setDeletedAt(Instant.now());
        feedback.setDeletedBy(SecurityUtil.currentUserId());
        feedbackRepo.save(feedback);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid FeedbackStatusRequest body) {
        FeedbackSubmission feedback = findActiveFeedback(id);
        feedback.setStatus(normalizeStatus(body.status()));
        feedbackRepo.save(feedback);
        return ApiResponse.ok(null);
    }

    private FeedbackSubmission findActiveFeedback(Long id) {
        return feedbackRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(4004, "鍙嶉涓嶅瓨鍦?"));
    }

    private void applyRequest(FeedbackSubmission feedback, FeedbackSaveRequest body, boolean create) {
        feedback.setSubmitterId(body.submitterId() != null ? body.submitterId() : (create ? SecurityUtil.currentUserId() : feedback.getSubmitterId()));
        feedback.setSubmitterRole(normalizeText(body.submitterRole(), create ? SecurityUtil.currentRole() : feedback.getSubmitterRole()));
        feedback.setType(normalizeText(body.type(), feedback.getType()));
        feedback.setContent(normalizeText(body.content(), feedback.getContent()));
        feedback.setStatus(body.status() != null ? normalizeStatus(body.status()) : (create ? "NEW" : feedback.getStatus()));
        feedback.setDeleted(false);
        feedback.setDeletedAt(null);
        feedback.setDeletedBy(null);
    }

    private FeedbackItemResponse toResponse(FeedbackSubmission feedback) {
        return new FeedbackItemResponse(
                feedback.getId(),
                feedback.getSubmitterId(),
                feedback.getSubmitterRole(),
                resolveSubmitterName(feedback.getSubmitterId()),
                feedback.getType(),
                feedback.getContent(),
                feedback.getStatus(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt()
        );
    }

    private boolean matchesKeyword(FeedbackItemResponse item, String keyword) {
        return String.valueOf(item.id()).contains(keyword)
                || lower(item.submitterName()).contains(keyword)
                || lower(item.submitterRole()).contains(keyword)
                || lower(item.type()).contains(keyword)
                || lower(item.content()).contains(keyword);
    }

    private String resolveSubmitterName(Long submitterId) {
        if (submitterId == null) {
            return "-";
        }
        ClientUser clientUser = clientUserRepo.findById(submitterId).orElse(null);
        if (clientUser != null && clientUser.getName() != null && !clientUser.getName().isBlank()) {
            return clientUser.getName();
        }
        UserAccount user = userRepo.findById(submitterId).orElse(null);
        if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "鐢ㄦ埛#" + submitterId;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null || !VALID_STATUSES.contains(normalized)) {
            throw new BizException(4001, "鍙嶉鐘舵€佷笉鍚堟硶");
        }
        return normalized;
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
