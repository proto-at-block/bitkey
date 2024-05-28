package build.wallet.f8e.logging

import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.setRedactedBody
import build.wallet.logging.LogEntry
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class TestRequestData(
  val test: String,
) : RedactedRequestBody

@Serializable
private data class TestResponseData(
  val test: String,
) : RedactedResponseBody

class F8eServiceLoggerTests : FunSpec({
  var http: HttpClient? = null
  val logs = mutableListOf<LogEntry>()
  val mockEngine = MockEngine {
    val body = Json.encodeToString(TestResponseData("testdata"))
    respond(
      body,
      HttpStatusCode.OK,
      headers {
        append("X-Test", "testvalue")
        append("Content-Type", ContentType.Application.Json.toString())
      }
    )
  }

  fun createClient(block: F8eServiceLogger.Config.() -> Unit): HttpClient {
    return HttpClient(mockEngine) {
      install(ContentNegotiation) {
        json()
      }
      install(F8eServiceLogger, block)
    }
  }

  Logger.setLogWriters(object : LogWriter() {
    override fun log(
      severity: Severity,
      message: String,
      tag: String,
      throwable: Throwable?,
    ) {
      if (tag.endsWith("tests-f8e-log")) {
        logs.add(LogEntry(tag, message))
      }
    }
  })

  afterEach {
    http?.close()
    http = null
  }

  test("debug logs all raw data") {
    logs.clear()
    http = createClient {
      tag = "debug-tests-f8e-log"
      debug = true
      sanitize("X-Test") { "***${it.last()}" }
    }

    val response = http.shouldNotBeNull().post {
      contentType(ContentType.Application.Json)
      setRedactedBody(TestRequestData("request"))
      header("X-Test", "test")

      withExtras("Test Extra", Unit)
    }

    response.body<TestResponseData>()

    response.call.attributes[F8eCallLogger].awaitClose()

    logs.size.shouldBeEqual(2)
    val (requestTag, requestMessage) = logs[0]
    val (responseTag, responseMessage) = logs[1]
    requestTag.shouldBeEqual("debug-tests-f8e-log")
    requestMessage.shouldBeEqual(
      """|REQUEST: http://localhost
         |METHOD: POST
         |HEADERS
         |-> Accept: application/json
         |-> Accept-Charset: UTF-8
         |-> X-Test: test
         |EXTRAS
         |Test Extra
         |kotlin.Unit
         |BODY START
         |{"test":"request"}
         |BODY END
      """.trimMargin()
    )
    responseTag.shouldBeEqual("debug-tests-f8e-log")
    responseMessage.shouldBeEqual(
      """|RESPONSE: 200 OK
         |METHOD: POST
         |FROM: http://localhost
         |HEADERS
         |-> Content-Type: application/json
         |-> X-Test: testvalue
         |BODY Content-Type: application/json
         |BODY START
         |{"test":"testdata"}
         |BODY END
      """.trimMargin()
    )
  }

  test("production logs redacted request data") {
    logs.clear()
    http = createClient {
      tag = "prod-tests-f8e-log"
      debug = false
      sanitize("X-Test") { "***${it.last()}" }
    }

    val response = http.shouldNotBeNull().post {
      contentType(ContentType.Application.Json)
      setRedactedBody(TestRequestData("request"))
      header("X-Test", "test")

      withExtras("Test Extra", Unit)
    }

    response.body<TestResponseData>()

    response.call.attributes[F8eCallLogger].awaitClose()

    logs.size.shouldBeEqual(2)
    val (requestTag, requestMessage) = logs[0]
    val (responseTag, responseMessage) = logs[1]
    requestTag.shouldBeEqual("prod-tests-f8e-log")
    requestMessage.shouldBeEqual(
      """|REQUEST: http://localhost
         |METHOD: POST
         |HEADERS
         |-> X-Test: ***t
         |EXTRAS
         |Test Extra
         |kotlin.Unit
         |BODY START
         |TestRequestData(test=██)
         |BODY END
      """.trimMargin()
    )
    responseTag.shouldBeEqual("prod-tests-f8e-log")
    responseMessage.shouldBeEqual(
      """|RESPONSE: 200 OK
         |METHOD: POST
         |FROM: http://localhost
         |HEADERS
         |-> X-Test: ***e
         |BODY Content-Type: application/json
         |BODY START
         |TestResponseData(test=██)
         |BODY END
      """.trimMargin()
    )
  }
})
