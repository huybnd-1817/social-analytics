# D6: Final Polish — Swagger Docs & README (D6-12 to D6-15)

**Date**: 2026-07-24 01:29
**Severity**: Medium
**Component**: Documentation, REST API Contract, Build Artifacts
**Status**: Resolved

## What Happened

Closed the final four tasks of the 6-day Social Analytics Dashboard build: finalized Swagger/OpenAPI annotations, created a production-ready README, and verified the JAR builds clean.

**Scope:**
- **D6-12**: Added `@ApiResponse` and `@Parameter` annotations to all 6 REST controllers
- **D6-13/D6-14**: Wrote README.md with prerequisites, quick-start, demo flow, Swagger link
- **D6-15**: Verified `./mvnw clean package` → 100 tests pass, JAR ready

## The Brutal Truth

This felt like the trim work at the end of a long forge — not the glamorous part, but the part that makes the piece actually usable by someone who didn't build it. The README came late enough that a first-time user would've hit friction. The Swagger annotations started incomplete and a reviewer caught a HIGH correctness issue that would've shipped misguided to API clients.

The galling part: the 400-vs-500 mistake (ExchangeRateController) was the kind of thing that sounds minor in a meeting — "just an HTTP code" — but it wrecks client retry logic and observability at 2am in production when the SOAP service is flaky.

## Technical Details

### D6-12: Swagger Annotations

**What was added:**
- `@ApiResponse` and `@ApiResponses` on all 6 endpoints (200, 400, 500 codes)
- `@Parameter` on all path/query/request-body params with descriptions and examples
- Consistent `@Tag` and `@Operation` (already present; just filled the gaps)

**Controllers touched:**
```
PostController        → @ApiResponse(200), @ApiResponse(400)
ChartController       → @ApiResponse(200), @ApiResponse(400)
ExchangeRateController → @ApiResponse(200), @ApiResponse(500) [CRITICAL FIX]
ImportController      → @ApiResponse(200), @ApiResponse(400), @ApiResponse(409)
ExcelExportController → @ApiResponse(200, CSV MIME type)
MetricController      → @ApiResponse(200)
```

**The 400-vs-500 issue:**

ExchangeRateController initially had `@ApiResponse(400, "Unsupported currency code")`.

Root cause of the mistake: the annotation was written assuming "bad currency code" is a client error. But the code at runtime throws:

```java
// ExchangeRateWebServiceClient.java lines 23-37
try {
    GetExchangeRateResponse response =
            (GetExchangeRateResponse) webServiceTemplate.marshalSendAndReceive(request);
    // SOAP network failure, parse failure, timeout → all wrapped in:
    throw new RuntimeException("Failed to fetch...", e);
} catch (Exception e) {
    throw new RuntimeException(...); // No handling for IllegalArgumentException
}
```

GlobalExceptionHandler has NO handler for bare `RuntimeException`, so Spring defaults to 500. The Swagger annotation promised 400, but the code returns 500 — a lie to clients.

**Fixed annotation:**
```java
@ApiResponse(responseCode = "500", description = "SOAP call failed or upstream service error")
```

Now the annotation matches the runtime behavior. Clients see 500, know the SOAP service is down, and retry appropriately.

### Secondary catch from /review-code --pending

ChartController's `@ApiResponse(400)` only mentioned "Invalid timezone identifier" but Spring also returns 400 for:
- Invalid ISO-8601 `from`/`to` date strings (caught by `MethodArgumentTypeMismatchException`)
- Spring's built-in type mismatch handler

Description broadened to: `"Invalid timezone identifier or date-time format."` so clients understand the full scope of what can fail at 400.

### D6-13/D6-14: README.md (125 lines)

