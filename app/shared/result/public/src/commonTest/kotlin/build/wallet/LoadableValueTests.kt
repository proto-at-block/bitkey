package build.wallet

import app.cash.turbine.test
import build.wallet.LoadableValue.InitialLoading
import build.wallet.LoadableValue.LoadedValue
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LoadableValueTests : FunSpec({

  test("map InitialLoading") {
    val value: LoadableValue<Int> = InitialLoading

    value.map { "$it" }.shouldBe(InitialLoading)
  }

  test("map LoadedValue") {
    val value: LoadableValue<Int> = LoadedValue(42)

    value.map { "$it" }.shouldBe(LoadedValue("42"))
  }

  test("wrap flow values into LoadableValue (emit InitialLoading on start)") {
    val values: Flow<Result<Int, String>> =
      flow {
        emit(Ok(1))
        emit(Ok(2))
        emit(Err("oops"))
      }

    values.asLoadableValue().test {
      awaitItem().shouldBe(Ok(InitialLoading))
      awaitItem().shouldBe(Ok(LoadedValue(1)))
      awaitItem().shouldBe(Ok(LoadedValue(2)))
      awaitItem().shouldBe(Err("oops"))
      awaitComplete()
    }
  }

  test("safely unwrap loaded value (ignoring InitialLoading)") {
    val loadableValues: Flow<Result<LoadableValue<Int>, String>> =
      flow {
        emit(Ok(InitialLoading))
        emit(Ok(LoadedValue(1)))
        emit(Ok(LoadedValue(2)))
        emit(Err("oops"))
      }

    loadableValues.unwrapLoadedValue().test {
      awaitItem().shouldBe(Ok(1))
      awaitItem().shouldBe(Ok(2))
      awaitItem().shouldBe(Err("oops"))
      awaitComplete()
    }
  }

  context("isLoaded extension function") {
    test("is initial loading") {
      val loadingValue: LoadableValue<Int> = InitialLoading
      loadingValue.isLoaded().shouldBeFalse()
    }

    test("is loaded") {
      val loadedValue: LoadableValue<Int> = LoadedValue(37)
      loadedValue.isLoaded().shouldBeTrue()
    }
  }
})
