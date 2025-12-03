package build.wallet.auth

import build.wallet.bitkey.app.AppAuthKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

class AppAuthKeyMessageSignerMock : AppAuthKeyMessageSigner {
  var result: Result<String, Throwable> = Ok("mock-signature-hex")

  override suspend fun <T> signMessage(
    publicKey: PublicKey<T>,
    message: ByteString,
  ): Result<String, Throwable> where T : AppAuthKey, T : CurveType.Secp256K1 = result

  fun reset() {
    result = Ok("mock-signature-hex")
  }
}
