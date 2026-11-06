package dev.infrai.edtech;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public final class CourseBacklogRunner {
    private CourseBacklogRunner() {}

    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: CourseBacklogRunner COURSE DELIVERY_AT DEADLINE REPORT_DESTINATION IMAGE [IMAGE ...]");
            System.exit(2);
        }
        List<String> images = Arrays.asList(args).subList(4, args.length);
        CourseImageBatchService.CourseBacklog backlog = new CourseImageBatchService.CourseBacklog(
                args[0], Instant.parse(args[1]), Instant.parse(args[2]), args[3], images);
        InfraiConfig config = InfraiConfig.fromEnvironment();
        CourseImageBatchService service = new CourseImageBatchService(new InfraiClient(config));
        System.out.println(Json.stringify(service.process(backlog, Instant.now()).asMap()));
    }
}
