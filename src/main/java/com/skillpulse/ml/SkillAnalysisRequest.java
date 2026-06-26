package com.skillpulse.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkillAnalysisRequest {
    private String token;
    private String skillName;
    private List<String> timestamps = new ArrayList<String>();
    private List<Double> accuracies = new ArrayList<Double>();
    private List<Double> difficulties = new ArrayList<Double>();
    private Map<String, Integer> errors = new HashMap<String, Integer>();
    private List<Double> hintsUsed = new ArrayList<Double>();
    private List<Integer> completionStatus = new ArrayList<Integer>();

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public List<String> getTimestamps() { return timestamps; }
    public void setTimestamps(List<String> timestamps) { this.timestamps = timestamps; }
    public List<Double> getAccuracies() { return accuracies; }
    public void setAccuracies(List<Double> accuracies) { this.accuracies = accuracies; }
    public List<Double> getDifficulties() { return difficulties; }
    public void setDifficulties(List<Double> difficulties) { this.difficulties = difficulties; }
    public Map<String, Integer> getErrors() { return errors; }
    public void setErrors(Map<String, Integer> errors) { this.errors = errors; }
    public List<Double> getHintsUsed() { return hintsUsed; }
    public void setHintsUsed(List<Double> hintsUsed) { this.hintsUsed = hintsUsed; }
    public List<Integer> getCompletionStatus() { return completionStatus; }
    public void setCompletionStatus(List<Integer> completionStatus) { this.completionStatus = completionStatus; }
}
