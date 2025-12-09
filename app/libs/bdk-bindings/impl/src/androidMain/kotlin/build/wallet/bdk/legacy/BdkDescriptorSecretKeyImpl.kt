package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkDerivationPath
import build.wallet.bdk.bindings.BdkDescriptorPublicKey
import build.wallet.bdk.bindings.BdkDescriptorSecretKey
import build.wallet.bdk.bindings.BdkResult
import build.wallet.toByteArray
import org.bitcoindevkit.DerivationPath
import org.bitcoindevkit.DescriptorSecretKey as FfiDescriptorSecretKey

internal class BdkDescriptorSecretKeyImpl(
  val ffiDescriptorSecretKey: FfiDescriptorSecretKey,
) : BdkDescriptorSecretKey {
  override fun derive(path: BdkDerivationPath): BdkResult<BdkDescriptorSecretKeyImpl> {
    return runCatchingBdkError {
      BdkDescriptorSecretKeyImpl(
        ffiDescriptorSecretKey = ffiDescriptorSecretKey.derive(DerivationPath(path = path.path))
      )
    }
  }

  override fun extend(path: BdkDerivationPath): BdkResult<BdkDescriptorSecretKeyImpl> {
    return runCatchingBdkError {
      BdkDescriptorSecretKeyImpl(
        ffiDescriptorSecretKey = ffiDescriptorSecretKey.extend(DerivationPath(path = path.path))
      )
    }
  }

  override fun asPublic(): BdkDescriptorPublicKey {
    return BdkDescriptorPublicKeyImpl(ffiDescriptorSecretKey.asPublic())
  }

  // Returns an extended private key as a string (`xprv...`)
  override fun raw(): String = ffiDescriptorSecretKey.asString()

  // Returns byte array representation of the private key
  override fun secretBytes(): ByteArray {
    return ffiDescriptorSecretKey.secretBytes().toByteArray()
  }
}
