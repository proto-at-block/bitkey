package bitkey.privilegedactions

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import bitkey.auth.RefreshToken
import build.wallet.account.AccountServiceFake
import build.wallet.auth.AppAuthKeyMessageSignerMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.f8e.actionproof.ActionProofFormatF8eClientFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.getOrElse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Instant
import uniffi.actionproof.Action
import uniffi.actionproof.ContextBindingPair

class ActionProofServiceImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val authTokensService = AuthTokensServiceFake()
  val ffiProvider = ActionProofFfiProviderFake()
  val appAuthKeyMessageSigner = AppAuthKeyMessageSignerMock()
  val signatureUtils = SignatureUtilsMock()

  lateinit var service: ActionProofServiceImpl

  val testTokens = AccountAuthTokens(
    accessToken = AccessToken("test-access-token"),
    refreshToken = RefreshToken("test-refresh-token"),
    accessTokenExpiresAt = Instant.DISTANT_FUTURE
  )

  beforeTest {
    accountService.reset()
    authTokensService.reset()
    ffiProvider.reset()
    appAuthKeyMessageSigner.reset()
    service = ActionProofServiceImpl(
      accountService = accountService,
      authTokensService = authTokensService,
      ffiProvider = ffiProvider,
      actionProofFormatF8eClient = ActionProofFormatF8eClientFake(),
      appAuthKeyMessageSigner = appAuthKeyMessageSigner,
      signatureUtils = signatureUtils
    )
  }

  test("computeTokenBinding returns error when no active account") {
    // Account service returns null by default when no account is set

    val result = service.computeTokenBinding()

    result.isErr shouldBe true
    result.error shouldBe ActionProofError.NoAccount
  }

  test("computeTokenBinding returns error when no tokens available") {
    accountService.setActiveAccount(FullAccountMock)
    // authTokensService has no tokens set

    val result = service.computeTokenBinding()

    result.isErr shouldBe true
    result.error shouldBe ActionProofError.NoAuthToken
  }

  test("computeTokenBinding returns binding from FFI provider") {
    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.computeTokenBinding()

    result.isOk shouldBe true
    result.value shouldBe "fake-token-binding-abc123"
    ffiProvider.computeTokenBindingCalls shouldBe listOf("test-access-token")
  }

  test("computeTokenBinding returns error for LiteAccount without Global tokens") {
    // Lite accounts only have Recovery-scoped tokens in production (see CreateLiteAccountServiceImpl),
    // and action proofs require hardware signing which Lite accounts don't have.
    // computeTokenBinding fails because getTokens requires Global-scoped tokens.
    accountService.setActiveAccount(LiteAccountMock)
    authTokensService.setTokens(LiteAccountMock.accountId, testTokens, AuthTokenScope.Recovery)

    val result = service.computeTokenBinding()

    result.isErr shouldBe true
    result.error shouldBe ActionProofError.NoAuthToken
  }

  test("computeTokenBinding works for SoftwareAccount") {
    accountService.setActiveAccount(SoftwareAccountMock)
    authTokensService.setTokens(SoftwareAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.computeTokenBinding()

    result.isOk shouldBe true
    result.value shouldBe "fake-token-binding-abc123"
  }

  test("computeTokenBinding uses explicit accountId when no active account exists") {
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.computeTokenBinding(accountId = FullAccountMock.accountId)

    result.isOk shouldBe true
    result.value shouldBe "fake-token-binding-abc123"
    ffiProvider.computeTokenBindingCalls shouldBe listOf("test-access-token")
  }

  test("buildBindings sorts keys alphabetically") {
    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.buildBindings(mapOf("zeta" to "z", "alpha" to "a", "mid" to "m"), nonce = "ff")

    result.isOk shouldBe true
    val bindings = result.value
    val parts = bindings.split(",")
    val keys = parts.map { it.substringBefore("=") }
    keys shouldBe listOf("alpha", "mid", "n", "tb", "zeta")
  }

  test("buildBindings includes token binding as tb key") {
    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.buildBindings(mapOf("eid" to "test-eid"), nonce = "ff")

    result.isOk shouldBe true
    val bindings = result.value
    bindings shouldContain "tb=fake-token-binding-abc123"
    bindings shouldContain "eid=test-eid"
  }

  test("buildBindings uses explicit accountId when no active account exists") {
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.buildBindings(
      extra = mapOf("eid" to "test-eid"),
      nonce = "ff",
      accountId = FullAccountMock.accountId
    )

    result.isOk shouldBe true
    result.value shouldBe "eid=test-eid,n=ff,tb=fake-token-binding-abc123"
  }

  test("buildBindings rejects extra map containing tb key") {
    val result = service.buildBindings(mapOf("tb" to "attempt-to-override"), nonce = "ff")

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidBindings>()
    (result.error as ActionProofError.InvalidBindings).message shouldContain "Token binding (tb) is computed automatically"
  }

  test("createActionProofHeader rejects empty signatures") {
    val result = service.createActionProofHeader(signatures = emptyList(), nonce = "ff")

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidSignature>()
    (result.error as ActionProofError.InvalidSignature).message shouldContain "Signatures list cannot be empty"
  }

  test("createActionProofHeader rejects invalid hex signatures") {
    val invalidSignature = "not-a-valid-hex-signature"

    val result = service.createActionProofHeader(signatures = listOf(invalidSignature), nonce = "ff")

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidSignature>()
    (result.error as ActionProofError.InvalidSignature).message shouldContain "lowercase hex characters"
  }

  test("createActionProofHeader rejects signatures with wrong length") {
    // Valid hex but wrong length (not 128 chars = 64 bytes)
    val shortSignature = "abcd1234"

    val result = service.createActionProofHeader(signatures = listOf(shortSignature), nonce = "ff")

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidSignature>()
    (result.error as ActionProofError.InvalidSignature).message shouldContain "lowercase hex characters"
  }

  test("createActionProofHeader rejects uppercase hex signatures") {
    // 130 chars but contains uppercase
    val uppercaseSignature = "AB" + "a".repeat(128)

    val result = service.createActionProofHeader(signatures = listOf(uppercaseSignature), nonce = "ff")

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidSignature>()
    (result.error as ActionProofError.InvalidSignature).message shouldContain "lowercase hex characters"
  }

  test("createActionProofHeader creates correct structure with nonce") {
    // Valid 65-byte hex signature (128 lowercase hex chars)
    val validSignature = "a".repeat(128)

    val result = service.createActionProofHeader(
      signatures = listOf(validSignature),
      nonce = "test-nonce-123"
    )

    result.isOk shouldBe true
    val proof = result.getOrElse { error("Should not fail") }
    proof.signatures shouldBe listOf(validSignature)
    proof.nonce shouldBe "test-nonce-123"
  }

  test("createActionProofHeader creates correct structure without nonce") {
    val validSignature = "b".repeat(128)

    val result = service.createActionProofHeader(
      signatures = listOf(validSignature),
      nonce = "ff"
    )

    result.isOk shouldBe true
    val proof = result.getOrElse { error("Should not fail") }
    proof.signatures shouldBe listOf(validSignature)
    proof.nonce shouldBe "ff"
  }

  test("createActionProofHeader accepts multiple valid signatures") {
    val signature1 = "a".repeat(128)
    val signature2 = "b".repeat(128)
    val signature3 = "0123456789abcdef".repeat(8)

    val result = service.createActionProofHeader(
      signatures = listOf(signature1, signature2, signature3),
      nonce = "multi-sig-nonce"
    )

    result.isOk shouldBe true
    val proof = result.getOrElse { error("Should not fail") }
    proof.signatures.size shouldBe 3
    proof.nonce shouldBe "multi-sig-nonce"
  }

  test("createActionProofHeader rejects if any signature in list is invalid") {
    val validSignature = "a".repeat(128)
    val invalidSignature = "INVALID"

    val result = service.createActionProofHeader(
      signatures = listOf(validSignature, invalidSignature),
      nonce = "ff"
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidSignature>()
    (result.error as ActionProofError.InvalidSignature).message shouldContain "lowercase hex characters"
  }

  test("buildPayload invokes provider with correct args and converts UByte list to ByteArray") {
    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)
    ffiProvider.buildPayloadResult = listOf(0xAAu, 0xBBu, 0xCCu)

    val result = service.buildPayload(
      action = Action.SET_RECOVERY_EMAIL,
      value = "new@example.com",
      extra = mapOf("eid" to "entity-123"),
      nonce = "test-nonce"
    )

    result.isOk shouldBe true
    result.value shouldBe byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())

    ffiProvider.buildPayloadCalls.size shouldBe 1
    val call = ffiProvider.buildPayloadCalls.first()
    call.action shouldBe Action.SET_RECOVERY_EMAIL
    call.value shouldBe "new@example.com"
    call.bindings.shouldContainExactlyInAnyOrder(
      ContextBindingPair("eid", "entity-123"),
      ContextBindingPair("n", "test-nonce"),
      ContextBindingPair("tb", "fake-token-binding-abc123")
    )
  }

  test("buildPayload includes nonce binding") {
    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.buildPayload(
      action = Action.ADD_BENEFICIARY,
      value = "beneficiary-id",
      extra = emptyMap(),
      nonce = "ff"
    )

    result.isOk shouldBe true

    val call = ffiProvider.buildPayloadCalls.first()
    call.bindings.shouldContainExactlyInAnyOrder(
      ContextBindingPair(key = "n", value = "ff"),
      ContextBindingPair("tb", "fake-token-binding-abc123")
    )
  }

  test("buildPayload rejects extra map containing tb key") {
    val result = service.buildPayload(
      action = Action.SET_RECOVERY_EMAIL,
      value = "new@example.com",
      extra = mapOf("tb" to "attempt-to-override"),
      nonce = "ff"
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidBindings>()
  }

  test("buildPayload rejects extra map containing nonce key") {
    val result = service.buildPayload(
      action = Action.SET_RECOVERY_EMAIL,
      value = null,
      extra = mapOf("n" to "attempt-to-override"),
      nonce = "ff"
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidBindings>()
  }

  test("buildPayload returns error when no active account") {
    val result = service.buildPayload(
      action = Action.DISABLE_RECOVERY_PHONE,
      value = null,
      extra = emptyMap(),
      nonce = "ff"
    )

    result.isErr shouldBe true
    result.error shouldBe ActionProofError.NoAccount
  }

  test("createAppSignedHeader success") {
    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.createAppSignedHeader(
      action = Action.CANCEL_LOST_HARDWARE_RECOVERY,
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isOk shouldBe true
    val header = result.getOrElse { error("Should not fail") }
    header.signatures.size shouldBe 1
    // Signature should be valid compact hex (128 chars from 64-byte decode)
    header.signatures.first().length shouldBe 128
  }

  test("createAppSignedHeader fails when signMessage fails") {
    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)
    appAuthKeyMessageSigner.result = Err(RuntimeException("signing failed"))

    val result = service.createAppSignedHeader(
      action = Action.CANCEL_LOST_HARDWARE_RECOVERY,
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InternalError>()
  }

  test("createAppSignedHeader fails when no account") {
    val result = service.createAppSignedHeader(
      action = Action.CANCEL_LOST_HARDWARE_RECOVERY,
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isErr shouldBe true
    result.error shouldBe ActionProofError.NoAccount
  }

  test("buildAppSignedPayload returns bindings, appSignature, and nonce") {
    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.buildAppSignedPayload(
      action = Action.ROTATE_APP_AUTH_KEYS,
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isOk shouldBe true
    val signed = result.getOrElse { error("Should not fail") }
    // Bindings should contain token binding and nonce
    signed.bindings shouldContain "tb="
    signed.bindings shouldContain "n="
    // App signature should be valid compact hex (128 chars from 64-byte decode)
    signed.appSignature.length shouldBe 128
    signed.appSignature.shouldMatch(Regex("^[0-9a-f]{128}$"))
    // Nonce should be 2 lowercase hex chars
    signed.nonce.shouldMatch(Regex("^[0-9a-f]{2}$"))
  }

  test("buildAppSignedPayload fails when no account") {
    val result = service.buildAppSignedPayload(
      action = Action.ROTATE_APP_AUTH_KEYS,
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isErr shouldBe true
    result.error shouldBe ActionProofError.NoAccount
  }

  test("buildAppSignedPayload fails when signMessage fails") {
    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)
    appAuthKeyMessageSigner.result = Err(RuntimeException("signing failed"))

    val result = service.buildAppSignedPayload(
      action = Action.ROTATE_APP_AUTH_KEYS,
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InternalError>()
  }

  test("buildAppSignedPayload includes extra bindings") {
    accountService.setActiveAccount(FullAccountMock)
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.buildAppSignedPayload(
      action = Action.ROTATE_APP_AUTH_KEYS,
      extra = mapOf("eid" to "entity-123"),
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isOk shouldBe true
    val signed = result.getOrElse { error("Should not fail") }
    signed.bindings shouldContain "eid=entity-123"
  }

  test("buildPayload uses explicit accountId when no active account exists") {
    authTokensService.setTokens(FullAccountMock.accountId, testTokens, AuthTokenScope.Global)

    val result = service.buildPayload(
      action = Action.DISABLE_RECOVERY_PHONE,
      value = null,
      extra = emptyMap(),
      nonce = "ff",
      accountId = FullAccountMock.accountId
    )

    result.isOk shouldBe true
    ffiProvider.buildPayloadCalls.first().bindings.shouldContainExactlyInAnyOrder(
      ContextBindingPair(key = "n", value = "ff"),
      ContextBindingPair("tb", "fake-token-binding-abc123")
    )
  }

  test("generateNonce returns 2 lowercase hex characters") {
    repeat(20) {
      service.generateNonce().shouldMatch(Regex("^[0-9a-f]{2}$"))
    }
  }

  test("cosignPayload returns error for blank bindings") {
    val result = service.cosignPayload(
      action = Action.ROTATE_SPENDING_KEYSET,
      preBuiltBindings = "",
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidBindings>()
    (result.error as ActionProofError.InvalidBindings).reason shouldContain "blank"
  }

  test("cosignPayload returns error for whitespace-only bindings") {
    val result = service.cosignPayload(
      action = Action.ROTATE_SPENDING_KEYSET,
      preBuiltBindings = "   ",
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidBindings>()
  }

  test("cosignPayload returns error for malformed binding pair missing equals") {
    val result = service.cosignPayload(
      action = Action.ROTATE_SPENDING_KEYSET,
      preBuiltBindings = "key1value1",
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidBindings>()
    (result.error as ActionProofError.InvalidBindings).reason shouldContain "Malformed"
  }

  test("cosignPayload returns error for empty key in binding pair") {
    val result = service.cosignPayload(
      action = Action.ROTATE_SPENDING_KEYSET,
      preBuiltBindings = "=value",
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidBindings>()
    (result.error as ActionProofError.InvalidBindings).reason shouldContain "Malformed"
  }

  test("cosignPayload returns error for trailing comma producing empty pair") {
    val result = service.cosignPayload(
      action = Action.ROTATE_SPENDING_KEYSET,
      preBuiltBindings = "key=value,",
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InvalidBindings>()
    (result.error as ActionProofError.InvalidBindings).reason shouldContain "Empty binding pair"
  }

  test("cosignPayload succeeds with valid bindings") {
    val result = service.cosignPayload(
      action = Action.ROTATE_SPENDING_KEYSET,
      preBuiltBindings = "n=ff,tb=token-binding-abc",
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isOk shouldBe true
    // Signature should be valid compact hex (128 chars)
    result.value.length shouldBe 128
    result.value.shouldMatch(Regex("^[0-9a-f]{128}$"))

    // Verify FFI was called with correct parsed bindings
    ffiProvider.buildPayloadCalls.size shouldBe 1
    val call = ffiProvider.buildPayloadCalls.first()
    call.action shouldBe Action.ROTATE_SPENDING_KEYSET
    call.bindings.shouldContainExactlyInAnyOrder(
      ContextBindingPair("n", "ff"),
      ContextBindingPair("tb", "token-binding-abc")
    )
  }

  test("cosignPayload allows value with equals sign") {
    val result = service.cosignPayload(
      action = Action.ROTATE_SPENDING_KEYSET,
      preBuiltBindings = "key=value=with=equals",
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isOk shouldBe true
    val call = ffiProvider.buildPayloadCalls.first()
    call.bindings shouldBe listOf(ContextBindingPair("key", "value=with=equals"))
  }

  test("cosignPayload returns error when signing fails") {
    appAuthKeyMessageSigner.result = Err(RuntimeException("signing failed"))

    val result = service.cosignPayload(
      action = Action.ROTATE_SPENDING_KEYSET,
      preBuiltBindings = "n=ff,tb=abc",
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )

    result.isErr shouldBe true
    result.error.shouldBeInstanceOf<ActionProofError.InternalError>()
  }
})
