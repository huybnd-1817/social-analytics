---
status: draft
authored_by: takumi
created: 2026-07-13
lang: en
---

# Business Context — Excel Export Posts & Metrics Report

## Why It Matters

Teams tracking social-media performance need a quick way to pull their post data and engagement numbers into a spreadsheet for offline analysis, sharing with stakeholders, or feeding into reporting tools. This feature gives any user a one-click download of all active posts and their latest metric snapshot — no manual copy-paste, no separate analytics tool required at the D2 stage.

## Who Uses It

- **Analyst / operator** — downloads the report to review post performance across platforms, build pivot tables, or prepare stakeholder presentations. They need accurate, up-to-date numbers in a format they can open immediately.
- **Developer / QA** — uses the endpoint during Day-2 validation to confirm that the export pipeline (post selection, metric resolution, file generation) works end-to-end before the OAuth2 and UI layers land in later sprints.

## What They Do

1. The user opens a browser or API client and requests the export endpoint — no form to fill in, no options to set.
2. The system collects every active post in the database and pairs each one with its most recent engagement snapshot (likes, shares, comments, followers, reach, impressions).
3. Posts that have never been crawled still appear in the file with blank or zero engagement figures, so the full post inventory is always represented.
4. The system assembles a spreadsheet file with one header row and one data row per post, then delivers it as a download named with the current date and time.
5. The user opens the file in their spreadsheet tool of choice and proceeds with their analysis.

## Unresolved Questions

None.
