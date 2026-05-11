package bitkey.privilegedactions

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import bitkey.auth.RefreshToken
import build.wallet.account.AccountServiceFake
import build.wallet.auth.AppAuthKeyMessageSignerMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.f8e.actionproof.ActionProofFormatF8eClientFake
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.platform.ActionProofAction
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.W3NfcCommands
import com.github.michaelbull.result.getOrElse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Instant
import uniffi.actionproof.ContextBindingPair

/**
 * End-to-end integration tests for the Action Proof flow.
 *
 * These tests exercise the complete action proof lifecycle using real implementations
 * with deterministic fakes — no native library dependencies required:
 *
 * - [ActionProofServiceImpl] for domain logic (payload building, header creation)
 * - [ActionProofFfiProviderFake] for deterministic FFI operations
 * - [FakeSigningNfcCommands] for deterministic hardware signing with two-tap emulation
 *
 * Note: [FakeSigningNfcCommands] emulates BitkeyW3CommandsFake's ConfirmWithEmulatedPrompt
 * two-tap pattern with a deterministic signature derived from the bindings input. We cannot
 * use BitkeyW3CommandsFake directly for the approve path because its signing pipeline
 * (MessageSigner -> DER decode -> hex) requires real crypto that MessageSignerFake does not
 * provide. The fake captures all call parameters and derives signatures from bindings so
 * tests can verify both payload wiring and that different inputs produce different outputs.
 *
 * HTTP header serialization via ActionProofPlugin is covered in
 * domain/f8e-client/impl ActionProofPluginTests and not duplicated here.
 */
