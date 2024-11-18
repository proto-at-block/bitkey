package build.wallet.bitkey.inheritance

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The descriptor keyset for a benefactor.
 *
 * This keyset is provided to the beneficiary when a claim is locked, allowing
 * the mobile client to generate a transaction on the benefactor's behalf.
 */
@JvmInline
@Serializable
value class BenefactorDescriptorKeyset(val value: String)
