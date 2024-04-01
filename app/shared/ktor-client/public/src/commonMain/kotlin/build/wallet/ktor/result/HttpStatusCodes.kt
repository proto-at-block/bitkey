package build.wallet.ktor.result

import io.ktor.http.HttpStatusCode

val HttpStatusCode.isClientError: Boolean get() = value in 400..499
val HttpStatusCode.isServerError: Boolean get() = value in 500..599
