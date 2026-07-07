# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FEI5 (File Exchange Interface version 5) is a NASA JPL / AMMOS file distribution system for exchanging scientific data files between ground system components. This repo (`fei-main`) is the core/shared library component of a three-repo ecosystem:
- `fei-main` (this repo) — FEI5 core libraries and shared utilities
- `fei-server` — FEI5 server deployment and tooling
- `fei-client` — FEI5 client service and tooling

## Prerequisites

- OpenJDK 1.8+ (Java 17 targeted in CI)
- Maven 3.6.3+
- `~/.m2/settings.xml` configured with CAE Artifactory credentials
- `parent-mipl` POM installed locally (`mvn -U clean install` in the parent-mipl repo)

## Build Commands

```bash
# Build all modules (tests skipped by default)
mvn -U clean install

# Build a single module and its dependencies
mvn -pl mdms-komodo-lib -am clean install

# Build with tests enabled
mvn -Dmaven.test.skip=false install

# Build with Javadoc JARs (required for deploy)
mvn javadoc:jar install

# Deploy to Artifactory (CI only)
mvn javadoc:jar deploy

# Tag a release (master branch only)
mvn scm:tag
```

There are no unit test source files in this repository (`src/test/java` does not exist). Integration/behavioral tests live in `fei-server` and `fei-client`.

## Branch and Release Model

- Feature branches merge directly to `master` — there is no `develop` branch
- `master` → production release (e.g., `3.6.0`); Jenkins builds, tags, and deploys on every merge
- Feature branches build as SNAPSHOT (e.g., `3.6.0b42-abc1234-SNAPSHOT`) for CI validation only
- `<semver>` in the root `pom.xml` **must** be bumped on every feature branch (current: `3.6.2`) — the CI `Version Check` stage blocks merging if `<semver>` matches `master`
- Commit messages must reference a ticket: `Issue #248 - Description`
- Sign-off required on all commits: `git commit -s` (DCO)

## Code Style

Checkstyle (Sun Java conventions) is enforced during the Maven build. Configuration: `etc/checkstyle/checkstyle_checks.xml`. Key rules:
- Javadoc required on all public methods and types
- No star imports
- `UPPER_CASE` constants, `camelCase` methods/variables
- Max file length: 5000 lines; max method length: 500 lines; max parameters: 15

## Module Architecture

### `mdms` — Foundation Infrastructure

Low-level library with no external dependencies except Log4j. Provides:
- **Concurrency primitives** (`jpl.mipl.mdms.concurrent`): custom `Semaphore`, `Mutex`, `Queue`, `BoundedQueue`, `MessageQueue`, `Task/TaskThread`, `Producer/Consumer`
- **Reactor-pattern networking** (`jpl.mipl.mdms.connection`): `Reactor`, `SocketAcceptor`, `SocketConnector`, `SocketStream`, `ServiceHandler/Factory`
- **Utilities** (`jpl.mipl.mdms.utils`): `MDMS` base logger, `CipherUtil`, `GetOpt/GetOptLong`, `FileMaker`
- **Pluggable logging** (`jpl.mipl.mdms.utils.logging`): `Logger`, `LoggerPlugin`, `Log4JPlugin`

### `mdms-komodo-lib` — Client API Library

The main user-facing library; depends on `mdms`. Key areas:

- **Core API** (`komodo.api`): `Session` manages server connections; `Client` is the simplified user wrapper; `FileType` handles per-type file operations; `Domain`/`SaxDomain` parses the domain topology file; `VFT` handles Virtual File Table operations; `ServerGroup`/`ServerProxy` represent server topology
- **Session utilities** (`komodo.util`): `Configuration`, `UserAuthenticator`, `AuthenticationType`, `PublicKeyEncrypter`, `PushFileEventQueue`, `ReconnectThrottle`, `UserToken`
- **SSL/TLS networking** (`FileService.net`): `SecureSocketsUtil` implements TLSv1.2 with FIPS 140-2/140-3 support via Bouncy Castle; `SocketUtil`/`ServerSocketUtil` for lower-level socket management
- **File I/O** (`FileService.io`): `FileIO`, `BufferedStreamIO`, `MessagePkg` for message framing
- **FileService utilities** (`FileService.util`): `FileSystem`/`FileUtil`/`DirectoryUtil`, `DateTimeUtil`, `PasswordUtil`, `WildcardRegexUtil`, `Interpreter`/`InterpreterDriver` for command parsing, `NativeUtil`/`SystemProcess`
- **Query Service** (`komodo.services.query.api`): SOAP/JAX-WS client API for querying file metadata

