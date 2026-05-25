# PawaPay Java SDK

[![Maven Central](https://img.shields.io/badge/Maven%20Central-2.4.6-blue.svg)](https://central.sonatype.com/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.java.net/)

The official PawaPay Java SDK delivers secure and scalable integration with the PawaPay payment ecosystem for Java applications. The SDK supports both V1 and V2 APIs, enabling deposits, payouts, refunds, payment page sessions, transaction status tracking, MNO availability checks, and active configuration retrieval.

Built with a native Rust-powered security core, the SDK focuses on performance, integrity validation, and enterprise-grade payment reliability.

---

# ⚠️ Security Notice

This SDK includes advanced protection mechanisms designed to secure transaction flows and preserve platform integrity.

- Hardware-bound licensing validation
- Runtime integrity verification
- Native cryptographic operations
- Progressive anti-tampering protection

Do not attempt to decompile, patch, modify, or bypass SDK verification layers.

Detected tampering may result in:

- Increased API latency
- Intermittent request failures
- Endpoint restrictions
- Permanent license revocation

Ensure your deployment environment satisfies the system requirements to avoid false integrity warnings.

---

# Table of Contents

- [Installation](#installation)
- [Configuration](#configuration)
- [System Requirements](#system-requirements)
- [Usage](#usage)
  - [Initialization](#initialization)
  - [Deposits](#deposits)
  - [Payouts](#payouts)
  - [Refunds](#refunds)
  - [Payment Page Sessions](#payment-page-sessions)
  - [Transaction Status](#transaction-status)
  - [MNO Availability & Active Configuration](#mno-availability--active-configuration)
- [API Version Compatibility](#api-version-compatibility)
- [Security & Integrity](#security--integrity)
- [Error Handling](#error-handling)
- [Live Sandbox Testing](#live-sandbox-testing)
- [Support](#support)

---
# Installation

## Maven

Add the dependency below to your `pom.xml`.

```xml
<dependency>
    <groupId>com.katorymnd</groupId>
    <artifactId>pawapay-java-sdk</artifactId>
    <version>2.6.6</version>
</dependency>
```

---

## Gradle

```groovy
implementation 'com.katorymnd:pawapay-java-sdk:2.6.6'
```

---

# Initial SDK Bootstrap Setup

After downloading or cloning the SDK project, you must complete the initial bootstrap process before making API calls.

The setup process prepares:

- VM bytecode resources
- Runtime session cache
- Hardware imprint validation
- Local integrity metadata

---

## Step 1, Create a `.env` File

Create a `.env` file in the root project directory.

Example required variables:

```env
PAWAPAY_SDK_LICENSE_DOMAIN=your-domain.com
PAWAPAY_SDK_LICENSE_SECRET=your-license-secret
```

> These values are required for SDK runtime validation and local bootstrap generation.

---

## Step 2, Create a `pom.xml` File

Create or verify your Maven `pom.xml` configuration before running the setup process.

Ensure the SDK dependency and Maven execution plugins are correctly configured.

---

## Step 3, Run the SDK Bootstrap Command

Execute the setup command below from the root project directory.

```bash
mvn clean compile exec:java -Dexec.mainClass="com.katorymnd.pawapay.sdk.utils.vm.SetupSDK"
```

---

## What the Bootstrap Process Generates

The setup process automatically prepares the SDK runtime environment and generates:

| File | Purpose |
|---|---|
| `.pawapay-imprint` | Machine fingerprint validation |
| `.pawapay-session` | Runtime validation session cache |
| `opcodes.json` | VM opcode configuration |
| `bytecode.bin` | Encoded runtime bytecode |

---

## Successful Setup Output

After a successful bootstrap, the SDK environment becomes ready for:

- Deposits
- Payouts
- Refunds
- Payment page sessions
- Runtime integrity validation
- Native VM execution

You can then proceed to run your integration examples or production workflows.

---

## Important Notes

- Always run the setup command before executing SDK examples.
- Do not manually modify generated VM or session files.
- Generated files are part of the SDK integrity protection flow.
- Missing bootstrap artifacts may trigger runtime validation failures.
- Re-run setup when migrating environments or rebuilding clean project states.

---

# Configuration

The SDK requires both:

- A valid PawaPay API token
- A valid Katorymnd SDK license key

Credentials are typically loaded using environment variables or deployment secrets.

---

## Required Environment Variables

| Variable | Description |
|---|---|
| `PAWAPAY_SANDBOX_API_TOKEN` | PawaPay sandbox API token |
| `KATORYMND_PAWAPAY_SDK_LICENSE_KEY` | SDK license key issued by Katorymnd |

---

# System Requirements

| Requirement | Value |
|---|---|
| Java | 17+ |
| Operating System | Linux, Windows, macOS |
| Architecture | 64-bit |
| Network Access | HTTPS access to `api.pawapay.io` |

> The SDK ships with native `.dll`, `.so`, and `.dylib` libraries for secure cryptographic execution.

---

# Usage

# Initialization

Initialize the SDK using the `Config.Builder`.

```java
import com.katorymnd.pawapay.sdk.api.ApiClient;
import com.katorymnd.pawapay.sdk.config.Config;
import com.katorymnd.pawapay.sdk.utils.Helpers;

public class PawaPayExample {

    public static void main(String[] args) {

        /**
         * Load credentials.
         */
        String apiToken = System.getProperty("PAWAPAY_SANDBOX_API_TOKEN");
        String licenseKey = System.getProperty("KATORYMND_PAWAPAY_SDK_LICENSE_KEY");

        /**
         * Build SDK configuration.
         */
        Config config = new Config.Builder()
                .apiKey(apiToken)
                .environment("sandbox") // or production
                .timeout(30000)
                .build();

        /**
         * Initialize API client.
         */
        ApiClient client = new ApiClient(
                config,
                licenseKey,
                true,
                "v2"// or V1
        );

        /**
         * Generate unique transaction ID.
         */
        String transactionId = Helpers.generateUniqueId();

        System.out.println("Generated Transaction ID: " + transactionId);

        /**
         * Close client when finished.
         */
        // client.close();
    }
}
```

---

# Deposits

## V2 Deposit (Recommended)

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepare metadata.
 */
List<Object> metaData = new ArrayList<>();

metaData.add(createMeta("orderId", "ORD-123456"));
metaData.add(createMeta("customerName", "John Doe"));
metaData.add(createMeta("source", "Mobile App"));

/**
 * Initiate V2 deposit.
 */
Map<String, Object> response = client.initiateDepositV2(
        "DEPOSIT_ID",
        "5000",
        "UGX",
        "256783456789",
        "MTN_MOMO_UGA",
        "Payment for Order #123",
        "REF_123",
        null,
        metaData
).join();

/**
 * Handle response.
 */
int status = (int) response.get("status");

System.out.println("Status: " + status);
System.out.println("Response: " + response.get("response"));

/**
 * Metadata helper.
 */
private static Map<String, Object> createMeta(
        String fieldName,
        String fieldValue
) {
    Map<String, Object> meta = new HashMap<>();

    meta.put("fieldName", fieldName);
    meta.put("fieldValue", fieldValue);

    return meta;
}
```

---

## V1 Deposit (Legacy)

```java
Map<String, Object> response = client.initiateDeposit(
        "DEPOSIT_ID",
        "5000",
        "UGX",
        "MTN_MOMO_UGA",
        "256783456789",
        "Payment for Order",
        "REF_123",
        null
).join();
```

---

# Payouts

## V2 Payout

```java
List<Object> metaData = new ArrayList<>();

metaData.add(createMeta("orderId", "ORD-998877"));
metaData.add(createMeta("customerId", "CUST-001"));
metaData.add(createMeta("reason", "Refund for returned item"));

Map<String, Object> response = client.initiatePayoutV2(
        "payout_ID",
        "10000",
        "UGX",
        "256783456789",
        "MTN_MOMO_UGA",
        "Refund for Order #123",
        metaData
).join();

System.out.println(response.get("response"));
```

---

## V1 Payout (Legacy)

```java
Map<String, Object> response = client.initiatePayout(
        "payout_ID",
        "10000",
        "UGX",
        "MTN_MOMO_UGA",
        "256783456789",
        "Refund for Order",
        metaData
).join();
```

---

# Refunds

Refunds require a previously successful deposit transaction ID.

---

## V2 Refund

```java
String originalDepositId = "DEPOSIT_ID";

List<Object> metaData = new ArrayList<>();

metaData.add(createMeta("reason", "Customer requested refund"));
metaData.add(createMeta("adminUser", "Admin-01"));
metaData.add(createMeta("refundSource", "Java SDK"));

Map<String, Object> response = client.initiateRefundV2(
        "refund_ID",
        originalDepositId,
        "5000",
        "UGX",
        metaData
).join();

System.out.println(response.get("response"));
```

---

## V1 Refund (Legacy)

```java
Map<String, Object> response = client.initiateRefund(
        "refund_ID",
        originalDepositId,
        "5000",
        metaData
).join();
```

---

# Payment Page Sessions

Create hosted payment pages for customer checkout flows.

---

## V2 Payment Page

```java
Map<String, Object> params = new HashMap<>();

params.put("depositId", Helpers.generateUniqueId());
params.put("amount", "15000");
params.put("currency", "UGX");
params.put("returnUrl", "https://example.com/payment-success");
params.put("customerMessage", "Payment for Premium Plan");
params.put("reason", "Monthly Subscription");
params.put("country", "UGA"); 
params.put("phoneNumber", "256783456789");

Map<String, Object> response = client
        .createPaymentPageSessionAuto(params)
        .join();

int status = (int) response.get("status");

if (status >= 200 && status <= 202) {

    Object responseData = response.get("response");

    if (responseData instanceof Map) {

        Map<?, ?> data = (Map<?, ?>) responseData;

        String redirectUrl = data.get("redirectUrl").toString();

        System.out.println("Redirect URL: " + redirectUrl);
    }
}
```

---

## V1 Payment Page (Legacy)

```java
Map<String, Object> params = new HashMap<>();

params.put("depositId", Helpers.generateUniqueId());
params.put("amount", "15000");
params.put("returnUrl", "https://example.com/payment-success");
params.put("statementDescription", "Premium Plan Payment");
params.put("reason", "Monthly Subscription");
params.put("msisdn", "256783456789");
params.put("country", "UGA");

Map<String, Object> response = client
        .createPaymentPageSessionAuto(params)
        .join();
```

---

# Transaction Status

Track deposits, payouts, and refunds.

```java
/**
 * Deposit status.
 */
Map<String, Object> depositStatus =
        client.checkTransactionStatusAuto(
                "DEPOSIT_ID",
                "deposit"
        ).join();

System.out.println(depositStatus.get("response"));

/**
 * Payout status.
 */
Map<String, Object> payoutStatus =
        client.checkTransactionStatusAuto(
                "payout_ID",
                "payout"
        ).join();

/**
 * Refund status.
 */
Map<String, Object> refundStatus =
        client.checkTransactionStatusAuto(
                "refund_ID",
                "refund"
        ).join();
```

---

# MNO Availability & Active Configuration

Retrieve live provider availability and active operational configuration.

```java
/**
 * Check MNO availability.
 */
Map<String, Object> mnoResponse =
        client.checkMnoAvailabilityAuto(
                null,
                null
        ).join();

System.out.println(mnoResponse.get("response"));

/**
 * Check active configuration.
 */
Map<String, Object> confResponse =
        client.checkActiveConfAuto(
                null,
                null
        ).join();

System.out.println(confResponse.get("response"));
```

---

# API Version Compatibility

| Feature | V1 | V2 |
|---|---|---|
| Deposits | ✅ | ✅ |
| Payouts | ✅ | ✅ |
| Refunds | ✅ | ✅ |
| Payment Pages | ✅ | ✅ |
| Transaction Status | ✅ | ✅ |
| MNO Availability | ✅ | ✅ |
| Active Configuration | ✅ | ✅ |
| Metadata Support | Limited | Full |
| Currency Parameter | ❌ | ✅ |

> V2 is recommended for all modern integrations.

---

# Security & Integrity

The SDK implements multiple protection layers designed for enterprise payment environments.

---

## Hardware-Bound Licensing

Licenses are tied to machine fingerprints. Deploying the SDK on a different server without license re-issuance may trigger validation failures.

---

## Runtime Integrity Verification

The SDK periodically validates:

- Java bytecode integrity
- Native library integrity
- Runtime execution state

Detected modifications may place the SDK into a degraded operational mode.

---

## Native Rust Security Core

Critical operations are delegated to a native Rust engine, including:

- Cryptographic hashing
- Signature verification
- License validation
- Runtime protection logic

This reduces Java-level tampering risks.

---

## SSL Enforcement

Strict SSL verification is enabled by default.

Disabling SSL verification is only recommended for local debugging environments and may trigger security warnings.

---

# Handling Degraded Mode

If the SDK detects suspicious runtime behavior, the following protections may activate:

- Artificial request latency
- Randomized API failures
- Endpoint restrictions
- Complete SDK shutdown

Verify the following if degradation occurs:

1. License key validity
2. Matching server fingerprint
3. Native library integrity
4. Runtime instrumentation tools
5. JVM compatibility

---

# Error Handling

All SDK methods return:

```java
CompletableFuture<Map<String, Object>>
```

---

## Example Error Handling

```java
Map<String, Object> response =
        client.initiateDepositV2(...).join();

/**
 * Read status code.
 */
int status = (int) response.get("status");

if (status >= 200 && status < 300) {

    /**
     * Success response.
     */
    Object data = response.get("response");

    System.out.println("Success: " + data);

} else {

    /**
     * Failure response.
     */
    Object errorData = response.get("response");

    System.err.println(
            "Error (" + status + "): " + errorData
    );
}
```

---

# Common Error Scenarios

| Scenario | Description |
|---|---|
| License Validation Failure | Invalid or expired SDK license |
| Integrity Violation | Runtime tampering detected |
| Authentication Failure | Invalid API token |
| Network Failure | Unable to reach PawaPay API |
| Invalid Parameters | Missing or malformed request fields |

---
# Live Sandbox Testing

You can access the live sandbox environment to test your integration here: [Live Java Sandbox Demo](https://katorymnd.dev/pawapay-demo/java/).

The live sandbox allows you to:

- Test deposits and payouts
- Validate request payloads
- Inspect real API responses
- Simulate mobile money flows
- Verify SDK integration behavior

> Use your own PawaPay Sandbox API token when testing in the live environment.

### Live Java Sandbox Demo

[Live Java Playground](https://katorymnd.dev/pawapay-demo/java/)

---

# SDK Licensing

The PawaPay Java SDK uses secure hardware-bound licensing for runtime validation and integrity protection.

To obtain a valid SDK license key, please visit the [official licensing portal](https://katorymnd.com/pawapay-payment-sdk/java/).

The licensing portal provides:

- SDK license generation
- Domain binding
- Runtime activation
- Environment authorization
- License management

---

# Recommended Environment Variables

For complete SDK initialization and runtime validation, your `.env` file should typically include:

```env
PAWAPAY_SANDBOX_API_TOKEN=your_sandbox_api_token
PAWAPAY_PRODUCTION_API_TOKEN=your_production_api_token
KATORYMND_PAWAPAY_SDK_LICENSE_KEY=your_license_key
PAWAPAY_SDK_LICENSE_DOMAIN=your_domain.com
PAWAPAY_SDK_LICENSE_SECRET=your_license_secret
```

---

# Recommended Developer Flow

1. Download or clone the SDK
2. Configure your `.env`
3. Add your SDK license key
4. Run the bootstrap setup command
5. Test inside the live sandbox environment
6. Move to production after validation

---

# Sandbox Testing Notes

- Sandbox transactions are intended for development and integration testing only.
- Always use sandbox API keys inside the testing lab.
- Production keys should only be used in secured live deployments.
- Runtime validation files generated during setup should remain untouched.

---
# Support

For integration assistance, licensing support, or security-related issues:

- Email: support@katorymnd.com
- Website: https://katorymnd.com

When reporting issues, include:

- License key
- Server fingerprint
- JVM version
- Operating system
- Relevant SDK logs

---

© 2026 Katorymnd Web Solutions. All rights reserved.