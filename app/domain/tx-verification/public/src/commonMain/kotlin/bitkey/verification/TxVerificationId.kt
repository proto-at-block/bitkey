package bitkey.verification

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Unique identifier for a transaction verification.
 *
 * This is used to identify the state of a transaction when looking up the
 * verification status with the server.
 */
@JvmInline
@Serializable
value class TxVerificationId(
  val value: String,
)
