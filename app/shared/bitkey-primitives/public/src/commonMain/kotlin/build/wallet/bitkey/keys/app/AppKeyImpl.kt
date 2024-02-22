package build.wallet.bitkey.keys.app

import build.wallet.crypto.CurveType
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey

/**
 * Represents an asymmetric key used within the SocRec verification protocol.
 */
data class AppKeyImpl(
  override val curveType: CurveType,
  override val publicKey: PublicKey,
  /**
   * The private key material may not always be present because it's unnecessary
   * in most places where we use AppKeys. Sometimes, we may not have the private key in the
   * app at all, if this asymmetric key is owned by another party, e.g. the TrustedContact.
   */
  val privateKey: PrivateKey?,
) : AppKey
