# Security Policy

## Supported Versions

diamondTracker is currently in **public beta**. Security updates are applied to the latest version only.

| Version | Supported          |
|---------|--------------------|
| latest (beta) | ✅ Yes        |
| older builds  | ❌ No         |

## Supported Platforms

- Android 8.0 (API 26) and above
- Tested on stock Android and common manufacturer UIs (Samsung One UI, etc.)

## Scope

diamondTracker is a local-first Android app for baseball coaches. It stores all data on-device using SQLite (Room Database). There is currently:

- **No network communication** – no APIs, no cloud sync, no analytics
- **No user authentication** – the app is designed for single-device, single-user use
- **No sensitive personal data** – only team names, player names, and game statistics

Given this scope, the attack surface is limited. Relevant security concerns include:

- SQL injection or data corruption via malformed input
- Insecure local data storage or unprotected database access
- Exported Activities or Content Providers exposing data to other apps
- Issues in future versions if networking or accounts are added

## Reporting a Vulnerability

We use **GitHub Private Vulnerability Reporting** to handle security disclosures responsibly.

**Please do not open a public GitHub Issue for security vulnerabilities.**

Instead:

1. Go to the [Security tab](../../security) of this repository
2. Click **"Report a vulnerability"**
3. Fill out the form with as much detail as possible

This is an open-source project maintained in our spare time. We will do our **best effort** to acknowledge your report as soon as possible and work toward a resolution, but we cannot guarantee fixed response times.

### What to include in your report

- A clear description of the vulnerability
- Steps to reproduce
- Potential impact
- Android version and device model (if relevant)
- Any suggested fix (optional but appreciated)

## Disclosure Policy

We follow **coordinated disclosure**:

- We ask that you give us reasonable time to address the issue before any public disclosure
- Once a fix is released, we are happy to credit you in the release notes (if you wish)
- We do not currently offer a bug bounty program

## Out of Scope

The following are **not** considered security vulnerabilities for this project:

- Issues on unsupported Android versions (below API 26)
- Attacks requiring physical access to an already unlocked device
- Issues in third-party libraries (please report those upstream)
- Theoretical vulnerabilities without a practical exploit

## Contact

For non-security issues (bugs, feature requests), please use [GitHub Issues](../../issues).

---

*This policy may be updated as the project evolves toward a stable release.*
