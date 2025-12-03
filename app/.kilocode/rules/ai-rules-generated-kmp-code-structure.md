# Kotlin Multiplatform Code Structure

## Summary
Defines KMP structure: shared business logic in `commonMain`, platform-specific via expect/actual, proper source set usage.

## When to Apply
- Creating/modifying KMP modules
- Deciding code placement (shared vs platform-specific)
- Implementing expect/actual declarations

## How to Apply

### Shared Code (commonMain)
**Should be shared:**
- Domain business logic (state machines, services, use cases)
- UI presentation models and Compose UI Multiplatform screens  
- Data access layer (DAOs, SQLDelight database)
- Domain models and data structures

### Platform-Specific Code
**Cannot be shared:**
- Native platform APIs (cryptography, NFC, phone parsing)
- Non-KMP dependencies (BDK artifacts)
- Platform-specific UI when Compose Multiplatform insufficient

**Strategy:** Use expect/actual classes/functions, align with shared abstractions.

### Source Sets & Targets
**Targets:** `android`, `ios`, `jvm` (for compilation/tests)

**Main:** `commonMain`, `commonJvmMain`, `androidMain`, `iosMain`, `jvmMain`
**Test:** `commonTest`, `commonJvmTest`, `androidUnitTest`, `iosTest`, `jvmTest`
**Integration:** `commonIntegrationTest`, `commonJvmIntegrationTest`, `jvmIntegrationTest`

### Code Placement
- **Default:** Start with `commonMain` for all business logic
- **Platform-specific:** Only when KMP constraints require it
- **Prefer:** expect/actual over duplicate implementations

## Example
```kotlin
// ✅ GOOD: Shared business logic in commonMain
// src/commonMain/kotlin/PaymentService.kt
interface PaymentService {
  suspend fun processPayment(request: PaymentRequest): Result<Payment, PaymentError>
}

class PaymentServiceImpl(
  private val cryptoProvider: CryptoProvider, // expect/actual
  private val paymentDao: PaymentDao // shared DAO
) : PaymentService {
  override suspend fun processPayment(request: PaymentRequest): Result<Payment, PaymentError> {
    // Shared business logic using platform abstractions
  }
}

// ✅ GOOD: Platform abstraction using expect/actual
// src/commonMain/kotlin/CryptoProvider.kt
expected interface CryptoProvider {
  suspend fun signTransaction(data: ByteArray): Result<Signature, CryptoError>
}

// src/androidMain/kotlin/CryptoProvider.kt
actual class AndroidCryptoProvider : CryptoProvider {
  actual override suspend fun signTransaction(data: ByteArray): Result<Signature, CryptoError> {
    // Android-specific crypto implementation
  }
}

// ❌ BAD: Duplicating business logic across platforms
// AndroidPaymentService.kt and IosPaymentService.kt with identical logic
```

**Module Structure:**
```
src/
  commonMain/kotlin/          // Shared business logic
    build/wallet/payment/PaymentService.kt
  androidMain/kotlin/         // Android-specific implementations
    build/wallet/payment/AndroidCryptoProvider.kt
  iosMain/kotlin/            // iOS-specific implementations
    build/wallet/payment/IosCryptoProvider.kt
  commonTest/kotlin/         // Shared test code
    build/wallet/payment/PaymentServiceTest.kt
```

## Related Rules
- @ai-rules/module-structure.md (module organization)
- @ai-rules/strong-typing.md (domain types across platforms)
- @ai-rules/dao-pattern.md (shared data access layer)
