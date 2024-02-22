package build.wallet.time

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDate

/**
 * Interop helper to create native [NSDate] from Kotlin [Instant].
 */
fun Instant.toDate(): NSDate = toNSDate()

/**
 * Interop helper to create Kotlin [Instant] from native [NSDate].
 */
fun Instant(date: NSDate): Instant = date.toKotlinInstant()
