package build.wallet.bitkey.inheritance

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.crypto.PublicKey
import kotlin.jvm.JvmInline

@JvmInline
value class InheritanceMaterialHash(val value: Int)

/**
 * Represents a contact for inheritance material hashing purposes.
 * Using a data class ensures order-independent hashing when used in a Set.
 */
data class InheritanceContactHashData(
  val id: RelationshipId,
  val identityKey: PublicKey<DelegatedDecryptionKey>,
)

/**
 * Data used to compute inheritance material hash.
 *
 * This includes all fields that affect the encrypted inheritance material packages:
 * - [networkType]: Bitcoin network (mainnet, signet, etc.)
 * - [spendingKey]: App spending key (for the encrypted keyset)
 * - [hardwareKey]: Hardware spending key (for the descriptor)
 * - [f8eSpendingKeyset]: Server spending keyset (for descriptor and optional root xpub)
 * - [contacts]: Beneficiary contacts with their encryption keys
 *
 * Changes to any of these will trigger re-upload of inheritance packages.
 */
data class InheritanceMaterialHashData(
  val networkType: BitcoinNetworkType,
  val spendingKey: AppSpendingPublicKey,
  val hardwareKey: HwSpendingPublicKey,
  val f8eSpendingKeyset: F8eSpendingKeyset,
  val contacts: Set<InheritanceContactHashData>,
) {
  val inheritanceMaterialHash = InheritanceMaterialHash(hashCode())
}
