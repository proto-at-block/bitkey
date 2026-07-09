package build.wallet.bitkey.hardware

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Device-identity-key signature over a derived [HwSpendingPublicKey], returned by firmware
 * as part of hardware attestation ("HWV1").
 */
@JvmInline
@Serializable
value class HwSpendingKeyAttestationSignature(val value: String)
