---
status: draft
authored_by: takumi
created: 2026-07-13
lang: en
---

# Business Context — Excel Bulk Import Posts

## Why It Matters

Social media teams accumulate large volumes of post data across platforms (Facebook, Twitter) that cannot be entered one record at a time without prohibitive manual effort. Excel Bulk Import Posts lets an operator load hundreds of posts in a single upload, cutting data-entry time from hours to seconds and making the analytics platform useful from day one with pre-existing content.

## Who Uses It

- **Platform operator / data analyst** — uploads prepared spreadsheets of historical or collected post data to seed the analytics database; needs confidence that either all rows landed correctly or nothing was changed, with a clear count of what succeeded or failed.

## What They Do

1. Operator prepares an Excel spreadsheet with one row per social post, filling in the platform name, the platform's own post identifier, and any available fields (title, content, URL, publication date).
2. Operator submits the spreadsheet to the import endpoint.
3. The system inspects every single row for completeness and correctness before touching the database — if any row fails (unknown platform, missing required field, duplicate post already on record), the entire batch is cancelled and nothing is saved.
4. If all rows are valid, the system saves every post in one operation and confirms success with a summary showing the total count and zero failures.
5. If any row was rejected, the system confirms the failure with a summary showing zero successes and the number of rejected rows, while still recording the attempt for audit purposes.
6. Operator reviews the summary counts; on failure, corrects the spreadsheet and re-uploads until a clean import is confirmed.

## Unresolved Questions

- **Operator error feedback**: The current design returns counts only (total / success / failed). Stakeholders may ask for a row-level error report (e.g., "row 3 had an unknown platform"). Confirm whether counts alone are sufficient for the first release or if a detailed error list is needed.
