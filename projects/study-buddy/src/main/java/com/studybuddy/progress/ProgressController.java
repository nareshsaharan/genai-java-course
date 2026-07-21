package com.studybuddy.progress;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studybuddy.progress.dto.RecommendationResponse;
import com.studybuddy.progress.dto.TopicProgressView;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/topics")
    public List<TopicProgressView> topics() {
        return progressService.getTopics();
    }

    @GetMapping("/weak-topics")
    public List<TopicProgressView> weakTopics() {
        return progressService.getWeakTopics();
    }

    @GetMapping("/recommendation")
    public RecommendationResponse recommendation() {
        return progressService.getRecommendation();
    }
}
