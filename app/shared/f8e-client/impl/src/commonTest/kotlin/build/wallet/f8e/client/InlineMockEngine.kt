package build.wallet.f8e.client

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS

data class InlineHandler(
  val request: HttpRequestData,
  val scope: MockRequestHandleScope,
)

/**
 * A wrapper for [MockEngine] to provide request/response handling
 * within test cases.
 *
 * ```
 * val inlineMockEngine = InlineMockEngine()
 * val http = HttpClient(inlineMockEngine.engine)
 *
 * fun testCase() = runTest {
 *    launch {
 *      inlineMockEngine.handle { request ->
 *        if (request.url.encodedPath == "/success") {
 *          respondOk()
 *        } else {
 *          respond(404)
 *        }
 *      }
 *    }
 *
 *    assetTrue(http.get("/success").isSuccess)
 * }
 * ```
 */
class InlineMockEngine {
  private val requestChannel = Channel<InlineHandler>(RENDEZVOUS)
  private val responseChannel = Channel<HttpResponseData>(RENDEZVOUS)
  val engine = MockEngine { request ->
    requestChannel.send(InlineHandler(request, this))
    responseChannel.receive()
  }

  suspend fun handle(body: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val (request, scope) = requestChannel.receive()
    responseChannel.send(body(scope, request))
  }
}
