package build.wallet.f8e.error

import build.wallet.f8e.error.code.F8eClientErrorCode
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import io.ktor.http.HttpStatusCode.Companion.NotFound

fun <T : F8eClientErrorCode> SpecificClientErrorMock(errorCode: T) =
  F8eError.SpecificClientError(
    error = HttpError.ClientError(HttpResponseMock(NotFound)),
    errorCode = errorCode
  )
