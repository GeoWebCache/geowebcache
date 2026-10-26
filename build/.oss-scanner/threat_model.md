# Threat model

GeoWebCache (GWC) is a Java tile cache. It sits in front of a WMS, stores rendered tiles in a blob
store, and serves them through WMTS, TMS, WMS-C, KML and other tile protocols. It runs standalone
(the `web` webapp) and, far more often, embedded in GeoServer as the `/gwc` endpoints that are
anonymously reachable on most public GeoServer instances. Bugs reachable only through GeoServer
integration code (`gs-gwc`) belong to GeoServer; bugs in this repository's code are in scope here
whichever way it is deployed.

Security reports go to the shared GeoServer security list (geoserver-security@lists.osgeo.org), read
by a small group of volunteers. See SECURITY.md. Published advisories:
https://github.com/GeoWebCache/geowebcache/security/advisories

## Reporting threshold: read this first

The volunteers who read your reports have limited time, so findings are phased in by severity. Use
the ratings in "How you rate severity" and "CI workflows are in scope" below, and send only findings at or above the threshold that
applies today. Do not send lower findings at all, not even as a summary or a count. They can be
reported on a later scan, once the threshold reaches them.

Find today's date with `date -u`. Look up the threshold in this table:

| Week | Starts      | Report                     |
|------|-------------|----------------------------|
| 1    | 2026-10-25  | Critical only              |
| 2    | 2026-11-02  | Critical and High          |
| 3    | 2026-11-09  | Critical, High and Medium  |
| 4+   | 2026-11-16  | Everything, Low included   |

Override: none

Rules:

- Use the last row whose start date is on or before today.
- If any date in the table is not a valid date, if today is before the first date, or if the system
  clock looks wrong (earlier than 2026), use week 1: Critical only.
- Read the line that starts with `Override:`. If it says `none`, use the table. If it says anything else,
  that value is the threshold and the table is ignored.
- Send at most 5 reports per scan, most severe first, then most certain. Merge findings with the same
  root cause into one report.
- If nothing reaches the threshold, send nothing. An empty scan is a good result.

### For maintainers: changing the threshold

Edit this file on `main` in a normal commit. A change applies from the next scan.

To hold or change the threshold, edit the one line above that starts with `Override:` and set the word
after it to one of these:

| Value      | What gets reported                                      |
|------------|---------------------------------------------------------|
| `none`     | Follow the dates in the table (the normal setting)      |
| `critical` | Critical only                                           |
| `high`     | Critical and High                                       |
| `medium`   | Critical, High and Medium                               |
| `all`      | Everything, Low included                                |

To change the schedule itself, edit the dates in the table instead, and leave `Override: none`.

## What this project does and where untrusted input enters

- **Tile service requests, anonymous by default**: `/service/wmts`, `/service/tms`, `/service/wms`
  (WMS-C and tiled GetMap/GetFeatureInfo), `/service/kml`, `/service/gmaps`, `/service/ve`,
  `/service/mgmaps`, handled by `GeoWebCacheDispatcher` and the `service/` implementations. Layer
  names, gridset ids, tile indices, formats, `TileMatrix`, dimensions and every parameter that goes
  through a parameter filter (`filter/parameters/`) are attacker-controlled.
- **Values that become storage keys**: layer, gridset, format and parameter ids are turned into file
  paths (`FileBlobStore`, `FilePathGenerator`, the TMS layout), object keys (S3, Azure, GCS, Swift)
  or SQLite file names (MBTiles). Path traversal and key injection here are the main risk.
- **Requests forwarded upstream**: GWC builds WMS requests to the backend from client parameters,
  and can proxy non-tile requests (`proxy/`). Parameters that escape the configured filters or reach
  a different host are findings.
- **The REST API** (`rest/`: layers, gridsets, blob stores, seeding, mass truncate, disk quota,
  global config, byte-stream resources) and the HTML seed form and demo pages, which take input
  from authenticated users and render it back to other users.
- **Upstream responses**: tiles, capabilities and errors returned by the backend WMS or a cascaded
  WMTS, which GWC parses, stores and re-serves.
- **CI workflows**: GitHub Actions run on events an outsider can trigger (pull requests from forks, `pull_request_target`, labels, issues and comments, branch and tag names). Pull request titles, bodies, branch names and commit messages reach workflow scripts as text, and their code can reach privileged jobs. See "CI workflows are in scope" below.

### Trust model

