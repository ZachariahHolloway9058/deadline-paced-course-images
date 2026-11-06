package dev.infrai.edtech;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public final class CourseImageBatchServiceTest {
    public static void main(String[] args) {
        require(InfraiClient.imageReference("base64:aGVsbG8=").equals(java.util.Map.of("base64", "aGVsbG8=")),
                "base64 input must not be sent as a URL");
        require(InfraiClient.imageReference("image_id:uploaded-123").equals(java.util.Map.of("image_id", "uploaded-123")),
                "uploaded image IDs must not be sent as URLs");
        require(InfraiClient.imageReference("https://example.edu/image.png").equals(
                java.util.Map.of("url", "https://example.edu/image.png")), "URLs must remain supported");
        Instant now = Instant.parse("2026-09-26T09:00:00Z");
        List<String> backlog = Collections.nCopies(18, "https://assets.example.edu/lesson.png");
        CourseImageBatchService.CourseBacklog urgent = new CourseImageBatchService.CourseBacklog(
                "risk-101", now.minusSeconds(3600), now.plusSeconds(5 * 3600), "faculty-ledger", backlog);
        CourseImageBatchService.CourseBacklog scheduled = new CourseImageBatchService.CourseBacklog(
                "risk-102", now.minusSeconds(3600), now.plusSeconds(30 * 3600), "faculty-ledger", backlog);

        require(CourseImageBatchService.batchAllowance(urgent, now) == 18,
                "an urgent learner deadline should release the full 18-image backlog");
        require(CourseImageBatchService.batchAllowance(scheduled, now) == 5,
                "a later learner deadline should preserve the five-image pacing cap");
        System.out.println("PASS deadline pacing: urgent=18 scheduled=5");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
