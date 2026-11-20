# Free Internet Connect

A small Android app that tests a short list of public HTTP proxies using OkHttp.

## Important safety notice

Public proxies are untrusted intermediaries. They may log, inspect, redirect, or modify traffic, and they may be operated without permission by third parties. Do not use this app for banking, passwords, personal data, or any sensitive service. The app only configures a proxy on its own connectivity test; it does **not** create a device-wide VPN or promise free/unmetered internet access. The bundled addresses are examples and may be offline or reassigned.

## Build

The included GitHub Actions workflow builds a debug APK on every push and publishes a release APK when a `v*` tag is pushed.
