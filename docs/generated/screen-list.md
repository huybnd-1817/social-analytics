# Screen List

## SCR001_Login

**Feature:** F004 — OAuth2 Social Login
**Route:** /login
**Description:** Login page presenting Facebook and Twitter/X social login buttons; displays error and logout confirmation message bands.
**States:** default, error (OAuth2 failure or missing email), logout-success

## SCR002_Dashboard

**Feature:** F005 — Dashboard
**Route:** /
**Description:** Post-login dashboard — shows user name, email (N/A for Twitter), provider badge, and logout button.
**States:** loaded
