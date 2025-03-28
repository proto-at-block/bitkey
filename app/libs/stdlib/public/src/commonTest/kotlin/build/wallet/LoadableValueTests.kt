package build.wallet

import app.cash.turbine.test
import build.wallet.LoadableValue.InitialLoading
import build.wallet.LoadableValue.LoadedValue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

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
    val values: Flow<Int> = flowOf(1, 2)

    values.asLoadableValue().test {
      awaitItem().shouldBe(InitialLoading)
      awaitItem().shouldBe(LoadedValue(1))
      awaitItem().shouldBe(LoadedValue(2))
      awaitComplete()
    }
  }

  test("safely unwrap loaded value (ignoring InitialLoading)") {
    val loadableValues: Flow<LoadableValue<Int>> =
      flow {
        emit(InitialLoading)
        emit(LoadedValue(1))
        emit(LoadedValue(2))
      }

    loadableValues.mapLoadedValue().test {
      awaitItem().shouldBe(1)
      awaitItem().shouldBe(2)
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
