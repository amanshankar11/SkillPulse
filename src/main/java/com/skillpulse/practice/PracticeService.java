package com.skillpulse.practice;

import com.skillpulse.auth.AppUser;
import com.skillpulse.auth.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PracticeService {
    private static final int MODULE_PASS_SCORE = 80;

    private final AuthService authService;
    private final PracticeSubjectRepository subjects;
    private final PracticeTopicRepository topics;
    private final PracticeQuestionRepository questions;
    private final QuestionOptionRepository options;
    private final UserPracticeAttemptRepository attempts;

    public PracticeService(AuthService authService,
                           PracticeSubjectRepository subjects,
                           PracticeTopicRepository topics,
                           PracticeQuestionRepository questions,
                           QuestionOptionRepository options,
                           UserPracticeAttemptRepository attempts) {
        this.authService = authService;
        this.subjects = subjects;
        this.topics = topics;
        this.questions = questions;
        this.options = options;
        this.attempts = attempts;
    }

    public List<PracticeDtos.SubjectResponse> subjects() {
        List<PracticeDtos.SubjectResponse> result = new ArrayList<PracticeDtos.SubjectResponse>();
        for (PracticeSubject subject : subjects.findAllByOrderByDisplayOrderAsc()) {
            result.add(new PracticeDtos.SubjectResponse(
                    subject,
                    topics.countBySubjectId(subject.getId()),
                    questions.countByTopicSubjectIdAndActiveTrue(subject.getId())
            ));
        }
        return result;
    }

    public List<PracticeDtos.TopicResponse> topics(Long subjectId) {
        return topics(subjectId, null);
    }

    public List<PracticeDtos.TopicResponse> topics(Long subjectId, String token) {
        List<PracticeDtos.TopicResponse> result = new ArrayList<PracticeDtos.TopicResponse>();
        AppUser user = token == null || token.trim().isEmpty() ? null : authService.findByToken(token).orElse(null);
        boolean previousCompleted = true;
        for (PracticeTopic topic : topics.findBySubjectIdOrderByDisplayOrderAsc(subjectId)) {
            long questionCount = questions.findByTopicIdAndActiveTrueOrderByIdAsc(topic.getId()).size();
            int score = user == null ? 0 : moduleScore(user, topic);
            boolean completed = user != null && score >= MODULE_PASS_SCORE;
            boolean locked = !previousCompleted;
            result.add(new PracticeDtos.TopicResponse(topic, questionCount, locked, completed, score, MODULE_PASS_SCORE));
            previousCompleted = completed;
        }
        return result;
    }

    public List<PracticeDtos.QuestionResponse> questions(Long topicId) {
        List<PracticeDtos.QuestionResponse> result = new ArrayList<PracticeDtos.QuestionResponse>();
        for (PracticeQuestion question : questions.findByTopicIdAndActiveTrueOrderByIdAsc(topicId)) {
            result.add(new PracticeDtos.QuestionResponse(question, options.findByQuestionIdOrderByDisplayOrderAsc(question.getId())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PracticeDtos.PlanResponse> plans(String token) {
        AppUser user = authService.requireUser(token);
        List<UserPracticeAttempt> userAttempts = attempts.findByUserIdOrderByAttemptedAtDesc(user.getId());
        List<PracticeDtos.PlanResponse> result = new ArrayList<PracticeDtos.PlanResponse>();

        for (PracticeSubject subject : subjects.findAllByOrderByDisplayOrderAsc()) {
            List<PracticeTopic> subjectTopics = topics.findBySubjectIdOrderByDisplayOrderAsc(subject.getId());
            List<UserPracticeAttempt> subjectAttempts = attemptsForSubject(userAttempts, subject.getId());
            long totalQuestions = questions.countByTopicSubjectIdAndActiveTrue(subject.getId());
            int health = estimateHealth(totalQuestions, subjectAttempts);
            PracticeDtos.PlanResponse plan = new PracticeDtos.PlanResponse(
                    subject.getId(),
                    subject.getName(),
                    status(health),
                    health,
                    health < 60 ? 35 : health < 80 ? 25 : 15,
                    totalQuestions,
                    subjectAttempts.size(),
                    Math.min(15, totalQuestions)
            );

            boolean previousCompleted = true;
            for (int i = 0; i < subjectTopics.size(); i++) {
                PracticeTopic topic = subjectTopics.get(i);
                long topicQuestionCount = questions.findByTopicIdAndActiveTrueOrderByIdAsc(topic.getId()).size();
                int moduleScore = moduleScore(user, topic);
                boolean completed = moduleScore >= MODULE_PASS_SCORE;
                boolean locked = !previousCompleted;
                plan.getSteps().add(new PracticeDtos.PlanStepResponse(
                        topic.getId(),
                        "Module " + topic.getDisplayOrder() + ": " + topic.getName(),
                        completed ? "Completed" : locked ? "Locked" : "Ready",
                        goalFor(subject, topic),
                        i == 0 ? 20 : 15,
                        topic.getDisplayOrder(),
                        locked,
                        completed,
                        moduleScore,
                        topicQuestionCount
                ));
                previousCompleted = completed;
            }
            result.add(plan);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PracticeDtos.AssessmentResponse assessment(Long subjectId, Integer limit, String token) {
        PracticeSubject subject = subjects.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found."));

        AppUser user = token == null || token.trim().isEmpty() ? null : authService.findByToken(token).orElse(null);
        List<PracticeTopic> subjectTopics = topics.findBySubjectIdOrderByDisplayOrderAsc(subjectId);
        if (!subjectTopics.isEmpty()) {
            PracticeTopic selectedTopic = subjectTopics.get(0);
            if (user != null) {
                for (PracticeTopic topic : subjectTopics) {
                    if (!isTopicLocked(user, topic) && moduleScore(user, topic) < MODULE_PASS_SCORE) {
                        selectedTopic = topic;
                        break;
                    }
                    if (!isTopicLocked(user, topic)) {
                        selectedTopic = topic;
                    }
                }
            }
            return assessmentForTopic(selectedTopic);
        }

        int max = limit == null ? 15 : Math.max(1, Math.min(15, limit));
        List<PracticeQuestion> pool = questions.findByTopicSubjectIdAndActiveTrueOrderByIdAsc(subjectId);
        List<PracticeDtos.QuestionResponse> selected = new ArrayList<PracticeDtos.QuestionResponse>();
        Set<String> seenPrompts = new HashSet<String>();

        for (PracticeQuestion question : pool) {
            if (selected.size() >= max) {
                break;
            }
            String promptKey = normalizePrompt(question.getPrompt());
            if (seenPrompts.contains(promptKey)) {
                continue;
            }
            List<QuestionOption> shuffledOptions = new ArrayList<QuestionOption>(
                    options.findByQuestionIdOrderByDisplayOrderAsc(question.getId()));
            Collections.shuffle(shuffledOptions);
            selected.add(new PracticeDtos.QuestionResponse(question, shuffledOptions));
            seenPrompts.add(promptKey);
        }

        return new PracticeDtos.AssessmentResponse(subject, selected);
    }

    @Transactional(readOnly = true)
    public PracticeDtos.AssessmentResponse moduleAssessment(Long topicId, String token) {
        AppUser user = authService.requireUser(token);
        PracticeTopic topic = topics.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Module not found."));
        if (isTopicLocked(user, topic)) {
            throw new IllegalArgumentException("Complete the previous module with at least 80% before starting this one.");
        }

        return assessmentForTopic(topic);
    }

    @Transactional(readOnly = true)
    public PracticeDtos.AssessmentResponse mistakeReview(String token, Integer limit) {
        AppUser user = authService.requireUser(token);
        int max = limit == null ? 15 : Math.max(1, Math.min(15, limit));
        List<PracticeDtos.QuestionResponse> selected = new ArrayList<PracticeDtos.QuestionResponse>();
        Set<Long> seenQuestions = new HashSet<Long>();

        for (UserPracticeAttempt attempt : attempts.findByUserIdOrderByAttemptedAtDesc(user.getId())) {
            Long questionId = attempt.getQuestion().getId();
            if (!seenQuestions.add(questionId)) continue;
            if (Boolean.TRUE.equals(attempt.getCorrect())) continue;
            if (!Boolean.TRUE.equals(attempt.getQuestion().getActive())) continue;

            List<QuestionOption> shuffledOptions = new ArrayList<QuestionOption>(
                    options.findByQuestionIdOrderByDisplayOrderAsc(questionId));
            Collections.shuffle(shuffledOptions);
            selected.add(new PracticeDtos.QuestionResponse(attempt.getQuestion(), shuffledOptions));
            if (selected.size() >= max) break;
        }

        return new PracticeDtos.AssessmentResponse(
                "Review Your Mistakes",
                "Retry questions you most recently answered incorrectly. Correct answers remove them from this review queue.",
                selected,
                true
        );
    }

    private PracticeDtos.AssessmentResponse assessmentForTopic(PracticeTopic topic) {
        List<PracticeDtos.QuestionResponse> selected = new ArrayList<PracticeDtos.QuestionResponse>();
        for (PracticeQuestion question : questions.findByTopicIdAndActiveTrueOrderByIdAsc(topic.getId())) {
            List<QuestionOption> shuffledOptions = new ArrayList<QuestionOption>(
                    options.findByQuestionIdOrderByDisplayOrderAsc(question.getId()));
            Collections.shuffle(shuffledOptions);
            selected.add(new PracticeDtos.QuestionResponse(question, shuffledOptions));
        }
        return new PracticeDtos.AssessmentResponse(topic, selected);
    }

    @Transactional
    public PracticeDtos.AttemptResponse submitAttempt(PracticeDtos.SubmitAttemptRequest request) {
        AppUser user = authService.requireUser(request.getToken());
        PracticeQuestion question = questions.findById(request.getQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("Question not found."));
        if (!Boolean.TRUE.equals(question.getActive())) {
            throw new IllegalArgumentException("This question is currently disabled.");
        }
        if (isTopicLocked(user, question.getTopic())) {
            throw new IllegalArgumentException("This module is locked. Complete the previous module first.");
        }
        QuestionOption selected = options.findById(request.getSelectedOptionId())
                .orElseThrow(() -> new IllegalArgumentException("Selected option not found."));

        if (!selected.getQuestion().getId().equals(question.getId())) {
            throw new IllegalArgumentException("Selected option does not belong to this question.");
        }
        QuestionOption correctOption = null;
        for (QuestionOption option : options.findByQuestionIdOrderByDisplayOrderAsc(question.getId())) {
            if (Boolean.TRUE.equals(option.getCorrectOption())) {
                correctOption = option;
                break;
            }
        }
        if (correctOption == null) {
            throw new IllegalArgumentException("Correct option is missing for this question.");
        }

        UserPracticeAttempt attempt = new UserPracticeAttempt();
        attempt.setUser(user);
        attempt.setQuestion(question);
        attempt.setSelectedOption(selected);
        attempt.setCorrect(Boolean.TRUE.equals(selected.getCorrectOption()));
        attempt.setTimeTakenSeconds(request.getTimeTakenSeconds() == null ? 0 : Math.max(0, request.getTimeTakenSeconds()));
        attempts.save(attempt);

        return new PracticeDtos.AttemptResponse(Boolean.TRUE.equals(selected.getCorrectOption()), question.getExplanation(), correctOption);
    }

    private List<UserPracticeAttempt> attemptsForSubject(List<UserPracticeAttempt> userAttempts, Long subjectId) {
        List<UserPracticeAttempt> result = new ArrayList<UserPracticeAttempt>();
        for (UserPracticeAttempt attempt : userAttempts) {
            if (attempt.getQuestion().getTopic().getSubject().getId().equals(subjectId)) {
                result.add(attempt);
            }
        }
        return result;
    }

    private List<PracticeQuestion> questionPool(Long subjectId, boolean advanced) {
        List<PracticeQuestion> all = questions.findByTopicSubjectIdAndActiveTrueOrderByIdAsc(subjectId);
        List<PracticeQuestion> filtered = new ArrayList<PracticeQuestion>();
        for (PracticeQuestion question : all) {
            PracticeQuestion.Difficulty difficulty = question.getDifficulty();
            if (advanced) {
                if (PracticeQuestion.Difficulty.MEDIUM.equals(difficulty)
                        || PracticeQuestion.Difficulty.HARD.equals(difficulty)) {
                    filtered.add(question);
                }
            } else if (PracticeQuestion.Difficulty.EASY.equals(difficulty)
                    || PracticeQuestion.Difficulty.MEDIUM.equals(difficulty)) {
                filtered.add(question);
            }
        }
        return filtered.size() >= 10 ? filtered : all;
    }

    private boolean canUseAdvancedQuestions(Long subjectId, String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        AppUser user = authService.findByToken(token).orElse(null);
        if (user == null) {
            return false;
        }

        long totalAttempts = attempts.countByUserIdAndQuestionTopicSubjectId(user.getId(), subjectId);
        if (totalAttempts < 10) {
            return false;
        }
        long correctAttempts = attempts.countByUserIdAndQuestionTopicSubjectIdAndCorrectTrue(user.getId(), subjectId);
        return correctAttempts * 100.0 / totalAttempts >= 75.0;
    }

    private boolean isTopicLocked(AppUser user, PracticeTopic topic) {
        List<PracticeTopic> subjectTopics = topics.findBySubjectIdOrderByDisplayOrderAsc(topic.getSubject().getId());
        for (PracticeTopic current : subjectTopics) {
            if (current.getId().equals(topic.getId())) {
                return false;
            }
            if (moduleScore(user, current) < MODULE_PASS_SCORE) {
                return true;
            }
        }
        return false;
    }

    private int moduleScore(AppUser user, PracticeTopic topic) {
        List<PracticeQuestion> topicQuestions = questions.findByTopicIdAndActiveTrueOrderByIdAsc(topic.getId());
        if (topicQuestions.isEmpty()) {
            return 0;
        }

        Map<Long, Boolean> latestByQuestion = new HashMap<Long, Boolean>();
        for (UserPracticeAttempt attempt : attempts.findByUserIdAndQuestionTopicIdOrderByAttemptedAtDesc(user.getId(), topic.getId())) {
            Long questionId = attempt.getQuestion().getId();
            if (!latestByQuestion.containsKey(questionId)) {
                latestByQuestion.put(questionId, Boolean.TRUE.equals(attempt.getCorrect()));
            }
        }

        int correct = 0;
        for (PracticeQuestion question : topicQuestions) {
            if (Boolean.TRUE.equals(latestByQuestion.get(question.getId()))) {
                correct++;
            }
        }
        return (int) Math.round(correct * 100.0 / topicQuestions.size());
    }

    private String normalizePrompt(String prompt) {
        return prompt == null ? "" : prompt.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private int estimateHealth(long totalQuestions, List<UserPracticeAttempt> subjectAttempts) {
        if (subjectAttempts.isEmpty()) {
            return 35;
        }

        int correct = 0;
        Set<Long> uniqueQuestions = new HashSet<Long>();
        for (UserPracticeAttempt attempt : subjectAttempts) {
            if (Boolean.TRUE.equals(attempt.getCorrect())) {
                correct++;
            }
            uniqueQuestions.add(attempt.getQuestion().getId());
        }

        double accuracy = correct * 100.0 / subjectAttempts.size();
        double coverage = totalQuestions == 0 ? 0 : uniqueQuestions.size() * 100.0 / totalQuestions;
        return (int) Math.max(0, Math.min(100, Math.round((accuracy * 0.75) + (coverage * 0.25))));
    }

    private String status(int health) {
        if (health >= 80) return "STRONG";
        if (health >= 60) return "STABLE";
        return "DECAYING";
    }

    private String activityForStep(int index) {
        if (index == 0) return "Concept warm-up";
        if (index == 1) return "Focused MCQ practice";
        return "Timed assessment prep";
    }

    private String goalFor(PracticeSubject subject, PracticeTopic topic) {
        return "Practice " + topic.getName() + " in " + subject.getName() + " and review explanations for missed questions.";
    }
}
