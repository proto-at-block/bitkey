package build.wallet.crypto

import build.wallet.rust.core.Spake2Context
import okio.ByteString
import okio.ByteString.Companion.toByteString
import build.wallet.rust.core.Spake2Keys as CoreSpake2Keys
import build.wallet.rust.core.Spake2Role as CoreSpake2Role

class Spake2Impl : Spake2 {
  override fun generateKeyPair(spake2Params: Spake2Params): Spake2KeyPair {
    val ctx = Spake2Context(spake2Params.role.toCore(), spake2Params.myName, spake2Params.theirName)
    val publicKey = ctx.generateMsg(spake2Params.password.toByteArray()).toByteString()
    val privateKey = ctx.readPrivateKey().toByteString()
    return Spake2KeyPair(Spake2PrivateKey(privateKey), Spake2PublicKey(publicKey))
  }

  override fun processTheirPublicKey(
    spake2Params: Spake2Params,
    myKeyPair: Spake2KeyPair,
    theirPublicKey: Spake2PublicKey,
    aad: ByteString?,
  ): Spake2SymmetricKeys {
    val ctx = Spake2Context(spake2Params.role.toCore(), spake2Params.myName, spake2Params.theirName)
    // Write password to context
    ctx.generateMsg(spake2Params.password.toByteArray())
    // Write key pair to context
    ctx.writeKeyPair(
      myKeyPair.privateKey.bytes.toByteArray(),
      myKeyPair.publicKey.bytes.toByteArray()
    )
    val result = ctx.processMsg(
      theirPublicKey.bytes.toByteArray(),
      aad?.toByteArray()
    )
    return Spake2SymmetricKeys(
      result.aliceEncryptionKey.toByteString(),
      result.bobEncryptionKey.toByteString(),
      result.aliceConfKey.toByteString(),
      result.bobConfKey.toByteString()
    )
  }

  override fun generateKeyConfMsg(
    role: Spake2Role,
    keys: Spake2SymmetricKeys,
  ): ByteString {
    // Name strings aren't used by `generateKeyConfMsg`, so we can set them to alice and bob rather
    // than requiring them as parameters.
    val ctx = Spake2Context(role.toCore(), "alice", "bob")
    return ctx.generateKeyConfMsg(keys.toCore()).toByteString()
  }

  override fun processKeyConfMsg(
    role: Spake2Role,
    receivedKeyConfMsg: ByteString,
    keys: Spake2SymmetricKeys,
  ) {
    // Name strings aren't used by `processKeyConfMsg`, so we can set them to alice and bob rather
    // than requiring them as parameters.
    val ctx = Spake2Context(role.toCore(), "alice", "bob")
    return ctx.processKeyConfMsg(receivedKeyConfMsg.toByteArray(), keys.toCore())
  }
}

fun Spake2Role.toCore(): CoreSpake2Role {
  return when (this) {
    Spake2Role.Alice -> CoreSpake2Role.ALICE
    Spake2Role.Bob -> CoreSpake2Role.BOB
  }
}

fun Spake2SymmetricKeys.toCore(): CoreSpake2Keys {
  return CoreSpake2Keys(
    aliceEncryptionKey.toByteArray(),
    bobEncryptionKey.toByteArray(),
    aliceConfKey.toByteArray(),
    bobConfKey.toByteArray()
  )
}
