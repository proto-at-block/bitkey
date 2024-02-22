package build.wallet.ktor.test

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.utils.EmptyContent
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.Attributes
import io.ktor.util.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class HttpResponseMock(
  override val status: HttpStatusCode,
) : HttpResponse() {
  override val call: HttpClientCall
    @OptIn(InternalAPI::class)
    get() =
      HttpClientCall(
        client = HttpClient(),
        requestData =
          HttpRequestData(
            url = Url("https://bitkey.world"),
            method = HttpMethod.Get,
            headers = Headers.Empty,
            body = EmptyContent,
            executionContext = Job(),
            attributes = Attributes()
          ),
        responseData =
          HttpResponseData(
            statusCode = status,
            requestTime = GMTDate.START,
            headers = Headers.Empty,
            version = HttpProtocolVersion.HTTP_2_0,
            body = "",
            callContext = EmptyCoroutineContext
          )
      )

  @InternalAPI
  override val content: ByteReadChannel get() = ByteChannel()
  override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
  override val headers: Headers get() = Headers.Empty
  override val requestTime: GMTDate get() = GMTDate()
  override val responseTime: GMTDate get() = GMTDate()
  override val version: HttpProtocolVersion get() = HttpProtocolVersion.HTTP_2_0
}
