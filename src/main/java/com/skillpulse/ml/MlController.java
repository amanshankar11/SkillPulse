package com.skillpulse.ml;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class MlController {
    private final SkillPulseMlService mlService;

    public MlController(SkillPulseMlService mlService) {
        this.mlService = mlService;
    }

    @GetMapping("/api/ml/health")
    public Map<String, Object> health() {
        return Collections.<String, Object>singletonMap("ready", true);
    }

    @PostMapping("/api/ml/analyze")
    public SkillAnalysisResponse analyze(@RequestBody SkillAnalysisRequest request) {
        return mlService.analyze(request);
    }
}
