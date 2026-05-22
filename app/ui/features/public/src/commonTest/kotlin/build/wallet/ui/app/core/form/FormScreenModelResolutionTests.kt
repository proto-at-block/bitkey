package build.wallet.ui.app.core.form

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class FormScreenModelResolutionTests : FunSpec({
  test("falls back to the legacy header when dsv2 header fallback is enabled") {
    val resolvedModel = resolveFormScreenModel(
      model = TestFormBodyModel(
        header = FormHeaderModel(headline = "Legacy headline"),
        designSystemV2Model = FormDesignSystemV2Model(header = null)
      )
    )

    resolvedModel.headerModel?.headline.shouldBe("Legacy headline")
  }

  test("can suppress the legacy header when dsv2 header fallback is disabled") {
    val resolvedModel = resolveFormScreenModel(
      model = TestFormBodyModel(
        header = FormHeaderModel(headline = "Legacy headline"),
        designSystemV2Model = FormDesignSystemV2Model(
          header = null,
          useLegacyHeaderFallback = false
        )
      )
    )

    resolvedModel.headerModel.shouldBeNull()
  }

  test("can suppress the legacy toolbar when dsv2 toolbar fallback is disabled") {
    val resolvedModel = resolveFormScreenModel(
      model = TestFormBodyModel(
        header = FormHeaderModel(headline = "Legacy headline"),
        toolbar = TestToolbarModel,
        designSystemV2Model = FormDesignSystemV2Model(
          toolbar = null,
          useLegacyToolbarFallback = false
        )
      )
    )

    resolvedModel.toolbarModel.shouldBeNull()
  }

  test("can suppress the legacy secondary button when dsv2 secondary button fallback is disabled") {
    val resolvedModel = resolveFormScreenModel(
      model = TestFormBodyModel(
        header = null,
        secondaryButton = TestSecondaryButton,
        designSystemV2Model = FormDesignSystemV2Model(
          secondaryButton = null,
          useLegacySecondaryButtonFallback = false
        )
      )
    )

    resolvedModel.secondaryButton.shouldBeNull()
  }

  test("can suppress the legacy primary button when dsv2 primary button fallback is disabled") {
    val resolvedModel = resolveFormScreenModel(
      model = TestFormBodyModel(
        header = null,
        primaryButton = TestPrimaryButton,
        designSystemV2Model = FormDesignSystemV2Model(
          primaryButton = null,
          useLegacyPrimaryButtonFallback = false
        )
      )
    )

    resolvedModel.primaryButton.shouldBeNull()
  }

  test("propagates dsv2 layout options") {
    val resolvedModel = resolveFormScreenModel(
      model = TestFormBodyModel(
        header = null,
        designSystemV2Model = FormDesignSystemV2Model(
          useDesignSystemV2ScreenLayout = true,
          scrollable = false,
          mainContentVerticalAlignment = FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER
        )
      )
    )

    resolvedModel.designSystemV2UseLayout.shouldBe(true)
    resolvedModel.designSystemV2Scrollable.shouldBe(false)
    resolvedModel.designSystemV2MainContentAlignment.shouldBe(FormScreenContentVerticalAlignment.Center)
  }

})

private data class TestFormBodyModel(
  override val header: FormHeaderModel?,
  override val toolbar: build.wallet.ui.model.toolbar.ToolbarModel? = null,
  override val primaryButton: ButtonModel? = null,
  override val secondaryButton: ButtonModel? = null,
  override val designSystemV2Model: FormDesignSystemV2Model? = null,
) : FormBodyModel(
    id = null,
    onBack = {},
    toolbar = toolbar,
    header = header,
    primaryButton = primaryButton,
    secondaryButton = secondaryButton
  )

private val TestToolbarModel = build.wallet.ui.model.toolbar.ToolbarModel()
private val TestPrimaryButton =
  ButtonModel(
    text = "Primary",
    size = ButtonModel.Size.Footer,
    onClick = StandardClick {}
  )
private val TestSecondaryButton =
  ButtonModel(
    text = "Secondary",
    size = ButtonModel.Size.Footer,
    onClick = StandardClick {}
  )