class ActionProofIntegrationTests : FunSpec({

  // -- Shared test infrastructure --
  // Fresh instances per test to prevent cross-contamination under parallel execution.

  lateinit var accountService: AccountServiceFake
  lateinit var authTokensService: AuthTokensServiceFake
  lateinit var ffiProvider: ActionProofFfiProviderFake
  lateinit var appAuthKeyMessageSigner: AppAuthKeyMessageSignerMock
  lateinit var signatureUtils: SignatureUtilsMock
  lateinit var service: ActionProofServiceImpl

  val testTokens = AccountAuthTokens(
    accessToken = AccessToken("test-access-token"),
    refreshToken = RefreshToken("test-refresh-token"),
    accessTokenExpiresAt = Instant.DISTANT_FUTURE
  )

  beforeTest {
    accountService = AccountServiceFake()
    authTokensService = AuthTokensServiceFake()
    ffiProvider = ActionProofFfiProviderFake()
    appAuthKeyMessageSigner = AppAuthKeyMessageSignerMock()
    signatureUtils = SignatureUtilsMock()

    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    service = ActionProofServiceImpl(
      accountService = accountService,
      authTokensService = authTokensService,
      ffiProvider = ffiProvider,
      actionProofFormatF8eClient = ActionProofFormatF8eClientFake(),
      appAuthKeyMessageSigner = appAuthKeyMessageSigner,
      signatureUtils = signatureUtils
    )
  }

  // ========================================================================
  // 1. Happy path: build payload -> NFC sign -> attach header -> f8e call
  // ========================================================================

  context("happy path: full action proof flow") {
    test("build bindings, NFC sign, create header with correct payload wiring") {
      val nonce = "test-nonce-1"
      val session = NfcSessionFake.invoke()
      val nfcCommands = FakeSigningNfcCommands()

      // Step 1: Build bindings (includes token binding automatically)
      val bindings = service.buildBindings(
        extra = mapOf("eid" to "entity-123"),
        nonce = nonce
      ).getOrElse { error("buildBindings failed: $it") }

      bindings shouldContain "tb=fake-token-binding-abc123"
      bindings shouldContain "eid=entity-123"
      bindings shouldContain "n=$nonce"

      // Step 2: NFC sign via W3 hardware (two-tap flow)
      val interaction = nfcCommands.signActionProof(
        session = session,
        version = 1u,
        action = ActionProofAction.SET_RECOVERY_EMAIL,
        value = "test@example.com",
        bindings = bindings
      )

      interaction.shouldBeInstanceOf<HardwareInteraction.ConfirmWithEmulatedPrompt<*>>()
      val prompt = interaction as HardwareInteraction.ConfirmWithEmulatedPrompt<String>
      prompt.approve.shouldNotBeNull()
      prompt.deny.shouldNotBeNull()

      // Verify the NFC command received the correct parameters
      nfcCommands.signActionProofCalls shouldHaveSize 1
      val nfcCall = nfcCommands.signActionProofCalls.first()
      nfcCall.version shouldBe 1u
      nfcCall.action shouldBe ActionProofAction.SET_RECOVERY_EMAIL
      nfcCall.value shouldBe "test@example.com"
      nfcCall.bindings shouldBe bindings

      // Step 3: User approves on device, second tap retrieves signature
      val signResult = prompt.approve.fetchResult(session, nfcCommands)

      signResult.shouldBeInstanceOf<HardwareInteraction.Completed<*>>()
      val signature = (signResult as HardwareInteraction.Completed<String>).result

      // Signature is derived from bindings — should be valid 128 lowercase hex
      signature.length shouldBe 128
      signature shouldBe nfcCommands.signatureForBindings(bindings)

      // Step 4: Create action proof header
      val header = service.createActionProofHeader(
        signatures = listOf(signature),
        nonce = nonce
      ).getOrElse { error("createActionProofHeader failed: $it") }

      header.version shouldBe 1
      header.signatures shouldBe listOf(signature)
      header.nonce shouldBe nonce
    }
  }

  // ========================================================================
  // 2. NFC failure and retry: fresh nonce produces new signature
  // ========================================================================

  context("NFC failure and retry") {
    test("retry with fresh nonce produces different bindings and different signature") {
      val session = NfcSessionFake.invoke()

      // Use a single commands instance that fails once then succeeds
      val nfcCommands = FakeSigningNfcCommands(failOnFirstAttempt = true)

      // First attempt with nonce-1
      val nonce1 = "nonce-1"
      val bindings1 = service.buildBindings(
        extra = mapOf("eid" to "entity-456"),
        nonce = nonce1
      ).getOrElse { error("buildBindings failed") }

      bindings1 shouldContain "n=nonce-1"

      // First signing attempt fails with NFC error
      shouldThrow<NfcException.CanBeRetried.TagLost> {
        nfcCommands.signActionProof(
          session = session,
          version = 1u,
          action = ActionProofAction.SET_RECOVERY_EMAIL,
          value = "test@example.com",
          bindings = bindings1
        )
      }

      // Retry with fresh nonce
      val nonce2 = "nonce-2"
      val bindings2 = service.buildBindings(
        extra = mapOf("eid" to "entity-456"),
        nonce = nonce2
      ).getOrElse { error("buildBindings failed on retry") }

      bindings2 shouldContain "n=nonce-2"
      bindings1 shouldNotBe bindings2

      // Same commands instance now succeeds on second call
      val interaction = nfcCommands.signActionProof(
        session = session,
        version = 1u,
        action = ActionProofAction.SET_RECOVERY_EMAIL,
        value = "test@example.com",
        bindings = bindings2
      )

      val prompt = interaction as HardwareInteraction.ConfirmWithEmulatedPrompt<String>
      val signResult = prompt.approve.fetchResult(session, nfcCommands) as HardwareInteraction.Completed<String>

      // Verify the retry used the fresh bindings (with nonce-2)
      nfcCommands.signActionProofCalls shouldHaveSize 2
      nfcCommands.signActionProofCalls[0].bindings shouldBe bindings1
      nfcCommands.signActionProofCalls[1].bindings shouldBe bindings2

      // Key assertion: different bindings produce different signatures
      val expectedSig1 = nfcCommands.signatureForBindings(bindings1)
      val expectedSig2 = nfcCommands.signatureForBindings(bindings2)
      expectedSig1 shouldNotBe expectedSig2
      signResult.result shouldBe expectedSig2

      val header = service.createActionProofHeader(
        signatures = listOf(signResult.result),
        nonce = nonce2
      ).getOrElse { error("createActionProofHeader failed") }

      header.nonce shouldBe nonce2

      // Verify buildBindings was called twice with different nonces
      ffiProvider.computeTokenBindingCalls shouldHaveSize 2
    }
  }

  // ========================================================================
  // 3. User cancellation: deny on device terminates cleanly
  // ========================================================================

  context("user cancellation") {
    test("deny on device throws UserDenied, no signature produced, deny called exactly once") {
      val session = NfcSessionFake.invoke()
      val nonce = "cancel-nonce"
      val nfcCommands = FakeSigningNfcCommands()

      val bindings = service.buildBindings(nonce = nonce)
        .getOrElse { error("buildBindings failed") }

      val interaction = nfcCommands.signActionProof(
        session = session,
        version = 1u,
        action = ActionProofAction.ADD_RECOVERY_CONTACT,
        value = "Alice",
        bindings = bindings
      )

      // Verify correct parameters were sent to hardware
      nfcCommands.signActionProofCalls shouldHaveSize 1
      nfcCommands.signActionProofCalls.first().action shouldBe ActionProofAction.ADD_RECOVERY_CONTACT
      nfcCommands.signActionProofCalls.first().value shouldBe "Alice"

      val prompt = interaction as HardwareInteraction.ConfirmWithEmulatedPrompt<String>
      // User denies on device — second tap throws UserDenied
      shouldThrow<NfcException.UserDenied> {
        prompt.deny.fetchResult(session, nfcCommands)
      }

      // Verify deny happened exactly once and no approve-path signature was produced
      nfcCommands.approveCallCount shouldBe 0
      nfcCommands.denyCallCount shouldBe 1
    }
  }

  // ========================================================================
  // 4. Signature validation: malformed signatures rejected in integrated flow
  // ========================================================================

  context("signature validation in integrated flow") {
    test("valid hardware signature is accepted after NFC signing") {
      val session = NfcSessionFake.invoke()
      val nonce = "valid-sig-nonce"
      val nfcCommands = FakeSigningNfcCommands()

      val bindings = service.buildBindings(nonce = nonce)
        .getOrElse { error("buildBindings failed") }

      val interaction = nfcCommands.signActionProof(
        session = session,
        version = 1u,
        action = ActionProofAction.SET_RECOVERY_PHONE,
        value = "+15551234567",
        bindings = bindings
      )

      val prompt = interaction as HardwareInteraction.ConfirmWithEmulatedPrompt<String>
      val signResult = prompt.approve.fetchResult(session, nfcCommands) as HardwareInteraction.Completed<String>
      val hwSignature = signResult.result

      // The binding-derived signature should pass validation (128 lowercase hex)
      val headerResult = service.createActionProofHeader(
        signatures = listOf(hwSignature),
        nonce = nonce
      )
      headerResult.isOk shouldBe true

      // Verify payload wiring: correct action and value reached hardware
      nfcCommands.signActionProofCalls.first().action shouldBe ActionProofAction.SET_RECOVERY_PHONE
      nfcCommands.signActionProofCalls.first().value shouldBe "+15551234567"
    }

    test("empty signature list is rejected") {
      val result = service.createActionProofHeader(signatures = emptyList(), nonce = "nonce")
      result.isErr shouldBe true
      result.error.shouldBeInstanceOf<ActionProofError.InvalidSignature>()
      (result.error as ActionProofError.InvalidSignature).message shouldContain "empty"
    }

    test("too-short signature is rejected") {
      val result = service.createActionProofHeader(
        signatures = listOf("abcd1234"),
        nonce = "nonce"
      )
      result.isErr shouldBe true
      result.error.shouldBeInstanceOf<ActionProofError.InvalidSignature>()
    }

    test("too-long signature is rejected") {
      val result = service.createActionProofHeader(
        signatures = listOf("a".repeat(130)), // 65 bytes would be 130 chars; valid is 128
        nonce = "nonce"
      )
      result.isErr shouldBe true
      result.error.shouldBeInstanceOf<ActionProofError.InvalidSignature>()
    }

    test("uppercase hex signature is rejected") {
      val result = service.createActionProofHeader(
        signatures = listOf("AB" + "cd".repeat(63)),
        nonce = "nonce"
      )
      result.isErr shouldBe true
      result.error.shouldBeInstanceOf<ActionProofError.InvalidSignature>()
    }

    test("non-hex characters in signature are rejected") {
      val result = service.createActionProofHeader(
        signatures = listOf("zz" + "cd".repeat(63)),
        nonce = "nonce"
      )
      result.isErr shouldBe true
      result.error.shouldBeInstanceOf<ActionProofError.InvalidSignature>()
    }
  }

  // ========================================================================
  // 5. Payload building: buildPayload via FFI fake
  // ========================================================================

  context("payload building") {
    test("buildPayload succeeds and passes correct args with exact binding values to FFI") {
      val result = service.buildPayload(
        action = uniffi.actionproof.Action.ADD_RECOVERY_CONTACT,
        value = "Alice",
        extra = mapOf("eid" to "contact-abc"),
        nonce = "payload-nonce"
      )

      result.isOk shouldBe true
      val payload = result.getOrElse { error("buildPayload failed: $it") }
      // ActionProofFfiProviderFake returns [0x01, 0x02, 0x03] by default
      payload shouldBe byteArrayOf(0x01, 0x02, 0x03)

      // Verify FFI provider received the correct action, value, and exact binding pairs
      ffiProvider.buildPayloadCalls shouldHaveSize 1
      val call = ffiProvider.buildPayloadCalls.first()
      call.action shouldBe uniffi.actionproof.Action.ADD_RECOVERY_CONTACT
      call.value shouldBe "Alice"
      call.bindings.shouldContainExactlyInAnyOrder(
        ContextBindingPair("eid", "contact-abc"),
        ContextBindingPair("n", "payload-nonce"),
        ContextBindingPair("tb", "fake-token-binding-abc123")
      )
    }
  }

  // ========================================================================
  // 6. Token binding: correctly computed and included
  // ========================================================================

  context("token binding") {
    test("token binding is computed from access token and included in bindings") {
      val bindings = service.buildBindings(
        extra = mapOf("eid" to "entity-789"),
        nonce = "tb-nonce"
      ).getOrElse { error("buildBindings failed") }

      // ActionProofFfiProviderFake returns "fake-token-binding-abc123"
      bindings shouldContain "tb=fake-token-binding-abc123"

      // Verify the correct access token was passed to FFI
      ffiProvider.computeTokenBindingCalls shouldHaveSize 1
      ffiProvider.computeTokenBindingCalls.first() shouldBe "test-access-token"
    }

    test("token binding is alphabetically sorted among other bindings") {
      val bindings = service.buildBindings(
        extra = mapOf("eid" to "abc", "zeta" to "z"),
        nonce = "sort-nonce"
      ).getOrElse { error("buildBindings failed") }

      val keys = bindings.split(",").map { it.substringBefore("=") }
      // alphabetical: eid, n, tb, zeta
      keys shouldBe listOf("eid", "n", "tb", "zeta")
    }

    test("token binding fails without active account") {
      accountService.reset() // removes active account

      val result = service.computeTokenBinding()

      result.isErr shouldBe true
      result.error shouldBe ActionProofError.NoAccount
    }

    test("token binding fails without auth tokens") {
      authTokensService.reset() // removes tokens but account still active

      val result = service.computeTokenBinding()

      result.isErr shouldBe true
      result.error shouldBe ActionProofError.NoAuthToken
    }
  }

  // ========================================================================
  // 7. Multiple signatures: app and hardware signatures in header
  // ========================================================================

  context("multiple signatures") {
    test("app and hardware signatures are both included in header") {
      val session = NfcSessionFake.invoke()
      val nonce = "multi-sig-nonce"
      val nfcCommands = FakeSigningNfcCommands()

      val bindings = service.buildBindings(nonce = nonce)
        .getOrElse { error("buildBindings failed") }

      // Get hardware signature via W3 fake two-tap flow
      val interaction = nfcCommands.signActionProof(
        session = session,
        version = 1u,
        action = ActionProofAction.DISABLE_SPEND_WITHOUT_HARDWARE,
        value = null,
        bindings = bindings
      )
      val prompt = interaction as HardwareInteraction.ConfirmWithEmulatedPrompt<String>
      val signResult = prompt.approve.fetchResult(session, nfcCommands) as HardwareInteraction.Completed<String>
      val hwSignature = signResult.result

      // Verify payload wiring for null-value action
      nfcCommands.signActionProofCalls.first().action shouldBe ActionProofAction.DISABLE_SPEND_WITHOUT_HARDWARE
      nfcCommands.signActionProofCalls.first().value shouldBe null

      // Simulate app-side signature (valid 128-char hex, different from hw signature)
      val appSignature = "ab".repeat(64)

      // Create header with both signatures
      val header = service.createActionProofHeader(
        signatures = listOf(hwSignature, appSignature),
        nonce = nonce
      ).getOrElse { error("createActionProofHeader failed") }

      header.signatures shouldHaveSize 2
      header.signatures[0] shouldBe hwSignature
      header.signatures[1] shouldBe appSignature
      header.nonce shouldBe nonce
    }
  }
})

