package com.skillpulse.ml;

import java.util.ArrayList;
import java.util.List;

public class SkillAnalysisResponse {
    private String skillName;
    private String status;
    private double confidence;
    private double recallProbability;
    private String priority;
    private List<String> actions = new ArrayList<String>();
    private String explanation;
    private String engine;

    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public double getRecallProbability() { return recallProbability; }
    public void setRecallProbability(double recallProbability) { this.recallProbability = recallProbability; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public List<String> getActions() { return actions; }
    public void setActions(List<String> actions) { this.actions = actions; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
}