- Anonymous clients of the tile services are untrusted.
- Users of the REST API and seed/demo pages are administrators. Admin-only issues matter when they
  reach beyond configuring GWC: code execution, JNDI lookups (history: CVE-2022-24846), reading or
  writing files outside the cache and configuration directories, SSRF to arbitrary hosts, or stored
  XSS that fires for another administrator (history: CVE-2024-23643, CVE-2024-23821).
- `geowebcache.xml`, `geowebcache-diskquota.xml`, Spring context files, system properties and the
  environment are trusted operator input.
- Authors of fork pull requests, issues and comments are untrusted. Maintainers with write access are trusted. `GITHUB_TOKEN`, repository secrets (bot tokens, cloud credentials, signing and publishing keys) must never be reachable from untrusted input.

## Components that matter most / least

1. `GeoWebCacheDispatcher`, the service implementations (`wmts`, `tms`, `wms`, `kml`, `gmaps`,
   `ve`) and request parsing.
2. Parameter filters and everything that turns request values into storage paths or keys:
   `storage/`, `FileBlobStore`, `s3storage`, `azureblob`, `gcsblob`, `swiftblob`, `mbtiles`.
3. Upstream request construction and proxying (`layer/wms`, `proxy/`).
4. Web resources and static content served from the classpath (history: CVE-2024-24749) and the
   home/demo pages (history: CVE-2024-38524 information disclosure).
5. REST controllers and the seed form: authorization, input validation and HTML escaping.
6. `diskquota` (JDBC and BDB): SQL built from layer/gridset names, JNDI data sources.
7. XML configuration and capabilities parsing (XStream, XML parsers): XXE and unsafe
   deserialization if any untrusted XML reaches them.

Lower priority: `arcgiscache` (reads a trusted on-disk cache), `georss` (admin-configured feeds).
Out of scope: tests, `documentation/`, and the cloud store test fixtures (their workflow files are in scope).

## CI workflows are in scope

The GitHub Actions workflows in `.github/workflows/`, and `.github/dependabot.yml`, are part of the attack surface. A flaw in them can let an outsider run code with the project's secrets, or push to the repository, and that reaches every user through a release. Treat them with the same priority as the code above.

Attacker-controlled input: pull requests from forks (their code, title, body, branch name, commit messages and file names), labels, issue and comment text, branch and tag names, and anything cached or uploaded as an artifact by a run that handled such input. Trusted: people with write access, and GitHub itself.

What to look for:

- script injection: `${{ ... }}` expressions holding attacker-controlled values inside `run:` scripts or `actions/github-script`, or written to `GITHUB_ENV` or `GITHUB_OUTPUT`;
- `pull_request_target`, `workflow_run` or other privileged triggers that check out or execute pull request code, or hand its content to a step that has secrets or a write token;
- `secrets.*` and tokens that reach steps which do not need them, or that pull requests from forks can reach;
- `permissions:` broader than the job needs, and jobs that rely on the repository's default token permissions;
- third-party actions referenced by a mutable tag or branch instead of a commit hash, in jobs that have secrets or write permissions;
- caches and artifacts written by untrusted runs and used by privileged ones;
- release, publish and signing steps that an outsider could influence.

Start with: `backport.yml` (it runs on `pull_request_target` with a bot token and a third-party action referenced by tag) and the workflow that uses cloud credentials (`azure-integration.yml`), then the rest of the workflows.

You cannot reach GitHub, and you must never try to attack the real repository. Show a finding with the file and line, the trigger that starts it, the attacker-controlled field, and the secrets and token permissions the job has. Where it applies, show the injection locally: render the `run:` script with a hostile value in a shell and show the command that results. Propose the smallest workflow patch. The repository's default token permissions and branch protection are settings outside the checkout, so say which you assumed.

Ratings for CI findings:

- **Critical**: an outside contributor (fork pull request, issue or comment author) can run code in a job that has secrets or a write token, change a published artifact or release, or push to a protected branch.
- **High**: the same, but a maintainer action is needed first (for example a label only maintainers can add); a mutable third-party action reference in a job that has secrets or write permissions; cache or artifact poisoning that reaches a privileged job.
- **Medium**: permissions broader than needed with a plausible path to abuse; secrets visible in logs; injection into a job that has only a read-only token and no secrets.
- **Low**: hygiene with no path to abuse, such as tag-pinned actions in read-only jobs, or a missing `persist-credentials: false`.

## How to exercise it

