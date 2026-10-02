package com.capstone.dsaplatform.repository;

import com.capstone.dsaplatform.entity.TopicState;
import org.springframework.data.jpa.repository.JpaRepository;

// Query methods are added in the phase that first needs them, so none exist unused.
public interface TopicStateRepository extends JpaRepository<TopicState, Long> {
}
