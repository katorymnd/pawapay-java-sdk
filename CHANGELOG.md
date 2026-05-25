# Changelog

All notable changes to the PawaPay Java SDK will be documented in this file.

## [2.2.0] - 2026-05-19

### 🛡️ Security & Integrity
- **Enhanced Native Binding:** Strengthened hardware-bound license validation via native core integration.
- **Integrity Verification:** Added runtime bytecode integrity checks to detect unauthorized modifications.
- **Tamper Resistance:** Implemented progressive degradation protocols for environments detecting invalid license states.
- **Session Persistence:** Secured session state with cross-platform cryptographic signatures to prevent replay attacks.

### 🚀 Features
- **Dual-Stack API Support:** Full support for both V1 and V2 PawaPay API endpoints with automatic version routing.
- **Dynamic Configuration:** SDK version now dynamically injected from build artifacts for consistent telemetry.
- **Improved Error Handling:** More descriptive error messages for license validation failures and network timeouts.

### 🐛 Bug Fixes
- Fixed an issue where SSL context initialization would fail on older JVM implementations.
- Resolved a race condition in the asynchronous license activation flow.
- Corrected metadata serialization for complex nested objects in V2 payloads.

### 📦 Dependencies
- Updated `jackson-databind` to `2.17.0` to address CVE-2024-xxxxx.
- Forced `jetty-server` to `11.0.25` to mitigate known HTTP/2 vulnerabilities.
- Updated `logback-classic` to `1.4.14`.

---

## [2.1.5] - 2026-04-10

### 🛡️ Security
- **License Validation:** Improved server-side handshake protocol to prevent MITM interception.
- **Obfuscation:** Enhanced internal class naming conventions to reduce surface area for static analysis.

### 🐛 Bug Fixes
- Fixed a memory leak in the `ProtectionManager` decay monitor thread.
- Corrected timestamp formatting in audit logs for UTC consistency.

---

## [2.1.0] - 2026-03-01

### 🚀 Features
- **Payment Pages:** Added support for V2 Payment Page sessions with dynamic amount details.
- **Refunds:** Introduced automated refund initiation with V1/V2 compatibility layer.

### 🛡️ Security
- **Native Core:** Migrated critical license checks to native Rust library for improved tamper resistance.
- **Environment Locking:** Added strict domain binding to prevent license sharing across multiple servers.

---

## [2.0.0] - 2026-01-15

### ⚠️ Breaking Changes
- **License Model:** Transitioned to hardware-bound licensing. `PAWAPAY_SDK_LICENSE_SECRET` environment variable is now required.
- **API Client:** Removed deprecated synchronous methods; all API calls are now `CompletableFuture` based.

### 🚀 Features
- **Async Architecture:** Complete rewrite of `ApiClient` to leverage Java 17 virtual threads and async HTTP client.
- **Native Integration:** Introduced `NativeCore` bridge for high-performance cryptographic operations.

### 🛡️ Security
- **Initial Protection Layer:** Implemented base `ProtectionManager` for violation tracking and SDK self-defense.
- **Bytecode Verification:** Added initial checksum validation for critical SDK classes.

---

## [1.9.2] - 2025-11-20

### 🐛 Bug Fixes
- Fixed JSON parsing error for empty metadata arrays in V1 deposits.
- Resolved connection timeout issues in high-latency networks.

### 📦 Dependencies
- Updated `httpclient` to `4.5.14`.

---

## [1.8.0] - 2025-09-05

### 🚀 Features
- Added support for Remittance status checks.
- Introduced `checkMnoAvailability` endpoint for real-time operator status.

### 🛡️ Security
- Added basic license key format validation.

---

## [1.0.0] - 2025-06-01

### 🎉 Initial Release
- Basic V1 API support for Deposits and Payouts.
- Standard Java HTTP client implementation.
- Simple license key validation.

***