package com.capstone.dsaplatform.service;

import com.capstone.dsaplatform.domain.ErrorCategory;
import com.capstone.dsaplatform.domain.ResourceSource;
import com.capstone.dsaplatform.dto.ResourceLink;
import com.capstone.dsaplatform.entity.LearningResource;
import com.capstone.dsaplatform.repository.LearningResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Study links for an (error category, topic) pair (CONTRACT.md section 4.5).
 * Curated, human-verified rows first; otherwise two SEARCH links. The backend never invents
 * a direct article or video URL, because an invented URL can be dead or wrong.
 */
@Service
public class ResourceService {

    private static final int MAX_CURATED = 3;

    private final LearningResourceRepository resources;

    public ResourceService(LearningResourceRepository resources) {
        this.resources = resources;
    }

    @Transactional(readOnly = true)
    public List<ResourceLink> linksFor(ErrorCategory category, Long topicId, String topicName) {
        List<LearningResource> curated = new ArrayList<>(
                resources.findByErrorCategoryAndTopicIdOrderByRankAscIdAsc(category, topicId));
        curated.addAll(resources.findByErrorCategoryAndTopicIsNullOrderByRankAscIdAsc(category));
        if (!curated.isEmpty()) {
            return curated.stream()
                    .limit(MAX_CURATED)
                    .map(r -> new ResourceLink(r.getTitle(), r.getUrl(), r.getSource(), false))
                    .toList();
        }
        return searchLinks(category, topicName);
    }

    private static List<ResourceLink> searchLinks(ErrorCategory category, String topicName) {
        String query = topicName + " " + searchPhrase(category) + " java";
        // URLEncoder encodes spaces as "+", which is what both search URLs expect.
        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return List.of(
                new ResourceLink("GeeksforGeeks search: " + query,
                        "https://www.google.com/search?q=site%3Ageeksforgeeks.org+" + q, ResourceSource.GFG, true),
                new ResourceLink("YouTube search: " + query,
                        "https://www.youtube.com/results?search_query=" + q, ResourceSource.YOUTUBE, true));
    }

    private static String searchPhrase(ErrorCategory category) {
        return switch (category) {
            case SYNTAX_COMPILATION -> "java compilation errors";
            case OFF_BY_ONE -> "off by one error";
            case MISSED_EDGE_CASE -> "edge cases";
            case WRONG_ALGORITHM -> "approach explained";
            case STATE_TRANSITION -> "dp state transition";
            case BAD_COMPLEXITY -> "time complexity optimization";
            case SUBOPTIMAL -> "optimal solution";
        };
    }
}
