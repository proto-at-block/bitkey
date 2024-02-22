package build.wallet.statemachine.partnerships.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.LOADING_TRANSFER_PARTNERS
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.LOADING_TRANSFER_PARTNER_REDIRECT
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.TRANSFER_PARTNERS_LIST
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.TRANSFER_PARTNERS_NOT_FOUND_ERROR
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.TRANSFER_PARTNER_REDIRECT_ERROR
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.partnerships.GetTransferPartnerListService
import build.wallet.f8e.partnerships.GetTransferRedirectService
import build.wallet.f8e.partnerships.PartnerInfo
import build.wallet.f8e.partnerships.RedirectInfo
import build.wallet.f8e.partnerships.RedirectUrlType.DEEPLINK
import build.wallet.f8e.partnerships.RedirectUrlType.WIDGET
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.ktor.result.NetworkingError
import build.wallet.platform.links.AppRestrictions
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.Icon.MediumIconQrCode
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize.MIN40
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.partnerships.PartnerRedirectionMethod.Deeplink
import build.wallet.statemachine.partnerships.PartnerRedirectionMethod.Web
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment.PRIMARY
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class PartnershipsTransferUiStateMachineImpl(
  private val getTransferPartnerListService: GetTransferPartnerListService,
  private val getTransferRedirectService: GetTransferRedirectService,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
) : PartnershipsTransferUiStateMachine {
  @Composable
  override fun model(props: PartnershipsTransferUiProps): SheetModel {
    var state: State by remember { mutableStateOf(State.LoadingPartnershipsTransfer) }

    when (val currentState = state) {
      State.LoadingPartnershipsTransfer -> {
        LaunchedEffect("load-transfer-partners") {
          getTransferPartnerListService
            .getTransferPartners(
              fullAccountId = props.account.accountId,
              f8eEnvironment = props.account.config.f8eEnvironment
            )
            .onSuccess {
              val transferPartners = it.partnerList.toImmutableList()
              state =
                State.ChoosingPartnershipsTransfer(
                  transferPartners = transferPartners,
                  onPartnerSelected = { partner ->
                    state =
                      State.LoadingPartnerRedirect(
                        partnerInfo = partner,
                        transferPartners = transferPartners
                      )
                  }
                )
            }
            .onFailure {
              state =
                State.LoadingPartnershipsTransferFailure(
                  error = it
                )
            }
        }
        return Loading(
          id = LOADING_TRANSFER_PARTNERS,
          onExit = props.onExit
        )
      }
      is State.LoadingPartnershipsTransferFailure ->
        return TransferErrorModel(
          id = TRANSFER_PARTNERS_NOT_FOUND_ERROR,
          errorMessage = "Could not load partners at this time.",
          onBack = props.onBack,
          onExit = props.onExit
        )
      is State.ChoosingPartnershipsTransfer -> {
        when {
          currentState.transferPartners.isEmpty() -> {
            // TODO(W-4119): Optimize null state for when there are no transfer partners
            return TransferErrorModel(
              id = TRANSFER_PARTNERS_NOT_FOUND_ERROR,
              errorMessage = "No partners to display at this time",
              onBack = props.onBack,
              onExit = props.onExit
            )
          }
          else -> {
            return ListTransferPartnersModel(
              id = TRANSFER_PARTNERS_LIST,
              props,
              currentState.transferPartners,
              onPartnerSelected = currentState.onPartnerSelected,
              onAnotherWalletOrExchangeSelected = props.onAnotherWalletOrExchange
            )
          }
        }
      }
      is State.LoadingPartnerRedirect -> {
        LaunchedEffect("load-transfer-partner-redirect-info") {
          appSpendingWalletProvider.getSpendingWallet(props.account.keybox.activeSpendingKeyset)
            .flatMap { it.getNewAddress() }
            .flatMap { address ->
              getTransferRedirectService
                .getTransferRedirect(
                  fullAccountId = props.account.keybox.fullAccountId,
                  f8eEnvironment = props.account.keybox.config.f8eEnvironment,
                  partner = currentState.partnerInfo.partner,
                  address = address
                )
                .onSuccess {
                  state =
                    State.PartnerRedirectInformationLoaded(
                      partnerInfo = currentState.partnerInfo,
                      redirectInfo = it.redirectInfo
                    )
                }
                .onFailure {
                  state =
                    State.LoadingPartnerRedirectFailure(
                      error = it,
                      partnerInfo = currentState.partnerInfo,
                      transferPartners = currentState.transferPartners,
                      rollback = { state = State.LoadingPartnershipsTransfer }
                    )
                }
            }
        }
        return Loading(
          id = LOADING_TRANSFER_PARTNERS,
          onExit = props.onExit
        )
      }
      is State.LoadingPartnerRedirectFailure ->
        return TransferErrorModel(
          id = TRANSFER_PARTNER_REDIRECT_ERROR,
          errorMessage = "Could not redirect to ${currentState.partnerInfo.name}",
          onBack = currentState.rollback,
          onExit = props.onExit
        )

      is State.PartnerRedirectInformationLoaded -> {
        when (currentState.redirectInfo.redirectType) {
          DEEPLINK -> {
            props.onPartnerRedirected(
              Deeplink(
                urlString = currentState.redirectInfo.url,
                appRestrictions =
                  currentState.redirectInfo.appRestrictions?.let {
                    AppRestrictions(
                      packageName = it.packageName,
                      minVersion = it.minVersion
                    )
                  },
                partnerName = currentState.partnerInfo.partner
              )
            )
          }

          WIDGET -> {
            props.onPartnerRedirected(Web(urlString = currentState.redirectInfo.url))
          }
        }
        return Loading(
          id = LOADING_TRANSFER_PARTNER_REDIRECT,
          onExit = props.onExit
        )
      }
    }
  }

  @Composable
  private fun TransferErrorModel(
    id: EventTrackerScreenId,
    errorMessage: String,
    onBack: () -> Unit,
    onExit: () -> Unit,
  ): SheetModel {
    return SheetModel(
      body =
        ErrorFormBodyModel(
          eventTrackerScreenId = id,
          title = "Error",
          subline = errorMessage,
          primaryButton = ButtonDataModel("Got it", isLoading = false, onClick = onBack),
          renderContext = Sheet,
          onBack = onBack
        ),
      dragIndicatorVisible = true,
      size = MIN40,
      onClosed = onExit
    )
  }

  @Composable
  private fun ListTransferPartnersModel(
    id: EventTrackerScreenId,
    props: PartnershipsTransferUiProps,
    partners: ImmutableList<PartnerInfo>,
    onPartnerSelected: (PartnerInfo) -> Unit,
    onAnotherWalletOrExchangeSelected: () -> Unit,
  ): SheetModel {
    val partnerItems =
      partners.map { partner ->
        ListItemModel(
          leadingAccessory =
            IconAccessory(
              model =
                IconModel(
                  iconImage =
                    when (val url = partner.logoUrl) {
                      null -> LocalImage(Bitcoin)
                      else ->
                        UrlImage(
                          url = url,
                          fallbackIcon = Bitcoin
                        )
                    },
                  iconSize = IconSize.Regular
                )
            ),
          title = partner.name,
          onClick = { onPartnerSelected(partner) },
          treatment = PRIMARY
        )
      }
    val anotherWalletOrExchange =
      ListItemModel(
        leadingAccessory =
          IconAccessory(
            model =
              IconModel(
                iconImage = LocalImage(MediumIconQrCode),
                iconSize = IconSize.Regular
              )
          ),
        title = "Another exchange or wallet",
        onClick = { onAnotherWalletOrExchangeSelected() },
        treatment = PRIMARY
      )
    val allItems = partnerItems + anotherWalletOrExchange
    val partnersGroupModel =
      ListGroupModel(
        items = allItems.toImmutableList(),
        style = CARD_ITEM
      )
    return TransferPartnersModel(
      id = id,
      content = ListGroup(listGroupModel = partnersGroupModel),
      onBack = props.onBack,
      onExit = props.onExit
    )
  }

  @Composable
  private fun TransferPartnersModel(
    id: EventTrackerScreenId,
    content: FormMainContentModel,
    onBack: () -> Unit,
    onExit: () -> Unit,
  ): SheetModel {
    return SheetModel(
      body =
        FormBodyModel(
          onBack = onBack,
          toolbar =
            ToolbarModel(
              middleAccessory = ToolbarMiddleAccessoryModel(title = "Select a partner")
            ),
          header = null,
          mainContentList = immutableListOf(content),
          primaryButton = null,
          id = id,
          renderContext = Sheet
        ),
      dragIndicatorVisible = true,
      size = MIN40,
      onClosed = onExit
    )
  }

  @Composable
  private fun Loading(
    id: EventTrackerScreenId,
    onExit: () -> Unit,
  ): SheetModel {
    return SheetModel(
      body =
        FormBodyModel(
          id = id,
          onBack = {},
          toolbar = null,
          header = null,
          mainContentList = immutableListOf(Loader),
          primaryButton = null,
          renderContext = Sheet
        ),
      dragIndicatorVisible = false,
      size = MIN40,
      onClosed = onExit
    )
  }
}

