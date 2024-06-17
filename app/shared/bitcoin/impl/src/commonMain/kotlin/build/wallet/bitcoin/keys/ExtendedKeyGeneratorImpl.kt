package build.wallet.bitcoin.keys

import build.wallet.bdk.bindings.BdkDerivationPath
import build.wallet.bdk.bindings.BdkDescriptorSecretKeyGenerator
import build.wallet.bdk.bindings.BdkMnemonicGenerator
import build.wallet.bdk.bindings.BdkMnemonicWordCount.WORDS_24
import build.wallet.bdk.bindings.generateMnemonic
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.bdk.bdkNetwork
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

class ExtendedKeyGeneratorImpl(
  private val bdkMnemonicGenerator: BdkMnemonicGenerator,
  private val bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator,
) : ExtendedKeyGenerator {
  override suspend fun generate(network: BitcoinNetworkType): Result<DescriptorKeypair, Error> =
    coroutineBinding {
      val bdkMnemonic = bdkMnemonicGenerator.generateMnemonic(WORDS_24)
      val bdkDescriptorSecretKey =
        bdkDescriptorSecretKeyGenerator.generate(network.bdkNetwork, bdkMnemonic)
      val mnemonic = bdkMnemonic.words

      val coinType = if (network == BITCOIN) "0" else "1"
      val derivationPath = "m/84'/$coinType'/0'"
      val derivedKey =
        bdkDescriptorSecretKey
          .derive(BdkDerivationPath(derivationPath)).result
          .bind()

      val dpub = derivedKey.asPublic().raw()
      val xprv = derivedKey.raw()

      DescriptorKeypair(
        publicKey = DescriptorPublicKey(dpub),
        privateKey = ExtendedPrivateKey(xprv, mnemonic)
      )
    }
}
