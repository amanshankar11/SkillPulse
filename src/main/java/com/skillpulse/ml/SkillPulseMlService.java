package com.skillpulse.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SkillPulseMlService {
    private final ObjectMapper objectMapper;
    private final String mlMode;
    private final String pythonCommand;
    private final Resource pythonScript;

    public SkillPulseMlService(
            ObjectMapper objectMapper,
            @Value("${skillpulse.ml.mode:java}") String mlMode,
            @Value("${skillpulse.ml.python.command:python}") String pythonCommand,
            @Value("${skillpulse.ml.python.script:classpath:ml/skillpulse_api.py}") Resource pythonScript) {
        this.objectMapper = objectMapper;
        this.mlMode = mlMode;
        this.pythonCommand = pythonCommand;
        this.pythonScript = pythonScript;
    }

    public SkillAnalysisResponse analyze(SkillAnalysisRequest request) {
        if ("python".equalsIgnoreCase(mlMode)) {
            SkillAnalysisResponse pythonResponse = tryPython(request);
            if (pythonResponse != null) {
                return pythonResponse;
            }
        }
        return analyzeWithJava(request);
    }

    private SkillAnalysisResponse tryPython(SkillAnalysisRequest request) {
        try {
            File script = materializeScript();
            Process process = new ProcessBuilder(pythonCommand, script.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            OutputStream stdin = process.getOutputStream();
            objectMapper.writeValue(stdin, request);
            stdin.close();

            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            byte[] output = readAll(process.getInputStream());
            if (process.exitValue() != 0) {
                return null;
            }
            SkillAnalysisResponse response = objectMapper.readValue(output, SkillAnalysisResponse.class);
            response.setEngine("python");
            return response;
        } catch (Exception ignored) {
            return null;
        }
    }

    private File materializeScript() throws Exception {
        File temp = File.createTempFile("skillpulse_api", ".py");
        temp.deleteOnExit();
        InputStream in = pythonScript.getInputStream();
        FileOutputStream out = new FileOutputStream(temp);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        in.close();
        out.close();
        return temp;
    }

    private byte[] readAll(InputStream input) throws Exception {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private SkillAnalysisResponse analyzeWithJava(SkillAnalysisRequest request) {
        List<Double> accuracies = safeAccuracies(request);
        int daysGap = daysSinceLastPractice(request);
        double recent = averageLast(accuracies, 5);
        double overall = average(accuracies);
        double trend = trend(accuracies);
        double recall = recallProbability(daysGap, recent, trend, average(request.getDifficulties()));

        String status;
        double confidence;
        if (recall < 0.50 || (recent < 0.60 && trend < -0.03)) {
            status = "DECAYING";
            confidence = clamp(0.70 + Math.abs(trend) + (0.60 - recent), 0.70, 0.97);
        } else if (trend > 0.04 && recent > 0.75) {
            status = "IMPROVING";
            confidence = clamp(0.68 + trend + recent / 10.0, 0.65, 0.95);
        } else {
            status = "STABLE";
            confidence = clamp(0.62 + overall / 5.0, 0.60, 0.90);
        }

        SkillAnalysisResponse response = new SkillAnalysisResponse();
        response.setSkillName(request.getSkillName() == null ? "Skill" : request.getSkillName());
        response.setStatus(status);
        response.setConfidence(round(confidence));
        response.setRecallProbability(round(recall));
        response.setPriority(priority(status, daysGap, recent));
        response.setActions(actions(status, daysGap, recent));
        response.setExplanation("Recent accuracy is " + Math.round(recent * 100)
                + "%, overall accuracy is " + Math.round(overall * 100)
                + "%, trend is " + round(trend)
                + ", and last practice was " + daysGap + " day(s) ago.");
        response.setEngine("java-fallback");
        return response;
    }

    private List<Double> safeAccuracies(SkillAnalysisRequest request) {
        if (request.getAccuracies() != null && !request.getAccuracies().isEmpty()) {
            return request.getAccuracies();
        }
        List<Double> defaults = new ArrayList<Double>();
        defaults.add(0.90);
        defaults.add(0.82);
        defaults.add(0.75);
        defaults.add(0.63);
        defaults.add(0.55);
        return defaults;
    }

    private int daysSinceLastPractice(SkillAnalysisRequest request) {
        if (request.getTimestamps() == null || request.getTimestamps().isEmpty()) {
            return 0;
        }
        try {
            LocalDate last = LocalDate.parse(request.getTimestamps().get(request.getTimestamps().size() - 1));
            return (int) Math.max(0, ChronoUnit.DAYS.between(last, LocalDate.now()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double recallProbability(int daysGap, double recent, double trend, double difficulty) {
        double halfLife = Math.exp(0.4 * recent + 0.3 * Math.max(0, trend) - 0.15 * Math.max(1.0, difficulty));
        halfLife = clamp(halfLife * 12.0, 0.5, 90.0);
        return clamp(Math.pow(2.0, -daysGap / halfLife), 0.0, 1.0);
    }

    private String priority(String status, int daysGap, double recent) {
        if ("DECAYING".equals(status)) {
            return "HIGH";
        }
        if (daysGap > 7 || recent < 0.70) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> actions(String status, int daysGap, double recent) {
        List<String> actions = new ArrayList<String>();
        if ("DECAYING".equals(status)) {
            actions.add("Schedule immediate refresher practice.");
            if (daysGap > 14) {
                actions.add("Reduce the long practice gap with daily micro-sessions.");
            }
            if (recent < 0.60) {
                actions.add("Restart with easier tasks before increasing difficulty.");
            }
        } else if ("IMPROVING".equals(status)) {
            actions.add("Continue the current practice pattern.");
            actions.add("Increase difficulty slightly to lock in mastery.");
        } else {
            actions.add("Maintain light review practice this week.");
        }
        return actions;
    }

    private double trend(List<Double> values) {
        if (values.size() < 2) {
            return 0;
        }
        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += i * values.get(i);
            sumXX += i * i;
        }
        return (n * sumXY - sumX * sumY) / Math.max(1.0, n * sumXX - sumX * sumX);
    }

    private double averageLast(List<Double> values, int count) {
        int start = Math.max(0, values.size() - count);
        return average(values.subList(start, values.size()));
    }

    private double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Double value : values) {
            sum += value == null ? 0 : value;
        }
        return sum / values.size();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
