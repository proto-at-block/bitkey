package build.wallet.statemachine.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.money.formatter.amountDisplayText
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.data.money.convertedOrNull
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList

@BitkeyInject(ActivityScope::class)
class FailedPartnerTransactionUiStateMachineImpl(
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val currencyConverter: CurrencyConverter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
) : FailedPartnerTransactionUiStateMachine {
  @Composable
  override fun model(props: FailedPartnerTransactionProps): ScreenModel {
    return FailedPartnerTransactionBodyModel(
      headerIcon = IconModel(
        iconImage = when (val url = props.transaction.details.partnerInfo.logoUrl) {
          null -> IconImage.LocalImage(Icon.Bitcoin)
          else -> IconImage.UrlImage(url, Icon.Bitcoin)
        },
        iconSize = IconSize.Avatar
      ),
      headline = "There was an issue with your ${props.transaction.details.partnerInfo.name} transaction",
      subline = "Visit ${props.transaction.details.partnerInfo.name} for more information.",
      content = partnershipTransactionFormContent(props.transaction.details),
      buttonModel = props.transaction.details.partnerTransactionUrl?.let {
        ButtonModel(
          text = "Go to ${props.transaction.details.partnerInfo.name}",
          treatment = ButtonModel.Treatment.Primary,
          leadingIcon = Icon.SmallIconArrowUpRight,
          size = ButtonModel.Size.Footer,
          testTag = null,
          onClick = StandardClick {
            inAppBrowserNavigator.open(
              url = it,
              onClose = {}
            )
          }
        )
      },
      onClose = props.onClose
    ).asRootScreen()
  }

  @Composable
  private fun partnershipTransactionFormContent(
    transaction: PartnershipTransaction,
  ): ImmutableList<FormMainContentModel> {
    val totalAmount = transaction.cryptoAmount?.let { BitcoinMoney.btc(it) }

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val totalFiatAmount = totalAmount?.let {
      convertedOrNull(
        converter = currencyConverter,
        fromAmount = totalAmount,
        toCurrency = fiatCurrency,
        atTime = transaction.created
      ) as FiatMoney?
    }

    val totalAmountTexts = totalAmount?.let {
      moneyDisplayFormatter.amountDisplayText(
        bitcoinAmount = totalAmount,
        fiatAmount = totalFiatAmount
      )
    }

    return immutableListOfNotNull(
      FormMainContentModel.Divider,
      totalAmountTexts?.let {
        DataList(
          items = immutableListOf(
            Data(
              title = "Amount",
              sideText = totalAmountTexts.primaryAmountText,
              secondarySideText = totalAmountTexts.secondaryAmountText
            )
          )
        )
      }
    )
  }
}

data class FailedPartnerTransactionBodyModel(
  val headerIcon: IconModel,
  val headline: String,
  val subline: String,
  val content: ImmutableList<FormMainContentModel>,
  val buttonModel: ButtonModel?,
  val onClose: () -> Unit,
) : FormBodyModel(
    id = MoneyHomeEventTrackerScreenId.FAILED_PARTNER_TRANSACTION,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory { onClose() }
    ),
    header = FormHeaderModel(
      iconModel = headerIcon,
      headline = headline,
      subline = subline
    ),
    onBack = onClose,
    mainContentList = content,
    primaryButton = buttonModel,
    renderContext = RenderContext.Screen,
    enableComposeRendering = true
  )
