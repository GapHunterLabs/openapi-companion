# Privacy Policy — OpenAPI Companion

**Effective date:** 2026-10-06

OpenAPI Companion is a Gap Hunter Labs plugin for IntelliJ Platform IDEs.
This policy is short because the plugin's design makes it short: there
is nothing to disclose beyond what's below.

## What this plugin collects

**Nothing.** OpenAPI Companion does not collect, transmit, or sell
any data — no source code, no file contents, no usage analytics, no
telemetry, no crash reports, no personally identifiable information.

## What it keeps on your machine

To decide when to show its one-time rating prompt, the plugin keeps two values
in the IDE's own settings on your computer: whether you have answered the
prompt, and a list of up to 500 findings it has already counted. Until the
next release, each entry in that list is the file path and line of a finding,
sometimes with its message. From the next release on, each entry is a one-way
fingerprint that cannot be turned back into a path, and the old list is
deleted. None of this is ever sent anywhere.

## Network access

**None.** OpenAPI Companion makes zero network calls during normal
operation. Every `$ref` resolution it performs runs entirely in-process,
inside your IDE, against local files already on disk. Nothing you write,
open, or edit is ever sent anywhere — an `http(s)://` `$ref` is shown as
unresolved rather than fetched over the network.

The one narrow exception: this plugin is 100% Paid (no free tier), so
the IDE's built-in `LicensingFacade` validates a purchased license
locally against JetBrains' own licensing infrastructure — the same
mechanism every commercial JetBrains-ecosystem plugin uses, entirely
separate from and unrelated to the plugin's actual `$ref`-resolution
logic, which still never leaves your machine.

## Third parties

None. OpenAPI Companion has no third-party SDKs, no analytics libraries,
no ad networks, no external dependencies that phone home.

## Changes to this policy

If this ever changes, this file will be updated and the change will be
noted in the plugin's `CHANGELOG.md`.

## Contact

Questions about this policy: **gaphunterlabs@gmail.com**
