package com.ncf.demo.repository;

import com.ncf.demo.entity.FeedbackSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackSubmissionRepository extends JpaRepository<FeedbackSubmission, Long> {
}
