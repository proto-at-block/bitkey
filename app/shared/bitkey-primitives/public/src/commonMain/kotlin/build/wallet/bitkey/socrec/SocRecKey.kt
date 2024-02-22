package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey

/**
 * Represents an asymmetric key used within the SocRec verification protocol.
 */
sealed interface SocRecKey : AppKey {
  val key: AppKey
}
