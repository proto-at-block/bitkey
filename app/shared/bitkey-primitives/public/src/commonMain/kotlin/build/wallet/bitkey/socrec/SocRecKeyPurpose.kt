package build.wallet.bitkey.socrec

import kotlin.reflect.KClass

/**
 * Purpose of each SocRec key, used as unique identifiers for socrec protocol keys in the database.
 *
 * IMPORTANT: Renaming enum values would require a migration.
 */
enum class SocRecKeyPurpose {
  ProtectedCustomerEphemeral,
  ProtectedCustomerIdentity,
  TrustedContactIdentity,
  ;

  companion object {
    fun <T : SocRecKey> fromKeyType(keyClass: KClass<T>): SocRecKeyPurpose =
      when (keyClass) {
        ProtectedCustomerEphemeralKey::class -> ProtectedCustomerEphemeral
        ProtectedCustomerIdentityKey::class -> ProtectedCustomerIdentity
        TrustedContactIdentityKey::class -> TrustedContactIdentity
        else -> error("Unknown SocRecKey type: $keyClass")
      }
  }
}
