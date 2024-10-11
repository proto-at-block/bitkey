package build.wallet.f8e.inheritance

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.setUnredactedBody
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.post

class UploadInheritanceMaterialF8eClientImpl(
  private val f8eClient: F8eHttpClient,
) : UploadInheritanceMaterialF8eClient {
  override suspend fun uploadInheritanceMaterial(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    inheritanceMaterial: InheritanceMaterial,
  ): Result<Unit, Throwable> {
    return f8eClient.authenticated(
      f8eEnvironment,
      fullAccountId
    ).catching {
      post("/api/accounts/${fullAccountId.serverId}/recovery/inheritance/packages") {
        withDescription("Uploading Inheritance Material")
        setUnredactedBody(inheritanceMaterial)
      }
    }.mapUnit()
  }
}
