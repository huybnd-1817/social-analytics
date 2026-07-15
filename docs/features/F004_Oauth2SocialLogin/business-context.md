---
status: draft
authored_by: takumi
created: 2026-07-14
lang: en
---

# Business Context — OAuth2 Social Login

## Why It Matters

The Social Analytics Dashboard has no login mechanism today — every endpoint is publicly accessible, which means anyone who discovers the URL can read or delete data. Social login via Facebook and Twitter gives the team a fast, credential-free way to lock the door: users authenticate with accounts they already have, and the platform never stores or manages passwords. This also captures the OAuth2 access token for each connected account, laying the groundwork for the social-data crawling features planned in later days.

## Who Uses It

- **Team member / analyst** — signs in with their Facebook or Twitter account to access the dashboard; experiences a one-click login flow with no registration step.
- **Application administrator** — connects their social accounts so the platform can later use the stored access tokens to crawl posts and metrics on their behalf. Administrator role is provisioned separately, not granted automatically through social login.

## What They Do

1. A team member visits the dashboard URL and is immediately directed to the login page — they see two buttons, one for Facebook and one for Twitter/X.
2. The team member clicks the button for their preferred social account and is taken to that platform's authorization screen, where they approve access.
3. The platform receives confirmation from the social network, creates or updates the team member's account in the system, and records their access token for future use.
4. The team member is redirected to the dashboard home page and can immediately use the application.
5. When the team member is finished, they log out — their session ends and they are returned to the login page with a confirmation message.
6. If the authorization is denied or an error occurs at the social network, the team member is returned to the login page with a clear message asking them to try again.

## Unresolved Questions

- **Role assignment intent:** The system assigns every social-login user the standard member role. Is there a business process for promoting specific users to administrator? If so, who performs that promotion and through what channel (direct database update, a future admin panel)?
- **Account linking:** If a team member signs in with Facebook first, then later with Twitter, should both accounts link to the same user profile? The current design treats them as independent identities keyed by email. If the same email is returned by both providers, they will share one user record — is this the intended behavior?
