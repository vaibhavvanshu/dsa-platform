package com.capstone.dsaplatform.repository;

import com.capstone.dsaplatform.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    // All 10 topics with their prerequisite edges in ONE query; the graph is tiny and always needed whole.
    @Query("select distinct t from Topic t left join fetch t.prerequisites order by t.id")
    List<Topic> findAllWithPrerequisites();
}
