---
status: draft
authored_by: takumi
created: 2026-07-13
lang: en
---

# Screens — Excel Export Posts & Metrics Report

## Screen List

N/A — background feature; no user-facing screens.

This feature is a pure HTTP streaming endpoint (`GET /export-report`). No UI screen exists at D2 scope. Screen specs will be considered if a frontend download button is added in a later sprint.

## User Journey

API-level journey (no UI screens):

1. Caller issues `GET /export-report` (browser address bar, API client, or future frontend button).
2. Server fetches all ACTIVE posts and resolves the latest metric per post.
3. Server builds the `.xlsx` workbook in memory and writes it to the response stream.
4. Caller receives an HTTP 200 response with `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` and `Content-Disposition: attachment; filename=report_{timestamp}.xlsx`.
5. The browser or client saves the file to disk; the user opens it in their spreadsheet application.
