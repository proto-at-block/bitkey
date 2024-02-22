package build.wallet.crypto

import build.wallet.core.Spake2Context
import okio.ByteString
import okio.ByteString.Companion.toByteString

class Spake2Impl(role: Spake2Role, myName: String, theirName: String) : Spake2 {
  private val ctx = Spake2Context(role.toCore(), myName, theirName)

  override fun generateMsg(password: ByteString): ByteString {
    return ctx.generateMsg(password.toByteArray()).toByteString()
  }

  override fun processMsg(
    theirMsg: ByteString,
    aad: ByteString?,
  ): Spake2Keys {
    val result = ctx.processMsg(theirMsg.toByteArray(), aad?.toByteArray())
    return Spake2Keys(
      result.aliceEncryptionKey.toByteString(),
      result.bobEncryptionKey.toByteString(),
      result.aliceConfKey.toByteString(),
      result.bobConfKey.toByteString()
    )
  }

  override fun generateKeyConfMsg(keys: Spake2Keys): ByteString {
    return ctx.generateKeyConfMsg(keys.toCore()).toByteString()
  }

  override fun processKeyConfMsg(
    receivedMac: ByteString,
    keys: Spake2Keys,
  ) {
    return ctx.processKeyConfMsg(receivedMac.toByteArray(), keys.toCore())
  }
}

fun Spake2Role.toCore(): build.wallet.core.Spake2Role {
  return when (this) {
    Spake2Role.Alice -> build.wallet.core.Spake2Role.ALICE
    Spake2Role.Bob -> build.wallet.core.Spake2Role.BOB
  }
}

fun Spake2Keys.toCore(): build.wallet.core.Spake2Keys {
  return build.wallet.core.Spake2Keys(
    aliceEncryptionKey.toByteArray(),
    bobEncryptionKey.toByteArray(),
    aliceConfKey.toByteArray(),
    bobConfKey.toByteArray()
  )
}
