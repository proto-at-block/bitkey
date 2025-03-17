package build.wallet.bitkey.relationships

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class TrustedContactRole(val key: String) {
  companion object {
    val SocialRecoveryContact = TrustedContactRole("SOCIAL_RECOVERY_CONTACT")
    val Beneficiary = TrustedContactRole("BENEFICIARY")
  }
}
