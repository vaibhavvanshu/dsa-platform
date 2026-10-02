package com.capstone.dsaplatform.service;

import com.capstone.dsaplatform.domain.ErrorCategory;
import com.capstone.dsaplatform.dto.WeaknessDtos.CategoryView;
import com.capstone.dsaplatform.dto.WeaknessDtos.TopicCountView;
import com.capstone.dsaplatform.dto.WeaknessDtos.WeaknessResponse;
import com.capstone.dsaplatform.entity.ErrorEvent;
import com.capstone.dsaplatform.repository.ErrorEventRepository;
import com.capstone.dsaplatform.support.Rounding;
import com.capstone.dsaplatform.support.Times;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Recurring-error profile over ALL of a user's error_events (CONTRACT.md section 5.5).
 * One event per submission means each count is "submissions that went wrong this way".
 */
@Service
public class WeaknessService {

    private final UserService userService;
    private final ErrorEventRepository errorEvents;
    private final ResourceService resourceService;

    public WeaknessService(UserService userService, ErrorEventRepository errorEvents, ResourceService resourceService) {
        this.userService = userService;
        this.errorEvents = errorEvents;
        this.resourceService = resourceService;
    }

    @Transactional(readOnly = true)
    public WeaknessResponse profile(Long userId) {
        userService.require(userId);
        List<ErrorEvent> events = errorEvents.findByUserIdWithTopic(userId);
        int total = events.size();

        Map<ErrorCategory, List<ErrorEvent>> byCategory =
                events.stream().collect(Collectors.groupingBy(ErrorEvent::getErrorCategory));

        List<CategoryView> categories = new ArrayList<>();
        // Only categories that occurred are in the map, so zero-count categories are omitted automatically.
        for (Map.Entry<ErrorCategory, List<ErrorEvent>> entry : byCategory.entrySet()) {
            List<ErrorEvent> inCategory = entry.getValue();
            LocalDateTime lastSeen = inCategory.stream().map(ErrorEvent::getOccurredAt)
                    .max(Comparator.naturalOrder()).orElseThrow();

            List<TopicCountView> topics = inCategory.stream()
                    .collect(Collectors.groupingBy(e -> e.getTopic().getId()))
                    .values().stream()
                    .map(list -> new TopicCountView(list.get(0).getTopic().getId(), list.get(0).getTopic().getName(),
                            list.size()))
                    .sorted(Comparator.comparingInt(TopicCountView::count).reversed()
                            .thenComparing(TopicCountView::topicId))
                    .toList();

            // Links target the topic where this mistake happens most, where studying helps most.
            TopicCountView worstTopic = topics.get(0);
            categories.add(new CategoryView(entry.getKey(), inCategory.size(),
                    Rounding.twoDecimals((double) inCategory.size() / total),
                    Times.toInstant(lastSeen), topics,
                    resourceService.linksFor(entry.getKey(), worstTopic.topicId(), worstTopic.name())));
        }

        categories.sort(Comparator.comparingInt(CategoryView::count).reversed()
                .thenComparing(CategoryView::lastSeenAt, Comparator.reverseOrder()));
        return new WeaknessResponse(userId, total, categories);
    }
}