**Generated code**: JAX-WS stubs are generated from `query-service.wsdl` at `target/generated-sources/wsdl` during the `generate-sources` Maven phase (package `jpl.mipl.mdms.FileService.komodo.services.query.server`).

### `mdms-shared-lib` — Packaging Module

POM-only module that bundles `etc/` as a `tar.gz` archive (`mdms-shared-lib-<version>-etc.tar.gz`) consumed by `fei-server` and `fei-client` deployment repos.

## Runtime Architecture

```
Domain File (etc/config/domain.fei)
  → parsed by SaxDomain/DomXmlParser
  → describes server groups, file types, hosts/ports, auth/comm mode

Session
  → opens SSL/TLS connections (TLSv1.2) to servers via SecureSocketsUtil
  → authenticates via SHA1 / Kerberos / token (AuthenticationType)
  → manages FileType handles

FileType / Client (user-facing operations)
  → add, get, delete, list, subscribe (push/pull), replace, rename, VFT
  → file metadata persisted in MySQL via c3p0 connection pool
  → configured via KomodoDB_Pool.properties / KomodoDB_CPDS.properties

Query Service
  → SOAP web service (query-service.wsdl)
  → enables file metadata queries

Significant Events
  → logged to MySQL SigEventsDB (security, user, VFT, alert, server-state events)
```

## Key Configuration Files

| File | Purpose |
|------|---------|
| `etc/config/domain.fei` | Server topology: server groups, hosts/ports, file types, auth mode |
| `etc/config/komodo.config` | Server runtime config: DB statement files, sig-event toggles |
| `etc/config/KomodoDB_Pool.properties` | c3p0 pool wrapper; specifies MySQL JDBC driver |
| `etc/config/KomodoDB_CPDS.properties` | c3p0 datasource: MySQL host/port/db/user/pass, pool sizing |
| `etc/config/SigEventsDB.properties` | Significant events DB config |
| `etc/checkstyle/checkstyle_checks.xml` | Checkstyle rules |

## Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Log4j 2 + SLF4J | 2.24.3 | Logging |
| Bouncy Castle FIPS | `bc-fips:2.1.2`, `bcpkix-fips:2.0.8`, `bcutil-fips:2.0.3` | FIPS 140-2/140-3 crypto/TLS |
| Jakarta JAX-WS | 4.0.3 | SOAP web service (Query Service) |
| Jakarta JAXB | 4.0 | XML binding |
| MySQL Connector/J | 8.0.33 | Database driver |
| c3p0 | 0.8.5.2 | JDBC connection pooling |
| JGroups | 2.6.22 | Server group clustering coordination |
| dom4j + jaxen | 1.6 / 1.1 | XML parsing |

## Java 17 Compatibility Notes

See `docs/JAVA17_MIGRATION_GUIDE.md` for full details. Key points:
- `komodo.policy` needs `getStackWalkerWithClassReference` runtime permission for Log4j 2 under Java 17
- MySQL JDBC driver class changed from `com.mysql.jdbc.Driver` to `com.mysql.cj.jdbc.Driver`; JDBC URL must include `?serverTimezone=UTC` (or equivalent)

## CI Pipeline

Jenkins (`Jenkinsfile-buildDeploy` in `.ci/`). Relevant behavior for contributors:
- **Version Check** stage runs on all non-`master` branches and fails if `<semver>` in `pom.xml` is unchanged from `master` — always bump `<semver>` on a feature branch
- CI runs `mvn javadoc:jar install -Dmaven.test.skip=false` (tests enabled, Javadoc built)
- Docker build image: `maven:3.8-openjdk-17-slim` with JPL Entrust root certs pre-installed
