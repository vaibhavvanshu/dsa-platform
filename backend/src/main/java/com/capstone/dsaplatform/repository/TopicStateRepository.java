package com.capstone.dsaplatform.repository;

import com.capstone.dsaplatform.entity.TopicState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopicStateRepository extends JpaRepository<TopicState, Long> {

    Optional<TopicState> findByUserIdAndTopicId(Long userId, Long topicId);

    List<TopicState> findByUserId(Long userId);
}
