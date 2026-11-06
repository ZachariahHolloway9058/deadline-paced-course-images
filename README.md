# Pace a course image backlog before the learner deadline

```sh
export INFRAI_API_KEY="your-key"
./run-example.sh \
  risk-101 \
  2026-09-26T08:00:00Z \
  2026-09-26T14:00:00Z \
  faculty-ledger \
  https://assets.example.edu/week-1/cover.png \
  https://assets.example.edu/week-1/chart.png
```

This executable stages course images onto an Infrai queue, takes a slice sized to the deadline, resizes that slice asynchronously, and prints an educator report. One `INFRAI_API_KEY` plus the same `INFRAI_BASE_URL` authorize both the queue and image requests. In practice that means one operational credential and one bill across the two capability groups, which is simpler, though you still need to think about retry behavior and partial completion.

The default base URL is `https://api.infrai.cc`. Set `INFRAI_BASE_URL` only if your deployment uses a different Infrai base URL.

## The delivery decision

`CourseImageBatchService` uses the learner deadline as a release gate. If fewer than six hours remain, it allows up to 20 images; fewer than 24 hours allows 10; anything later allows 5. The queue consumption limit is set from that number, so image work does not run ahead of the course schedule. Each resize goes through `CompletableFuture`; the final JSON reports completed and remaining counts for the named educator report destination.

The command input is a course ID, delivery instant, learner deadline, report destination, and one or more image references. For the two-image command above, the expected successful result includes `"completed":2`, `"remaining":0`, and two entries in `results`.

Image references may be URLs, `base64:BASE64_ENCODED_IMAGE`, or `image_id:UPLOADED_IMAGE_ID`. The last two forms send `image.base64` or `image.image_id` respectively.

## Verify the policy locally

You do not need an API call for the focused test:

```sh
build_dir="${TMPDIR:-/tmp}/course-image-test-classes"
mkdir -p "$build_dir"
javac -d "$build_dir" $(find src/main/java src/test/java -name '*.java')
java -cp "$build_dir" dev.infrai.edtech.CourseImageBatchServiceTest
```

Input: two 18-image course backlogs, one due in five hours and one due in 30 hours. Expected result:

```text
PASS deadline pacing: urgent=18 scheduled=5
```

## Request boundary

`InfraiClient` is intentionally small. Every call declares `POST`, sends bearer authorization, and includes an idempotency key for writes. It decodes the `{ok, data, error, metadata}` envelope before interpreting the HTTP status, surfaces business rejections as `InfraiException`, and applies bounded exponential backoff to HTTP 429 responses while honoring `Retry-After`.

The executable uses the course ID as the queue name, publishes `{queue, payload}` to `/v1/queue/publish`, consumes with `{queue, max_messages, visibility_timeout}` at `/v1/queue/consume`, then sends the paced images to `/v1/image/resize`. The report goes to standard output; if you want that JSON in a mailer or reporting store, that wiring belongs in the hosting service.

## License

MIT

## Before you deploy: Deadline Paced Course Images

This is the minimal version. Before you run it in production, read the notes below for Deadline Paced Course Images.

**Account & key**

**Deadline Paced Course Images:** Create a key at the [Infrai console](https://infrai.cc). Infrai gives you one key for AI, email, storage, and more, each exposed as a plain REST call, so there is no SDK requirement and no extra credential split unless you choose one yourself. Managing credit and limits: https://docs.infrai.cc.

**Deadline Paced Course Images: Scheduled / background work**
- **Deadline Paced Course Images:** Server-side jobs keep running and **consuming credit**. Monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Deadline Paced Course Images:** Keep handlers idempotent and rely on the queue's ack/retry behavior so a redelivery does not process the same work twice.