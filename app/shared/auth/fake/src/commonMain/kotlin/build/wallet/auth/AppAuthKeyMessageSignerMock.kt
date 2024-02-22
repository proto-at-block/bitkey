package build.wallet.auth

import build.wallet.bitkey.app.AppAuthPublicKey
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import okio.ByteString

class AppAuthKeyMessageSignerMock : AppAuthKeyMessageSigner {
  var result: Result<String, Throwable> = Err(Throwable())

  override suspend fun signMessage(
    publicKey: AppAuthPublicKey,
    message: ByteString,
  ): Result<String, Throwable> = result

  fun reset() {
    result = Err(Throwable())
  }
}
