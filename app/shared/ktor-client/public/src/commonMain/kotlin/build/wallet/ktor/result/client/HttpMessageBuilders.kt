package build.wallet.ktor.result.client

import build.wallet.platform.data.MimeType
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.contentType

fun HttpMessageBuilder.contentType(mimeType: MimeType) = contentType(mimeType.toContentType())
