package build.wallet.f8e.notifications

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.EmptyRequestBody
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.*

class TestNotificationF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : TestNotificationF8eClient {
  override suspend fun notification(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> {
    return f8eHttpClient.authenticated(
      f8eEnvironment,
      accountId
    ).bodyResult<EmptyResponseBody> {
      post("/api/accounts/${accountId.serverId}/notifications/test") {
        withDescription("Request a test notification from the server")
        setRedactedBody(EmptyRequestBody)
      }
    }.mapUnit()
  }
}
