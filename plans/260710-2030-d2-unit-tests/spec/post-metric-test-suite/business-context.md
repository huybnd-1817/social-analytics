---
status: draft
authored_by: takumi
created: 2026-07-10
lang: en
---

# PostMetricTestSuite — Business Context

## Why It Matters

Untested code is a liability that compounds every time the project grows. This work establishes a baseline automated test suite for the Social Analytics backend so that the team can confidently change, extend, and review the service without manually verifying every endpoint. The suite covers the two most critical operations the system performs today — reading social posts and soft-deleting them — and gives reviewers a machine-checked contract they can cite in pull requests.

## Who Uses It

- **Backend developer** — runs the tests locally before pushing; the suite catches regressions in pagination, soft-delete logic, and HTTP error codes without needing a running database.
- **Code reviewer** — reads test names and scenarios as a specification; confirms that the service's behavior is intentional and provable, not assumed.
- **Team lead / tech lead** — uses the passing suite as a gate before merging new features into the main branch.

## What They Do

1. A developer adds the in-memory database library to the project's test configuration so that database-layer tests can run without an external database server.
2. The developer writes tests for the post service — verifying that only active posts are returned when listing, that deleting a post marks it as removed without actually erasing it, and that deleting a non-existent post produces a clear error.
3. The developer writes a test for the metric service confirming that all metrics are returned and correctly formatted.
4. The developer writes database-layer tests that verify the repository correctly filters posts by their status and looks up individual posts by both identifier and status together.
5. The developer writes web-layer tests that confirm the posts endpoint returns the expected structure and status codes — including the error response when a post cannot be found.
6. When all tests pass, the suite is committed and becomes part of the project's continuous safety net — any future change that breaks these behaviors will be caught automatically.

## Unresolved Questions

None.
