package build.wallet.logging

import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlin.text.RegexOption.IGNORE_CASE

@Suppress("StringShouldBeRawString") // Ignore for regex patterns.
object SensitiveDataValidator {
  private val indicators: List<SimpleIndicator> = listOf(
    SimpleIndicator(
      name = "Dev tag",
      matcher = { it.tag == DEV_TAG }
    ),
    SimpleIndicator(
      name = "Bitcoin private key",
      matcher = Regex("[tx]prv\\w{78,112}", IGNORE_CASE).asLogMatcher()
    ),
    SimpleIndicator(
      name = "Bitcoin addresses",
      matcher = Regex("\\b(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,39}\\b", IGNORE_CASE).asLogMatcher()
    ),
    SimpleIndicator(
      name = "Bitcoin transaction ID",
      matcher = Regex("\\b[a-f0-9]{64}\\b", IGNORE_CASE).asLogMatcher()
    ),
    SimpleIndicator(
      name = "BIP-39 phrase",
      // 12 words or more that don't contain common words.
      matcher = Regex("(?:\\b(?!(?:done|expected|finished|cannot|onto|unexpected|while|without|fail|the|and|was|his|with|for|had|not|her|which|from|but|him|she|were|are|their|who|would|said)\\b)[A-Za-z]{3,}\\b(?:[^A-Za-z]|$)){12}").asLogMatcher()
    ),
    SimpleIndicator(
      name = "Recovery Code",
      matcher = { entry ->
        Regex("(?:^|[^\\dA-Za-z])((?:\\d-?){12,}\\d)(?:[^\\dA-Za-z]|$)")
          .findAll(entry.message)
          .flatMap { it.groupValues.drop(1) }
          .any {
            it.replace("-", "").toBigInteger().let { data ->
              // Starts with a preamble digit (1) and version (0):
              data.shr(data.bitLength() - 2).intValue() == 0b1_0
            }
          }
      }
    ),
    SimpleIndicator(
      name = "Common names/aliases",
      matcher = Regex("(alice|bob|aunt|uncle|cousin)", IGNORE_CASE).asLogMatcher()
    )
  )

  /**
   * Returns `true` if log tag or message contains sensitive data.
   * Also `true` if dev tag is used, potentially indicating that dev code has been checked in.
   */
  fun check(entry: LogEntry): SensitiveDataResult {
    val result = indicators.filter { indicator -> indicator.match(entry) }

    return when {
      result.isEmpty() -> SensitiveDataResult.NoneFound
      else -> SensitiveDataResult.Sensitive(
        violations = result
      )
    }
  }

  /**
   * Like [check], but also scans the messages in an associated throwable's cause and
   * suppressed-exception graph. Exception messages can embed the same sensitive material as
   * log messages (e.g. an exception message containing an xprv or txid), and Datadog uploads
   * the full graph, so every message in it must be validated with the same indicators.
   *
   * If either the log entry or throwable chain is sensitive, the result is
   * [SensitiveDataResult.Sensitive] and callers must redact the message/tag *and* drop or
   * replace the throwable before upload.
   */
  fun check(
    entry: LogEntry,
    throwable: Throwable?,
  ): SensitiveDataResult {
    val results = buildList {
      add(check(entry))

      val visited = mutableListOf<Throwable>()
      val pending = mutableListOf<Throwable>()
      throwable?.let(pending::add)
      while (pending.isNotEmpty()) {
        val current = pending.removeAt(pending.lastIndex)
        if (visited.any { it === current }) continue

        visited += current
        current.message?.let { message ->
          add(check(LogEntry(tag = entry.tag, message = message)))
        }
        current.cause?.let(pending::add)
        pending += current.suppressedExceptions
      }
    }
    val violations = results
      .filterIsInstance<SensitiveDataResult.Sensitive>()
      .flatMap { it.violations }
      .distinct()

    return when {
      violations.isEmpty() -> SensitiveDataResult.NoneFound
      else -> SensitiveDataResult.Sensitive(violations = violations)
    }
  }
}
