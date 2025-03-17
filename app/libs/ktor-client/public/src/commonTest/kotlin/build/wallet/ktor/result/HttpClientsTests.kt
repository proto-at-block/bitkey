package build.wallet.ktor.result

import build.wallet.ktor.test.HttpResponseMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException

class HttpClientsTests : FunSpec({
  val client = HttpClient()

  test("success responses") {
    successStatusCodes.onEach { statusCode ->
      val response = HttpResponseMock(statusCode)

      client
        .catching { response }
        .shouldBe(Ok(response))
    }
  }

  test("client errors") {
    clientErrorCodes.onEach { statusCode ->
      val response = HttpResponseMock(statusCode)

      client
        .catching { response }
        .shouldBe(Err(HttpError.ClientError(response)))
    }
  }

  test("server errors") {
    serverErrorCodes.onEach { statusCode ->
      val response = HttpResponseMock(statusCode)

      client
        .catching { response }
        .shouldBe(Err(HttpError.ServerError(response)))
    }
  }

  test("I/O error") {
    val exception = IOException("oops")

    client
      .catching { throw exception }
      .shouldBe(Err(HttpError.NetworkError(exception)))
  }

  test("unhandled exception") {
    val exception = Exception()
    client
      .catching { throw exception }
      .shouldBe(Err(HttpError.UnhandledException(exception)))
  }

  test("unhandled errors") {
    unhandledErrors.onEach { statusCode ->
      val response = HttpResponseMock(statusCode)

      client
        .catching { response }
        .shouldBe(Err(HttpError.UnhandledError(response)))
    }
  }
})

private val successStatusCodes =
  HttpStatusCode.allStatusCodes
    .filter { it.value in 200..299 }

private val clientErrorCodes =
  HttpStatusCode.allStatusCodes
    .filter { it.value in 400..499 }

private val serverErrorCodes =
  HttpStatusCode.allStatusCodes
    .filter { it.value in 500..599 }

private val unhandledErrors =
  HttpStatusCode.allStatusCodes
    .minus(successStatusCodes)
    .minus(clientErrorCodes)
    .minus(serverErrorCodes)
