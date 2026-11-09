# Security Policy

## Reporting a vulnerability

Report vulnerabilities to [security@smallpict.app](mailto:security@smallpict.app). You get an acknowledgement within 3 business days and a fix or a mitigation plan within 30 days for confirmed issues. Please do not open a public issue for a vulnerability.

## Supported versions

Only the latest minor release on Maven Central receives security fixes. Upgrade before reporting if you are behind.

## What the SDK guarantees

- Every API request is signed with HMAC-SHA256 using your secret key; the secret never leaves the process in any header, log or exception message.
- The upload PUT to the pre-signed storage URL carries only `Content-Type` and `Content-Length`: no API key, signature or idempotency header.
- `upgrade_url` and `manage_url` from API responses are exposed as plain strings and never opened, fetched or followed.
- Redirects are not followed with signed headers; the client never fetches a URL you did not configure as `baseUrl`.
- Exception messages mask API keys (`sp_*`) and secrets (`sps_*`, `sec_*`). Do not log raw API keys, signatures or image payloads yourself.
