package build.wallet.ui.app.model

import build.wallet.ui.model.Model
import build.wallet.ui.model.TypedUiModelMap
import build.wallet.ui.model.UiModel
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class TypedUiModelMapTests : FunSpec({

  val fooUiModel = UiModel<FooModel> { }
  val barUiModel = UiModel<BarModel> { }

  test("get UiModel by type") {
    val uiModelMap = TypedUiModelMap(fooUiModel, barUiModel)

    uiModelMap.getUiModelFor(FooModel::class).shouldBe(fooUiModel)
  }

  test("cannot find a UiModel by type") {
    val uiModelMap = TypedUiModelMap(fooUiModel)

    uiModelMap.getUiModelFor(BarModel::class).shouldBeNull()
  }

  test("UiModels with duplicated keys are not allowed") {
    shouldThrow<IllegalStateException> {
      TypedUiModelMap(fooUiModel, barUiModel, fooUiModel)
    }
  }

  test("empty map is allowed") {
    TypedUiModelMap()
  }
})

private object FooModel : Model()

private object BarModel : Model()
