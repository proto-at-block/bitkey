package build.wallet.statemachine.dev.cloud

import bitkey.serialization.json.decodeFromStringResult
import bitkey.serialization.json.encodeToStringResult
import com.github.michaelbull.result.getOrElse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Formatting helpers for rendering cloud backup values in debug UI.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object CloudBackupViewerFormatter {
  private const val PREVIEW_MAX_CHARS = 140

  private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  fun prettyValue(raw: String): String {
    val jsonElement = prettyJson.decodeFromStringResult<JsonElement>(raw)
      .getOrElse { return raw }

    return prettyJson.encodeToStringResult(jsonElement)
      .getOrElse { raw }
  }

  fun previewValue(raw: String): String {
    val compact = prettyValue(raw)
      .replace(Regex("\\s+"), " ")
      .trim()

    return if (compact.length <= PREVIEW_MAX_CHARS) {
      compact
    } else {
      compact.take(PREVIEW_MAX_CHARS - 1) + "…"
    }
  }
}
