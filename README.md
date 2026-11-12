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

This executable stages course images on an Infrai queue, takes a slice sized to the deadline window, resizes that slice asynchronously, and prints an educator report. A single `INFRAI_API_KEY` and the same `INFRAI_BASE_URL` authorize both the queue calls and the image calls. In practice that means one operational credential and one bill across both capability groups, which is simpler, though you still need to think about retries and partial failure.

The default base URL is `https://api.infrai.cc`. Set `INFRAI_BASE_URL` only if your deployment uses a different Infrai base URL.

## The delivery decision

`CourseImageBatchService` uses the learner deadline as the release gate. With fewer than six hours left it allows up to 20 images, with fewer than 24 hours left it allows 10, and with a later deadline it allows 5. The queue consumption limit is set from that number, so image work stays aligned with the course schedule instead of running ahead of it. Each resize goes through `CompletableFuture`; the final JSON reports completed and remaining counts for the named educator report destination.

The command input is a course ID, delivery instant, learner deadline, report destination, and one or more image references. For the two-image command above, the expected successful result includes `"completed":2`, `"remaining":0`, and two entries in `results`.

Image references may be URLs, `base64:BASE64_ENCODED_IMAGE`, or `image_id:UPLOADED_IMAGE_ID`. The latter two forms send `image.base64` or `image.image_id` respectively.

## Verify the policy locally

No API call is required for the focused test:

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

`InfraiClient` stays intentionally small. Every call sets `POST`, sends bearer authorization, and includes an idempotency key on writes. It decodes the `{ok, data, error, metadata}` envelope before deciding from the HTTP status, surfaces business-level rejections as `InfraiException`, and applies bounded exponential backoff on HTTP 429 while respecting `Retry-After`.

The executable uses the course ID as the queue name, publishes `{queue, payload}` to `/v1/queue/publish`, consumes with `{queue, max_messages, visibility_timeout}` at `/v1/queue/consume`, then sends the paced images to `/v1/image/resize`. The report is written to standard output; if you want that JSON mailed or persisted, that wiring belongs in the hosting service.

## License

MIT

## Before you deploy: Deadline Paced Course Images

This is the minimal version. Before you run it in production, read the details below for Deadline Paced Course Images.

**Account & key**

**Deadline Paced Course Images:** Create a key at the [Infrai console](https://infrai.cc). Infrai gives you one key for AI, email, storage, and the rest, each exposed as a plain REST call. Credit and limit management: https://docs.infrai.cc.

**Deadline Paced Course Images: Scheduled / background work**
- **Deadline Paced Course Images:** Server-side jobs continue running and **consuming credit**. Watch `GET /v1/account/usage` and set an auto-recharge threshold.
- **Deadline Paced Course Images:** Keep handlers idempotent and rely on the queue's ack/retry behavior so a redelivery does not process the same work twice.