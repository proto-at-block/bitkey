/*
Originally from: https://github.com/komputing/KBase58

MIT License

Copyright (c) 2019 Komputing

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package build.wallet.emergencyexitkit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe

class Base58Test : FunSpec({
  fun String.hexString(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
      .map { it.toInt(16).toByte() }
      .toByteArray()
  }

  // Tests from https://github.com/bitcoin/bitcoin/blob/master/src/test/data/base58_encode_decode.json
  val testVectors =
    mapOf(
      "" to "",
      "61" to "2g",
      "626262" to "a3gV",
      "636363" to "aPEr",
      "73696d706c792061206c6f6e6720737472696e67" to "2cFupjhnEsSn59qHXstmK2ffpLv2",
      "00eb15231dfceb60925886b67d065299925915aeb172c06647" to "1NS17iag9jJgTHD1VXjvLCEnZuQ3rJDE9L",
      "516b6fcd0f" to "ABnLTmg",
      "bf4f89001e670274dd" to "3SEo3LWLoPntC",
      "572e4794" to "3EFU7m",
      "ecac89cad93923c02321" to "EJDM8drfXA6uyA",
      "10c8511e" to "Rt5zm",
      "00000000000000000000" to "1111111111"
    )

  test("Encoding to base58") {
    testVectors.forEach {
      it.key.hexString().encodeToBase58String()
        .shouldBeEqual(it.value)
    }
  }

  test("Decoding from base58") {
    testVectors.forEach {
      it.value.decodeBase58()
        .shouldBe(it.key.hexString())
    }
  }
})
