# Permissions Matrix

## Route-Level Permissions

| ROUTE### | Path | Method | ANONYMOUS | USER | ADMIN | Notes |
|----------|------|--------|-----------|------|-------|-------|
| ROUTE001 | / | GET | ✗ → /login | ✓ | ✓ | Dashboard; requires session |
| ROUTE002 | /login | GET | ✓ | ✓ | ✓ | Public login page |
| ROUTE003 | /oauth2/authorization/facebook | GET | ✓ | ✓ | ✓ | Public — OAuth2 initiation |
| ROUTE004 | /oauth2/authorization/twitter | GET | ✓ | ✓ | ✓ | Public — OAuth2 initiation |
| ROUTE005 | /login/oauth2/code/facebook | GET | ✓ | ✓ | ✓ | Public — OAuth2 callback |
| ROUTE006 | /login/oauth2/code/twitter | GET | ✓ | ✓ | ✓ | Public — OAuth2 callback |
| ROUTE007 | /logout | POST | ✗ | ✓ | ✓ | CSRF-protected |
| ROUTE008 | /posts | GET | ✗ → /login | ✓ | ✓ | Authenticated; no role distinction |
| ROUTE009 | /posts/{id} | DELETE | ✗ → /login | ✓ | ✓ | Authenticated; no role distinction |
| ROUTE010 | /import-posts | POST | ✗ → /login | ✓ | ✓ | Authenticated; no role distinction |
| ROUTE011 | /export-report | GET | ✗ → /login | ✓ | ✓ | Authenticated; no role distinction |
| ROUTE012 | /metrics | GET | ✗ → /login | ✓ | ✓ | Authenticated |
| ROUTE013 | /metrics/last-updated | GET | ✗ → /login | ✓ | ✓ | Authenticated |
| ROUTE014 | /swagger-ui.html | GET | ✓ (dev) | ✓ (dev) | ✓ (dev) | Disabled in prod (springdoc.swagger-ui.enabled=false) |
| ROUTE015 | /v3/api-docs | GET | ✓ (dev) | ✓ (dev) | ✓ (dev) | Disabled in prod |

**Legend:** ✓ = allowed | ✗ = denied | ✗ → /login = redirect to login page

## Role Definitions

| Role | Assigned By | Notes |
|------|------------|-------|
| USER | CustomOAuth2UserService on OAuth2 login | Granted to all successfully authenticated users via `SimpleGrantedAuthority("ROLE_USER")` |
| ADMIN | Default in DB schema (`user_role DEFAULT 'ADMIN'`) | DB-level default; no separate admin-only endpoints exist yet |

## Notes

- Spring Security `authenticated()` rule covers all protected paths — no role-based route restrictions are currently in effect (USER and ADMIN have identical access).
- The `UserRole` entity field exists in the data model for future role-based access control.
- Data ownership is not enforced at the route level: any authenticated user can delete any post, view any metric, etc.
- Unauthenticated requests to protected paths receive `302 → /login` (not 401/403), because the app uses session-based auth with Thymeleaf views.
