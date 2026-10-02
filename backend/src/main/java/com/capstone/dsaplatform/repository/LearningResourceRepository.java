package com.capstone.dsaplatform.repository;

import com.capstone.dsaplatform.domain.ErrorCategory;
import com.capstone.dsaplatform.entity.LearningResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {

    // Two queries instead of one clever OR: topic-specific links must come before generic ones (CONTRACT 4.5).
    List<LearningResource> findByErrorCategoryAndTopicIdOrderByRankAscIdAsc(ErrorCategory errorCategory, Long topicId);

    List<LearningResource> findByErrorCategoryAndTopicIsNullOrderByRankAscIdAsc(ErrorCategory errorCategory);
}
