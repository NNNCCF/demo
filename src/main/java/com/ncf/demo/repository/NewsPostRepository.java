package com.ncf.demo.repository;

import com.ncf.demo.entity.NewsPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {
    List<NewsPost> findAllByOrderByCreatedAtDesc();
}