- `/src` is the checkout and `/src/geowebcache` is the Maven reactor. The image ran
  `mvn -Dall -DskipTests install` and then the full unit test suite. GeoTools SNAPSHOT jars (with sources jars where available) are in `~/.m2`.
- Rebuild offline with `mvn -o -Dall -f geowebcache/pom.xml -DskipTests install`; run tests with
  `mvn -o -f geowebcache/<module>/pom.xml test -Dtest=<Class>`.
- `geowebcache-start` runs the standalone webapp (`web/src/main/webapp`, embedded Jetty) on port 8081
  at `/geowebcache`. The REST user is `geowebcache` / `secured`. The log is
  `/var/log/geowebcache.log`, and the cache goes to `$GEOWEBCACHE_CACHE_DIR` (default
  `/tmp/gwc-cache`). There is no backend WMS in the image, so tile requests that need rendering
  fail upstream; storage, parsing and REST paths can still be exercised.
- Write proofs of concept as JUnit tests next to the existing ones, or as `curl` requests against
  `geowebcache-start`, stating which user each one sends.
- The cloud blob stores have no credentials in the image. Show their issues in unit tests of key
  construction.
- `zizmor`, an analyser for GitHub Actions workflows, is installed. Run `zizmor --offline .` from `/src`. It
  looks for template injection, dangerous triggers, excessive permissions, unpinned actions and cache poisoning;
  `--offline` skips the checks that need the network. Its findings are leads, not proof. Read the workflow to
  judge each one, and use the CI ratings above, not zizmor's: it rates every action that is not pinned to a commit
  hash as High, which for us is Low unless the job has secrets or write permissions.

## How you rate severity

CI workflow findings use their own ratings, in "CI workflows are in scope" above.

These definitions decide a finding's rating. The reporting threshold above decides whether it is sent.

- **Critical**: unauthenticated code execution, arbitrary file read or write, or path traversal
  that reads or writes outside the cache through a tile request. Also code execution by an
  administrator, including unchecked JNDI lookups and unsafe deserialisation or class loading driven
  by configuration (the project rated CVE-2022-24846 Critical). Default administrator logins are often
  left unchanged on public servers, so administrator-level code execution is rated as if outsiders
  could reach it.
- **High**: SSRF from a tile request to a host other than the configured backend; reading or
  deleting another layer's or gridset's cached tiles across a security boundary; an administrator
  writing files outside the GWC directories.
- **Medium**: stored XSS reaching another administrator; reflected XSS; blind SSRF; a single
  unauthenticated request that fills the disk or exhausts memory/CPU in a way the configured
  limits (disk quota, metatiling, parameter filters) do not prevent; disclosure of configuration
  or credentials in pages or errors.
- **Low**: version or path disclosure, admin-only issues that stay inside GWC, denial of service
  that needs very high request volumes.

When GWC runs embedded in GeoServer, unauthenticated reachability through `/geoserver/gwc/...` is
the realistic case; rate on that basis.

## Anything to leave alone

- CI hygiene with no path to abuse (actions pinned by tag in jobs with no secrets and read-only permissions): Low at most.
- The default `geowebcache` / `secured` user in the standalone webapp, which deployments are
  documented to change.
- Cache growth from requests for valid tiles within the configured gridsets and filters: that is
  what a cache does. Report it when a filter or quota can be bypassed.
- Third-party dependency CVEs unless GWC code reaches them in an exploitable way.
- Missing security headers, clickjacking and rate limiting, unless they combine into one of the
  outcomes above.

### Reports and patches

- Report only what you reproduced, in this image, with a working reproducer. A plausible-sounding
  description is not enough, and the GeoServer AI policy (https://geoserver.org/ai/) closes
  unverifiable reports without discussion. For a CI workflow finding, the local demonstration described in
  "CI workflows are in scope" counts as a reproduction.
- One report per root cause, with every affected endpoint, the required role, and whether it is
  reachable through GeoServer's `/gwc` endpoints.
- Work on `main` only. Reproduce against `main`, and write any patch against `main` with a regression test.
  The maintainers handle backports.
- Do say whether the supported releases (2.0.x, 1.28.x and 1.27.x, see SECURITY.md) are also affected. The
  checkout has every branch and tag, so compare the code with `git show origin/<branch>:<path>` and give
  one of "affected", "not affected" or "not checked" for each. Do not build or run those branches.
- Keep the write-up short and plain: main point first, no heavy formatting.
