package build.wallet.f8e

import build.wallet.f8e.error.F8eClientErrorResponse
import build.wallet.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.error.code.GeneralClientErrorCode
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import build.wallet.f8e.error.code.MobilePayErrorCode
import build.wallet.f8e.error.code.MobilePayErrorCode.NO_SPENDING_LIMIT_EXISTS
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class F8eErrorTests : FunSpec({

  val jsonDecoder = Json { ignoreUnknownKeys = true }

  test("error code deserialized from error response - GeneralClientErrorCode") {
    val json = """{"errors":[{"category":"INVALID_REQUEST_ERROR","code":"BAD_REQUEST"}]}"""
    val clientErrorResponse: F8eClientErrorResponse<GeneralClientErrorCode> =
      jsonDecoder.decodeFromString(
        json
      )
    clientErrorResponse.errors.first().code
      .shouldBe(GeneralClientErrorCode.BAD_REQUEST)
  }

  test("error code deserialized from error response - AddTouchpointClientErrorCode") {
    val json =
      """
      {"message":"Unsupported country code","errors":[{"category":"INVALID_REQUEST_ERROR","code":"UNSUPPORTED_COUNTRY_CODE","detail":"Unsupported country code"}]}
      """.trimIndent()
    val clientErrorResponse: F8eClientErrorResponse<AddTouchpointClientErrorCode> =
      jsonDecoder.decodeFromString(
        json
      )
    clientErrorResponse.errors.first().code
      .shouldBe(AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE)
  }

  test("error code deserialized from error response - VerifyTouchpointClientErrorCode") {
    val json = """{"errors":[{"category":"INVALID_REQUEST_ERROR","code":"CODE_MISMATCH"}]}"""
    val clientErrorResponse: F8eClientErrorResponse<VerifyTouchpointClientErrorCode> =
      jsonDecoder.decodeFromString(
        json
      )
    clientErrorResponse.errors.first().code
      .shouldBe(VerifyTouchpointClientErrorCode.CODE_MISMATCH)
  }

  test("error code deserialized from error response - InitiateAccountDelayNotifyErrorCode") {
    val json = """{"errors":[{"category":"AUTHENTICATION_ERROR","code":"COMMS_VERIFICATION_REQUIRED"}]}"""
    val clientErrorResponse: F8eClientErrorResponse<InitiateAccountDelayNotifyErrorCode> =
      jsonDecoder.decodeFromString(
        json
      )
    clientErrorResponse.errors.first().code
      .shouldBe(InitiateAccountDelayNotifyErrorCode.COMMS_VERIFICATION_REQUIRED)
  }

  test("error code deserialized from error response - CancelDelayNotifyRecoveryErrorCode") {
    val json = """{"errors":[{"category":"AUTHENTICATION_ERROR","code":"COMMS_VERIFICATION_REQUIRED"}]}"""
    val clientErrorResponse: F8eClientErrorResponse<CancelDelayNotifyRecoveryErrorCode> =
      jsonDecoder.decodeFromString(
        json
      )
    clientErrorResponse.errors.first().code
      .shouldBe(CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED)
  }

  test("error code deserialized from error response - MobilePayErrorCode") {
    val json = """{"errors":[{"category":"INVALID_REQUEST_ERROR","code":"NO_SPENDING_LIMIT_EXISTS"}]}"""
    val clientErrorResponse: F8eClientErrorResponse<MobilePayErrorCode> =
      jsonDecoder.decodeFromString(
        json
      )
    clientErrorResponse.errors.first().code
      .shouldBe(NO_SPENDING_LIMIT_EXISTS)
  }
})
