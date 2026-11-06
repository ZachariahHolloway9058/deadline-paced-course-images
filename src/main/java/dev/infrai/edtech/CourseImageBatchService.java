package dev.infrai.edtech;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class CourseImageBatchService {
    private final InfraiClient infrai;

    public CourseImageBatchService(InfraiClient infrai) {
        this.infrai = infrai;
    }

    public EducatorReport process(CourseBacklog backlog, Instant now) {
        int allowance = batchAllowance(backlog, now);
        for (String image : backlog.images()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("course", backlog.courseId());
            payload.put("image", image);
            payload.put("learner_deadline", backlog.learnerDeadline().toString());
            payload.put("educator_report", backlog.educatorReport());
            infrai.publish(backlog.courseId(), payload, InfraiClient.idempotency("queue", backlog.courseId() + image));
        }

        List<Map<String, Object>> messages = infrai.consume(backlog.courseId(), allowance, 120);
        List<String> pacedImages = messages.stream()
                .map(message -> message.get("payload"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(payload -> backlog.courseId().equals(String.valueOf(payload.get("course"))))
                .map(payload -> String.valueOf(payload.get("image")))
                .limit(allowance)
                .toList();

        List<CompletableFuture<Map<String, Object>>> jobs = pacedImages.stream()
                .map(image -> CompletableFuture.supplyAsync(() -> infrai.resize(
                        image, 1280, 720,
                        InfraiClient.idempotency("resize", backlog.courseId() + image))))
                .toList();
        List<Map<String, Object>> completed = jobs.stream().map(CompletableFuture::join).toList();
        return new EducatorReport(backlog.courseId(), allowance, completed.size(),
                backlog.images().size() - completed.size(), backlog.educatorReport(), completed);
    }

    public static int batchAllowance(CourseBacklog backlog, Instant now) {
        int hours = (int) Math.max(0, Duration.between(now, backlog.learnerDeadline()).toHours());
        int urgencyLimit = hours < 6 ? 20 : hours < 24 ? 10 : 5;
        return Math.max(1, Math.min(backlog.images().size(), urgencyLimit));
    }

    public record CourseBacklog(
            String courseId,
            Instant deliveryAt,
            Instant learnerDeadline,
            String educatorReport,
            List<String> images
    ) {
        public CourseBacklog {
            images = List.copyOf(images);
            if (!learnerDeadline.isAfter(deliveryAt)) {
                throw new IllegalArgumentException("Learner deadline must follow course delivery");
            }
            if (images.isEmpty()) throw new IllegalArgumentException("At least one image is required");
        }
    }

    public record EducatorReport(
            String courseId,
            int allowedThisRun,
            int completed,
            int remaining,
            String destination,
            List<Map<String, Object>> results
    ) {
        public Map<String, Object> asMap() {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("course", courseId);
            report.put("allowed_this_run", allowedThisRun);
            report.put("completed", completed);
            report.put("remaining", remaining);
            report.put("educator_report", destination);
            report.put("results", new ArrayList<>(results));
            return report;
        }
    }
}
