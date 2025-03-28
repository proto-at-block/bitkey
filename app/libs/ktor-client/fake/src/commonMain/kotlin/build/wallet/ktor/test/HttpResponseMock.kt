package build.wallet.ktor.test

import io.ktor.client.*
import io.ktor.client.call.HttpClientCall
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class HttpResponseMock(
  override val status: HttpStatusCode,
  private val callAttributes: Attributes = Attributes(),
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
            attributes = callAttributes
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
