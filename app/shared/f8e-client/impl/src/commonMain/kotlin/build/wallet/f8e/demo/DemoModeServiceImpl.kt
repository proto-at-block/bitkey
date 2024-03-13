package build.wallet.f8e.demo

import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.UnauthenticatedF8eHttpClient
import build.wallet.ktor.result.bodyResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class DemoModeServiceImpl(
  private val f8eHttpClient: UnauthenticatedF8eHttpClient,
) : DemoModeService {
  override suspend fun initiateDemoMode(
    f8eEnvironment: F8eEnvironment,
    code: String,
  ): Result<Unit, Error> {
    return f8eHttpClient.unauthenticated(f8eEnvironment)
      .bodyResult<Unit> {
        post("/api/demo/initiate") {
          setBody(
            InitiateDemoModeRequest(
              code = code
            )
          )
        }
      }.mapError { Error("Unable to validate code for demo mode") }
  }
}

@Serializable
private data class InitiateDemoModeRequest(
  @SerialName("code")
  val code: String,
)
