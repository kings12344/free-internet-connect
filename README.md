# Free Internet Connect

Version 2 replaces the stale hard-coded public proxy list with a durable diagnostic flow:

- Tests direct HTTPS when no proxy is entered.
- Accepts a proxy host, port, and optional Basic-auth credentials supplied by the user.
- Uses HTTPS `CONNECT` through the configured HTTP proxy.
- Tries multiple HTTPS endpoints, retries with backoff, and reports the actual exception or HTTP response.
- Saves the non-secret proxy host, port, and username locally; the password is not persisted.
- Never claims “connected” unless an HTTPS request succeeds.
- Can fetch a recent, filtered HTTP proxy candidate list from ProxyScrape, then test at most 25 candidates sequentially through HTTPS.

## Important limitation

No Android app can make dead public IPs reliable or create free/unmetered internet without a functioning upstream connection. For dependable service, use a proxy or VPN server that you own or are authorized to use. Public proxies are untrusted intermediaries and may log, inspect, redirect, or modify traffic. Do not use an unknown proxy for banking, passwords, private messages, or sensitive data.

This app is a connectivity tester and per-request proxy demonstration; it does **not** create a device-wide VPN. A real VPN product requires a `VpnService` implementation plus a properly configured VPN gateway/server.

The candidate refresh uses ProxyScrape's documented public endpoint with `ssl=yes`, `anonymity=elite`, a 5-second source timeout, and a bounded result set. Candidate lists are not guarantees: a proxy can disappear between publication and testing. See the [ProxyScrape API documentation](https://docs.proxyscrape.com/api-reference/public-api/get-proxy-list).

## Build

The GitHub Actions workflow builds a debug APK on every push and publishes a release APK when a `v*` tag is pushed.
