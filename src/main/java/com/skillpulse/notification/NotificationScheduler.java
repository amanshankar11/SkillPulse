package com.skillpulse.notification;

import com.skillpulse.auth.AppUser;
import com.skillpulse.auth.UserRepository;
import com.skillpulse.dashboard.DashboardService;
import com.skillpulse.dashboard.SkillSummary;
import com.skillpulse.practice.UserPracticeAttempt;
import com.skillpulse.practice.UserPracticeAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class NotificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private final UserRepository users;
    private final UserPracticeAttemptRepository attempts;
    private final NotificationPreferenceService preferenceService;
    private final NotificationPreferenceRepository preferences;
    private final NotificationMailService mail;
    private final DashboardService dashboard;
    private final ZoneId zone;

    public NotificationScheduler(UserRepository users, UserPracticeAttemptRepository attempts,
            NotificationPreferenceService preferenceService, NotificationPreferenceRepository preferences,
            NotificationMailService mail, DashboardService dashboard,
            @Value("${skillpulse.notifications.zone:Asia/Kolkata}") String zoneId) {
        this.users = users; this.attempts = attempts; this.preferenceService = preferenceService;
        this.preferences = preferences; this.mail = mail; this.dashboard = dashboard; this.zone = ZoneId.of(zoneId);
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "${skillpulse.notifications.zone:Asia/Kolkata}")
    public void dailyPracticeReminders() {
        LocalDate today = LocalDate.now(zone);
        for (AppUser user : users.findAll()) safely(() -> {
            NotificationPreference value = preferenceService.forUser(user);
            if (!Boolean.TRUE.equals(value.getDailyPracticeReminders()) || today.equals(value.getLastDailyReminderDate())) return;
            if (!practiceDates(user).contains(today)) {
                mail.send(user, "Your SkillPulse practice is waiting", greeting(user)
                        + "\n\nYou have not completed an assessment today. A short practice session will keep your skill health moving forward.\n\n- SkillPulse");
                value.setLastDailyReminderDate(today); preferences.save(value);
            }
        });
    }

    @Scheduled(cron = "0 0 20 * * *", zone = "${skillpulse.notifications.zone:Asia/Kolkata}")
    public void streakWarnings() {
        LocalDate today = LocalDate.now(zone);
        for (AppUser user : users.findAll()) safely(() -> {
            NotificationPreference value = preferenceService.forUser(user);
            if (!Boolean.TRUE.equals(value.getStreakNotifications()) || today.equals(value.getLastStreakNotificationDate())) return;
            Set<LocalDate> dates = practiceDates(user);
            if (!dates.contains(today) && dates.contains(today.minusDays(1))) {
                int streak = streakEndingOn(dates, today.minusDays(1));
                mail.send(user, "Protect your " + streak + "-day SkillPulse streak", greeting(user)
                        + "\n\nYour " + streak + "-day practice streak is about to break. Complete one assessment today to keep it active.\n\n- SkillPulse");
                value.setLastStreakNotificationDate(today); preferences.save(value);
            }
        });
    }

    @Scheduled(cron = "0 15 20 * * *", zone = "${skillpulse.notifications.zone:Asia/Kolkata}")
    public void skillDecayAlerts() {
        LocalDate today = LocalDate.now(zone);
        for (AppUser user : users.findAll()) safely(() -> {
            NotificationPreference value = preferenceService.forUser(user);
            if (!Boolean.TRUE.equals(value.getSkillDecayAlerts()) || today.equals(value.getLastDecayAlertDate())) return;
            @SuppressWarnings("unchecked") List<SkillSummary> skills = (List<SkillSummary>) dashboard.summary(user).get("skills");
            StringBuilder atRisk = new StringBuilder();
            for (SkillSummary skill : skills) if (skill.getScore() < 60) {
                if (atRisk.length() > 0) atRisk.append("\n");
                atRisk.append("- ").append(skill.getName()).append(": ").append(skill.getScore()).append("%");
            }
            if (atRisk.length() > 0) {
                mail.send(user, "SkillPulse skills need attention", greeting(user)
                        + "\n\nThese skills are currently below 60% health:\n" + atRisk
                        + "\n\nOpen your practice plan to strengthen them.\n\n- SkillPulse");
                value.setLastDecayAlertDate(today); preferences.save(value);
            }
        });
    }

    @Scheduled(cron = "0 0 8 * * MON", zone = "${skillpulse.notifications.zone:Asia/Kolkata}")
    public void weeklyProgressReports() {
        LocalDate today = LocalDate.now(zone);
        for (AppUser user : users.findAll()) safely(() -> {
            NotificationPreference value = preferenceService.forUser(user);
            if (!Boolean.TRUE.equals(value.getWeeklyProgressReport()) || today.equals(value.getLastWeeklyReportDate())) return;
            Map<String, Object> summary = dashboard.summary(user);
            mail.send(user, "Your weekly SkillPulse progress", greeting(user) + "\n\nHere is your weekly snapshot:\n"
                    + "- Overall skill health: " + summary.get("overallHealth") + "%\n"
                    + "- Practice streak: " + summary.get("practiceStreakDays") + " days\n"
                    + "- Practice time: " + summary.get("practiceMinutesThisWeek") + " minutes\n"
                    + "- Skills needing attention: " + summary.get("skillsNeedingAttention")
                    + "\n\nKeep learning consistently.\n\n- SkillPulse");
            value.setLastWeeklyReportDate(today); preferences.save(value);
        });
    }

    private Set<LocalDate> practiceDates(AppUser user) {
        Set<LocalDate> dates = new HashSet<LocalDate>();
        for (UserPracticeAttempt attempt : attempts.findByUserIdOrderByAttemptedAtDesc(user.getId()))
            dates.add(attempt.getAttemptedAt().atZone(zone).toLocalDate());
        return dates;
    }
    private int streakEndingOn(Set<LocalDate> dates, LocalDate date) {
        int streak = 0; while (dates.contains(date.minusDays(streak))) streak++; return streak;
    }
    private String greeting(AppUser user) { return "Hello " + user.getName() + ","; }
    private void safely(Runnable task) {
        try { task.run(); } catch (RuntimeException ex) { log.error("Notification delivery failed", ex); }
    }
}
