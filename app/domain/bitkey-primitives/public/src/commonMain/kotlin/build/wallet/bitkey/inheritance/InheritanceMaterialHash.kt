package build.wallet.bitkey.inheritance

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import kotlin.jvm.JvmInline

@JvmInline
value class InheritanceMaterialHash(val value: Int)

data class InheritanceMaterialHashData(
  val networkType: BitcoinNetworkType,
  val spendingKey: AppSpendingPublicKey,
  val contacts: List<EndorsedTrustedContact>,
) {
  val inheritanceMaterialHash = InheritanceMaterialHash(hashCode())
}