Created `/README.md` with:
1. **Overview**: what the app does, tech stack (Spring Boot 3, OAuth2, PostgreSQL, SOAP, WebSocket, Excel, JMS)
2. **Prerequisites**: Java 21, Maven 3.9+, PostgreSQL, optional OAuth2 credentials
3. **Quick Start**: 5-step flow from git clone → run application
4. **Swagger UI link**: `http://localhost:8080/swagger-ui.html`
5. **Test & JAR commands**: how to run tests and build the artifact

No invented data; all instructions pulled from the actual codebase configuration.

### D6-15: JAR Build Verification

```bash
./mvnw clean package
```

**Result:**
```
BUILD SUCCESS
[INFO] Tests run: 100, Failures: 0, Errors: 0, Skipped: 0
[INFO] JAR built: target/social-analytics-0.0.1-SNAPSHOT.jar
```

All 100 integration, unit, and smoke tests pass. JAR is at rest and ready to be shipped or run on any box with Java 21.

## What We Tried

1. **First Swagger pass**: annotations written from the controller code signature alone (no cross-reference to exception handlers) → missed the 400 vs 500 mismatch
2. **Second pass** (post-review): traced ExchangeRateController down through the SOAP client code → spotted the RuntimeException wrap-and-rethrow
3. **Validation**: `/review-code --pending` caught the incomplete ChartController description

## Root Cause Analysis

**Why 400 vs 500 was missed the first time:**

The logic of "unsupported currency code" *sounds* like a client error (400-class). But the code path never validates the currency string — it just passes it to the SOAP client, which either succeeds or throws a bare `RuntimeException` on network/parse/timeout. The assumption "bad input → 400" broke because there is no input validation, only SOAP operation that can fail.

**Why README came late:**

Tasks D6-12 through D6-15 are the "final polish" phase, typically deferred until the core features (D1-D6 reflection, SOAP, WebSocket, Excel, JMS) are done. That's sound prioritization — but it meant first-time users would have hit walls (missing OAuth2 env var docs, unclear "how do I start this?" flow).

## Lessons Learned

1. **Swagger annotations are a contract, not decoration.** A `@ApiResponse` code is a promise to clients about what HTTP status they will see. Never write it from the code structure alone — trace it down to the actual exception handlers (`GlobalExceptionHandler`) and verify the mapping. If there's no handler, that's a 500, not your guess at what it "should" be.

2. **Bare `RuntimeException` is an anti-pattern for typed error handling.** The SOAP client wraps all failures in `RuntimeException` with no way for GlobalExceptionHandler to distinguish "network down" from "parse error" from "timeout." Each should arguably map to different HTTP codes or retry strategies. Consider using checked exceptions or custom exception types so the error path can be traced in Swagger AND in observability.

3. **The reviewer agent earned its keep twice:**
   - PRIMARY: caught the correctness bug (400 vs 500)
   - SECONDARY: caught the completeness gap (ChartController description)
   - A single human reviewer might have caught one or spotted the other on re-read; two passes with different focus areas got both.

4. **README timing matters.** Write it early enough that first-time users can follow it. Write it late enough that the actual commands and paths are stable. D6-13/D6-14 is the right moment — core features done, final polish underway.

5. **Integration test count (100 tests) is a good smell.** Tests pass before docs, which pass before JAR. The reverse order (docs first, then find test failures) is a trap.

## Next Steps

- **Ship the JAR** when ready; all 100 tests green.
- **Monitor SOAP failure rates** once in production. The 500 responses will now flow through observability correctly instead of looking like server errors.
- **Consider refactoring ExchangeRateWebServiceClient** to throw custom exceptions (`SoapClientException`) instead of bare `RuntimeException`, so future exception handlers can offer finer-grained codes (503 for service unavailable, 504 for timeout, etc.). But that's a follow-up — not a blocker for ship.
- **Keep the README in sync** as features change. Link to it from the project home page.

---

**Commit:** `87d586b` — docs(swagger): finalize OpenAPI annotations and add README (D6-12 to D6-15)

**Files touched:** 16 files, +485 lines
- Controllers: 6 annotated
- README: 125 lines
- Plans & evidence: recorded for audit