// ==========================================================================
// Test helpers
// ==========================================================================

/**
 * Fake NfcCommands that emulates BitkeyW3CommandsFake's two-tap ConfirmWithEmulatedPrompt
 * pattern for [signActionProof], using a deterministic binding-derived signature instead
 * of real crypto.
 *
 * **Signature derivation**: The approve-path signature is computed as a deterministic hash
 * of the [bindings] string, zero-padded to 128 lowercase hex chars (64 bytes). This ensures
 * different bindings (e.g., different nonces) produce different signatures, allowing tests
 * to verify that input changes propagate through to the signed output.
 *
 * This avoids the need for native library dependencies (MessageSigner, SignatureUtils)
 * while faithfully exercising the ConfirmWithEmulatedPrompt -> Approve/Deny -> Completed
 * interaction pattern that real W3 hardware uses.
 *
 * Captures all call parameters in [signActionProofCalls] and tracks [approveCallCount]
 * and [denyCallCount] so tests can assert on payload wiring, cancellation side-effects,
 * and that different inputs produce different outputs.
 *
 * @param failOnFirstAttempt If true, the first call to [signActionProof] throws
 *   [NfcException.CanBeRetried.TagLost] to simulate NFC connection loss
 */
private class FakeSigningNfcCommands(
  failOnFirstAttempt: Boolean = false,
) : W3NfcCommands by build.wallet.nfc.W3NfcCommandsMock({ name ->
    app.cash.turbine.Turbine(name = name)
  }) {
  private var shouldFail = failOnFirstAttempt

  /** All calls to [signActionProof], including failed ones. */
  val signActionProofCalls = mutableListOf<SignActionProofCall>()

  /** Number of times the Approve fetchResult was invoked (signature was produced). */
  var approveCallCount = 0
    private set

  /** Number of times the Deny fetchResult was invoked. */
  var denyCallCount = 0
    private set

  data class SignActionProofCall(
    val version: UInt,
    val action: ActionProofAction,
    val value: String?,
    val bindings: String,
  )

  /**
   * Computes the deterministic signature for a given bindings string.
   * Exposed for test assertions so callers can verify expected vs actual signatures.
   */
  fun signatureForBindings(bindings: String): String {
    // Use Kotlin's hashCode as a simple deterministic function, then zero-pad to 128 hex chars.
    // This is NOT cryptographically meaningful — it just ensures different bindings yield
    // different signatures while always producing valid 128 lowercase hex.
    val hash = bindings.hashCode().toUInt().toString(16)
    return hash.padStart(128, '0').take(128)
  }

  override suspend fun signActionProof(
    session: NfcSession,
    version: UInt,
    action: ActionProofAction,
    value: String?,
    bindings: String,
  ): HardwareInteraction<String> {
    signActionProofCalls.add(SignActionProofCall(version, action, value, bindings))

    if (shouldFail) {
      shouldFail = false
      throw NfcException.CanBeRetried.TagLost("Simulated NFC connection loss")
    }

    val sig = signatureForBindings(bindings)

    return HardwareInteraction.ConfirmWithEmulatedPrompt(
      approve = EmulatedPromptOption(
        fetchResult = { _, _ ->
          approveCallCount++
          HardwareInteraction.Completed(sig)
        }
      ),
      deny = EmulatedPromptOption(
        fetchResult = { _, _ ->
          denyCallCount++
          throw NfcException.UserDenied()
        }
      )
    )
  }
}