private sealed interface State {
  /**
   * Loading available transfer partners
   */
  data object LoadingPartnershipsTransfer : State

  /**
   * There was a failure loading transfer partners
   */
  data class LoadingPartnershipsTransferFailure(
    val error: NetworkingError,
  ) : State

  /**
   * Transfer partners are loaded and ready to choose from
   */
  data class ChoosingPartnershipsTransfer(
    val transferPartners: ImmutableList<PartnerInfo>,
    val onPartnerSelected: (PartnerInfo) -> Unit,
  ) : State

  /**
   * Loading transfer partner's redirect information
   */
  data class LoadingPartnerRedirect(
    val partnerInfo: PartnerInfo,
    val transferPartners: ImmutableList<PartnerInfo>,
  ) : State

  /**
   * There was a failure loading a transfer partner's redirect info.
   * We carry over the [transferPartners] in the event user wants to go back
   * to the partners list
   */
  data class LoadingPartnerRedirectFailure(
    val error: NetworkingError,
    val partnerInfo: PartnerInfo,
    val transferPartners: ImmutableList<PartnerInfo>,
    val rollback: () -> Unit,
  ) : State

  /**
   * Partner redirect information loaded
   */
  data class PartnerRedirectInformationLoaded(
    val partnerInfo: PartnerInfo,
    val redirectInfo: RedirectInfo,
  ) : State
}
