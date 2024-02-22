package build.wallet.availability

import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.catching
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.HttpClient
import io.ktor.client.request.head

class InternetNetworkReachabilityServiceImpl : InternetNetworkReachabilityService {
  override suspend fun checkConnection(): Result<Unit, HttpError> {
    return HttpClient().catching {
      // TODO (W-5710): Replace with konnection library
      head("https://google.com") {}
    }.map {}
  }
}
