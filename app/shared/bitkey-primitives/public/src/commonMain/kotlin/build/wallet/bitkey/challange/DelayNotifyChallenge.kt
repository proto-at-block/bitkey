package build.wallet.bitkey.challange

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.crypto.PublicKey
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import kotlin.jvm.JvmInline

/**
 * A challenge to be signed by app or hardware in order to authorize a
 * delay/notify action for a particular set of keys.
 */
@JvmInline
value class DelayNotifyChallenge(
  val data: String,
) {
  fun asByteString(): ByteString = data.encodeUtf8()

  enum class Type(val key: String) {
    RECOVERY("CompleteDelayNotify"),
    INHERITANCE("LockInheritanceClaim"),
  }

  companion object {
    fun fromParts(
      type: Type,
      app: PublicKey<AppGlobalAuthKey>,
      recovery: PublicKey<AppRecoveryAuthKey>?,
      hw: HwAuthPublicKey,
    ): DelayNotifyChallenge {
      return DelayNotifyChallenge("${type.key}${hw.pubKey.value}${app.value}${recovery?.value ?: ""}")
    }

    fun fromKeybox(
      type: Type,
      keybox: Keybox,
    ): DelayNotifyChallenge {
      return fromParts(
        type = type,
        app = keybox.activeAppKeyBundle.authKey,
        recovery = keybox.activeAppKeyBundle.recoveryAuthKey,
        hw = keybox.activeHwKeyBundle.authKey
      )
    }
  }
}
