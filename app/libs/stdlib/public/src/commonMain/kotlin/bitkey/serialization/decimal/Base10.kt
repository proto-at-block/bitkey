package bitkey.serialization.decimal

import build.wallet.catchingResult
import com.github.michaelbull.result.Result
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import com.ionspin.kotlin.bignum.integer.toBigInteger
import okio.ByteString
import okio.ByteString.Companion.toByteString

object Base10 {
  fun encode(input: ByteString): Result<String, Throwable> =
    catchingResult {
      val bigInteger = BigInteger.fromByteArray(input.toByteArray(), Sign.POSITIVE)
      bigInteger.toString(10)
    }

  fun decode(input: String): Result<ByteString, Throwable> =
    catchingResult {
      val bigInteger = input.toBigInteger(10)
      val byteArray = bigInteger.toByteArray()
      byteArray.toByteString()
    }
}
