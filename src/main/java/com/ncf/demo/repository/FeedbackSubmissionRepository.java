package com.ncf.demo.repository;

import com.ncf.demo.entity.FeedbackSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface FeedbackSubmissionRepository extends JpaRepository<FeedbackSubmission, Long> {
    Optional<FeedbackSubmission> findByIdAndDeletedFalse(Long id);

    List<FeedbackSubmission> findByDeletedFalse(Sort sort);
}
