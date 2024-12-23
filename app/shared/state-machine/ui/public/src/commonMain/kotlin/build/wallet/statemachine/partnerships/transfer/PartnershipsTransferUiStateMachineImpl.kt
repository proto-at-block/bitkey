package build.wallet.statemachine.partnerships.transfer

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.*
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.partnerships.GetTransferPartnerListF8eClient
import build.wallet.f8e.partnerships.GetTransferRedirectF8eClient
import build.wallet.f8e.partnerships.RedirectInfo
import build.wallet.f8e.partnerships.RedirectUrlType.DEEPLINK
import build.wallet.f8e.partnerships.RedirectUrlType.WIDGET
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.logError
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnerRedirectionMethod.Deeplink
import build.wallet.partnerships.PartnerRedirectionMethod.Web
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.partnerships.PartnershipTransactionType
import build.wallet.partnerships.PartnershipTransactionsService
import build.wallet.platform.links.AppRestrictions
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize.MIN40
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment.PRIMARY
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay

@BitkeyInject(ActivityScope::class)
class PartnershipsTransferUiStateMachineImpl(
  private val getTransferPartnerListF8eClient: GetTransferPartnerListF8eClient,
  private val getTransferRedirectF8eClient: GetTransferRedirectF8eClient,
  private val partnershipTransactionsService: PartnershipTransactionsService,
  private val eventTracker: EventTracker,
  private val bitcoinAddressService: BitcoinAddressService,
) : PartnershipsTransferUiStateMachine {
  @Composable
  override fun model(props: PartnershipsTransferUiProps): SheetModel {
    var state: State by remember { mutableStateOf(State.LoadingPartnershipsTransfer) }

    when (val currentState = state) {
      State.LoadingPartnershipsTransfer -> {
        LaunchedEffect("load-transfer-partners") {
          getTransferPartnerListF8eClient
            .getTransferPartners(
              fullAccountId = props.keybox.fullAccountId,
              f8eEnvironment = props.keybox.config.f8eEnvironment
            )
            .onSuccess {
              val transferPartners = it.partnerList.toImmutableList()
              transferPartners.forEach { partner ->
                eventTracker.track(
                  action = Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER,
                  context = PartnerEventTrackerScreenIdContext(partner)
                )
              }
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
      is State.LoadingPartnershipsTransferFailure -> {
        val anotherWalletOrExchange =
          ListItemModel(
            leadingAccessory =
              IconAccessory(
                model =
                  IconModel(
                    iconImage = LocalImage(MediumIconQrCode),
                    iconSize = IconSize.Large
                  )
              ),
            title = "Another exchange or wallet",
            onClick = { props.onAnotherWalletOrExchange() },
            treatment = PRIMARY
          )

        val partnersGroupModel =
          ListGroupModel(
            items = immutableListOf(anotherWalletOrExchange),
            style = CARD_ITEM
          )

        return TransferPartnersErrorModel(
          id = TRANSFER_PARTNERS_NOT_FOUND_ERROR,
          content = ListGroup(partnersGroupModel),
          onBack = props.onBack,
          onExit = props.onExit
        )
      }

      is State.ChoosingPartnershipsTransfer -> {
        when {
          currentState.transferPartners.isEmpty() -> {
            LaunchedEffect("display-qr-code-delay") {
              delay(500)
              props.onAnotherWalletOrExchange()
            }

            return Loading(
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
          coroutineBinding {
            val address = bitcoinAddressService.generateAddress().bind()
            val result = getTransferRedirectF8eClient
              .getTransferRedirect(
                fullAccountId = props.keybox.fullAccountId,
                f8eEnvironment = props.keybox.config.f8eEnvironment,
                partner = currentState.partnerInfo.partnerId.value,
                address = address
              ).bind()

            val localTransaction = partnershipTransactionsService.create(
              id = result.redirectInfo.partnerTransactionId,
              partnerInfo = currentState.partnerInfo,
              type = PartnershipTransactionType.TRANSFER
            ).bind()

            localTransaction to result
          }.onFailure { error ->
            state =
              State.LoadingPartnerRedirectFailure(
                error = error,
                partnerInfo = currentState.partnerInfo,
                transferPartners = currentState.transferPartners,
                rollback = { state = State.LoadingPartnershipsTransfer }
              )
          }.onSuccess { (localTransaction, result) ->
            state =
              State.PartnerRedirectInformationLoaded(
                partnerInfo = currentState.partnerInfo,
                redirectInfo = result.redirectInfo,
                localTransaction = localTransaction
              )
          }
        }
        return Loading(
          id = LOADING_TRANSFER_PARTNER_REDIRECT,
          context = PartnerEventTrackerScreenIdContext(currentState.partnerInfo),
          onExit = props.onExit
        )
      }
      is State.LoadingPartnerRedirectFailure ->
        return TransferErrorModel(
          id = TRANSFER_PARTNER_REDIRECT_ERROR,
          context = PartnerEventTrackerScreenIdContext(currentState.partnerInfo),
          error = currentState.error,
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
                partnerName = currentState.partnerInfo.partnerId.value
              ),
              currentState.localTransaction
            )
          }

          WIDGET -> {
            props.onPartnerRedirected(
              Web(urlString = currentState.redirectInfo.url, currentState.partnerInfo),
              currentState.localTransaction
            )
          }
        }
        return Loading(
          id = TRANSFER_PARTNER_REDIRECTING,
          context = PartnerEventTrackerScreenIdContext(currentState.partnerInfo),
          onExit = props.onExit
        )
      }
    }
  }

  @Composable
  private fun TransferErrorModel(
    id: DepositEventTrackerScreenId,
    context: PartnerEventTrackerScreenIdContext? = null,
    title: String = "Error",
    error: Throwable?,
    errorMessage: String,
    onBack: () -> Unit,
    onExit: () -> Unit,
  ): SheetModel {
    LaunchedEffect("transfer-log-error", error, errorMessage) {
      logError(throwable = error) { errorMessage }
    }
    return SheetModel(
      body =
        ErrorFormBodyModel(
          eventTrackerScreenId = id,
          eventTrackerContext = context,
          title = title,
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
    id: DepositEventTrackerScreenId,
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
                  iconSize = IconSize.Large
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
                iconSize = IconSize.Large
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
    id: DepositEventTrackerScreenId,
    content: FormMainContentModel,
    onBack: () -> Unit,
    onExit: () -> Unit,
  ): SheetModel {
    return SheetModel(
      body = TransferPartnersBodyModel(
        header = FormHeaderModel(
          headline = "Receive bitcoin from",
          subline = null
        ),
        id = id,
        content = content,
        onBack = onBack
      ),
      dragIndicatorVisible = true,
      onClosed = onExit
    )
  }

  @Composable
  private fun TransferPartnersErrorModel(
    id: DepositEventTrackerScreenId,
    content: FormMainContentModel,
    onBack: () -> Unit,
    onExit: () -> Unit,
  ): SheetModel {
    return SheetModel(
      body = TransferPartnersBodyModel(
        header = FormHeaderModel(
          icon = LargeIconWarningFilled,
          headline = "Could not load partners at this time.",
          alignment = CENTER
        ),
        id = id,
        content = content,
        onBack = onBack
      ),
      dragIndicatorVisible = true,
      onClosed = onExit
    )
  }

  private data class TransferPartnersBodyModel(
    override val header: FormHeaderModel?,
    override val id: DepositEventTrackerScreenId,
    val content: FormMainContentModel,
    override val onBack: () -> Unit,
  ) : FormBodyModel(
      onBack = onBack,
      toolbar = null,
      header = header,
      mainContentList = immutableListOf(content),
      primaryButton = null,
      id = id,
      renderContext = Sheet
    )

  @Composable
  private fun Loading(
    id: DepositEventTrackerScreenId? = null,
    context: PartnerEventTrackerScreenIdContext? = null,
    onExit: () -> Unit,
  ): SheetModel {
    return SheetModel(
      body = LoadingBodyModel(
        id = id,
        context = context
      ),
      dragIndicatorVisible = false,
      size = MIN40,
      onClosed = onExit
    )
  }

  private data class LoadingBodyModel(
    override val id: DepositEventTrackerScreenId?,
    val context: PartnerEventTrackerScreenIdContext?,
  ) : FormBodyModel(
      id = id,
      eventTrackerContext = context,
      onBack = {},
      toolbar = null,
      header = null,
      mainContentList = immutableListOf(Loader),
      primaryButton = null,
      renderContext = Sheet
    )
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
    val error: Throwable,
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
    val localTransaction: PartnershipTransaction,
  ) : State
}
