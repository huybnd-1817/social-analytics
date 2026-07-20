---
status: draft
authored_by: takumi
created: 2026-07-15
lang: en
---

# Screens — D4 Automated Crawl Job

## SCR-dashboard-last-updated — Dashboard "Last Updated" section

**Route:** `GET /`
**Template:** `dashboard.html` (extends existing F005 dashboard card)

### Purpose

Adds a "Last Updated" row to the existing user-info card on the dashboard. Shows when the hourly crawl job last completed, giving the authenticated user confidence that metrics are fresh.

### Layout change

The existing card has: Email row, Provider row, Logout button.
D4 inserts a new **Last Updated** row between the provider row and the logout button.

```
┌────────────────────────────────────────┐
│  Social Analytics                       │
│  Welcome, {name}                        │
│  ─────────────────────────────────────  │
│  Email    │ {email or N/A}              │
│  Provider │ [Facebook badge]            │
│  Last crawled │ {timestamp or Never}    │  ← NEW
│  ─────────────────────────────────────  │
│  [Logout]                               │
└────────────────────────────────────────┘
```

### Template logic

```
th:text="${lastCrawledAt != null} ? ${#temporals.format(lastCrawledAt, 'yyyy-MM-dd HH:mm:ss')} + ' UTC' : 'Never'"
```

Actually since `lastCrawledAt` is `java.time.Instant` (not `java.time.LocalDateTime`), use `.toString()` which produces ISO-8601. Simpler and accurate:

```html
<td th:text="${lastCrawledAt != null} ? ${lastCrawledAt} : 'Never'"></td>
```

### States

| State | Display |
|-------|---------|
| Job never ran | "Never" |
| Job ran | ISO-8601 timestamp string (e.g. `2026-07-15T07:00:01.234Z`) |

### No new page/route

This is a modification to the existing dashboard template, not a new screen.
