package build.wallet.ktor.result.client

import build.wallet.platform.data.MimeType
import io.ktor.http.ContentType

internal fun MimeType.toContentType(): ContentType = ContentType.parse(name)
