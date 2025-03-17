package build.wallet

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ResultsTests : FunSpec({
  val testError = Error("test error")

  test("mapIfNotNull") {
    val result: Result<String?, Error> = Ok("test")
    val nullResult: Result<String?, Error> = Ok(null)
    val errorResult: Result<String?, Error> = Err(testError)

    result.mapIfNotNull { 123 }.shouldBe(Ok(123))
    nullResult.mapIfNotNull { 123 }.shouldBe(Ok(null))
    errorResult.mapIfNotNull { 123 }.shouldBe(Err(testError))
  }

  test("flatMapIfNotNull") {
    val result: Result<String?, Error> = Ok("test")
    val nullResult: Result<String?, Error> = Ok(null)
    val errorResult: Result<String?, Error> = Err(testError)

    result.flatMapIfNotNull { Ok(123) }.shouldBe(Ok(123))
    nullResult.flatMapIfNotNull { Ok(123) }.shouldBe(Ok(null))
    errorResult.flatMapIfNotNull { Ok(123) }.shouldBe(Err(testError))
  }
})
