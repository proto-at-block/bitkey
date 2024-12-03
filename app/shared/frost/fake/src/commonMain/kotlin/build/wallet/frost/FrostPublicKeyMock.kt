package build.wallet.frost

class FrostPublicKeyMock(
  val string: String,
) : PublicKey {
  override fun asString(): String = string
}
