package build.wallet.f8e.logging

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel

/**
 * [LoggedContent] wraps the [OutgoingContent] for an HTTP request and
 * substitutes the [OutgoingContent.ReadChannelContent.readFrom] [ByteReadChannel]
 * with a copy of the original request body.
 */
internal class LoggedContent(
  private val originalContent: OutgoingContent,
  private val channel: ByteReadChannel,
) : OutgoingContent.ReadChannelContent() {
  override val contentType: ContentType? = originalContent.contentType
  override val contentLength: Long? = originalContent.contentLength
  override val status: HttpStatusCode? = originalContent.status
  override val headers: Headers = originalContent.headers

  override fun <T : Any> getProperty(key: AttributeKey<T>): T? = originalContent.getProperty(key)

  override fun <T : Any> setProperty(
    key: AttributeKey<T>,
    value: T?,
  ) = originalContent.setProperty(key, value)

  override fun readFrom(): ByteReadChannel = channel
}
