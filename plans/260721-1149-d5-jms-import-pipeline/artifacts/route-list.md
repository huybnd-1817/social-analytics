# Route List

## REST & MVC Routes

| ROUTE### | Method | Path | Controller | Auth | Description |
|----------|--------|------|-----------|------|-------------|
| ROUTE001 | GET | / | DashboardController | authenticated | Dashboard view (Thymeleaf; shows user info + logout) |
| ROUTE002 | GET | /login | LoginController | public | Login page (Thymeleaf; OAuth2 provider buttons) |
| ROUTE003 | GET | /oauth2/authorization/facebook | Spring Security | public | Redirect to Facebook OAuth2 authorization |
| ROUTE004 | GET | /oauth2/authorization/twitter | Spring Security | public | Redirect to Twitter/X OAuth2 authorization |
| ROUTE005 | GET | /login/oauth2/code/facebook | Spring Security | public | Facebook OAuth2 callback; exchanges code for token; upserts User+SocialAccount |
| ROUTE006 | GET | /login/oauth2/code/twitter | Spring Security | public | Twitter OAuth2 callback; exchanges code for token; upserts User+SocialAccount |
| ROUTE007 | POST | /logout | Spring Security | authenticated | Logout; invalidates session; CSRF-protected |
| ROUTE008 | GET | /posts | PostController | authenticated | Paginated list of ACTIVE posts (JSON) |
| ROUTE009 | DELETE | /posts/{id} | PostController | authenticated | Soft-delete post (sets status=DELETED) |
| ROUTE010 | POST | /import-posts | ImportController | authenticated | Bulk import posts from multipart .xlsx upload |
| ROUTE011 | GET | /export-report | ExcelExportController | authenticated | Export ACTIVE posts + latest metrics as .xlsx download |
| ROUTE012 | GET | /metrics | MetricController | authenticated | Aggregated social metrics (JSON) |
| ROUTE013 | GET | /metrics/last-updated | MetricController | authenticated | Timestamp of last crawl (JSON) |
| ROUTE014 | GET | /swagger-ui.html | springdoc | public (dev) | Swagger UI — disabled in prod |
| ROUTE015 | GET | /v3/api-docs | springdoc | public (dev) | OpenAPI 3 JSON spec — disabled in prod |

## Security Configuration

**Source:** `config/SecurityConfig.java`

- **Public paths:** `/login`, `/oauth2/**`, `/login/oauth2/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/error`, static assets (`/css/**`, `/js/**`, `/images/**`)
- **All other paths:** require authenticated session
- **CSRF:** enabled (POST /logout requires valid CSRF token)
- **Session:** stateful (Spring Security default)
- **Logout:** POST /logout → invalidates session + clears auth → redirects to /login?logout

## Notes

- No versioned API prefix (e.g. `/api/v1`) — all routes at root context
- Swagger UI and OpenAPI spec are conditionally enabled: `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` are `true` in dev, `false` in prod
- OAuth2 redirect URIs match Spring Security default pattern: `{baseUrl}/login/oauth2/code/{registrationId}`
