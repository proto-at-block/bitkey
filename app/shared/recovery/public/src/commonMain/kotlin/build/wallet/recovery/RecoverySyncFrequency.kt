package build.wallet.recovery

import kotlin.jvm.JvmInline
import kotlin.time.Duration

/**
 * Determine show frequency recovery should be synced by [RecoverySyncer].
 */
@JvmInline
value class RecoverySyncFrequency(val value: Duration)
