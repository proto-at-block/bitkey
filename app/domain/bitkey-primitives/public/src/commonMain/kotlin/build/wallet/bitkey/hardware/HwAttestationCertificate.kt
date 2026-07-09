package build.wallet.bitkey.hardware

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Base64-encoded DER certificate returned by firmware as part of the hardware
 * attestation certificate chain.
 */
@JvmInline
@Serializable
value class HwAttestationCertificate(val value: String)
