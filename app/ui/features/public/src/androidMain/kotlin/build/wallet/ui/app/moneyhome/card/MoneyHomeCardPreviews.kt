@file:Suppress("detekt:TooManyFunctions")

package build.wallet.ui.app.moneyhome.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.Progress
import build.wallet.bitkey.relationships.*
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.AddBitcoin
import build.wallet.home.GettingStartedTask.TaskId.EnableSpendingLimit
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.pricechart.DataPoint
import build.wallet.pricechart.PriceDirection
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.BitcoinPrice
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.statemachine.moneyhome.card.gettingstarted.FirmwareUpdateGettingStartedTileModel
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardModel
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedTaskRowModel
import build.wallet.statemachine.moneyhome.card.inheritance.BenefactorLockedCompleteClaimCardModel
import build.wallet.statemachine.moneyhome.card.inheritance.BenefactorPendingClaimCardModel
import build.wallet.statemachine.moneyhome.card.inheritance.BeneficiaryPendingClaimCardModel
import build.wallet.statemachine.moneyhome.lite.card.BuyOwnBitkeyMoneyHomeCardModel
import build.wallet.statemachine.moneyhome.lite.card.InheritanceMoneyHomeCard
import build.wallet.statemachine.moneyhome.lite.card.WalletsProtectingMoneyHomeCardModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryCardModel
import build.wallet.statemachine.trustedcontact.model.TrustedContactCardModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.callout.CalloutModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Instant.Companion.DISTANT_FUTURE
import kotlinx.datetime.Instant.Companion.DISTANT_PAST
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@Preview
@Composable
fun PreviewMoneyHomePriceCard(
  isLoading: Boolean = false,
  price: String = "$90,000.00",
) {
  MoneyHomeCard(
    model =
      CardModel(
        title = null,
        content = BitcoinPrice(
          isLoading = isLoading,
          priceChange = "10.00% today",
          priceDirection = PriceDirection.UP,
          lastUpdated = "Updated 12:00am",
          price = price,
          data = generateChartData(150)
            .takeUnless { isLoading }
            ?: immutableListOf()
        ),
        style = Outline()
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomePriceCardLoading() {
  PreviewMoneyHomePriceCard(isLoading = true)
}

@Preview(
  fontScale = 1.5f
)
@Composable
fun PreviewMoneyHomePriceCardLargeFont() {
  PreviewMoneyHomePriceCard(
    isLoading = false,
    price = "$100,000.00"
  )
}

@Preview(
  fontScale = 2f
)
@Composable
fun PreviewMoneyHomePriceCardHugeFont() {
  PreviewMoneyHomePriceCard(
    isLoading = false,
    price = "$100,000.00"
  )
}

@Preview
@Composable
fun PreviewMoneyHomeGettingStarted() {
  MoneyHomeCard(
    model =
      GettingStartedCardModel(
        animations = null,
        taskModels =
          immutableListOf(
            GettingStartedTaskRowModel(
              task = GettingStartedTask(AddBitcoin, Incomplete),
              isEnabled = true,
              onClick = {}
            ),
            GettingStartedTaskRowModel(
              task = GettingStartedTask(EnableSpendingLimit, Incomplete),
              isEnabled = false,
              onClick = {}
            )
          )
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeGettingStartedWithFirmwareUpdate() {
  MoneyHomeCard(
    model =
      GettingStartedCardModel(
        animations = null,
        taskModels =
          immutableListOf(
            GettingStartedTaskRowModel(
              task = GettingStartedTask(AddBitcoin, Incomplete),
              isEnabled = true,
              onClick = {}
            ),
            GettingStartedTaskRowModel(
              task = GettingStartedTask(EnableSpendingLimit, Incomplete),
              isEnabled = false,
              onClick = {}
            )
          ),
        firmwareUpdateTile = FirmwareUpdateGettingStartedTileModel(onClick = {})
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardInvitationPending() {
  MoneyHomeCard(
    model =
      TrustedContactCardModel(
        contact =
          Invitation(
            relationshipId = "foo",
            trustedContactAlias = TrustedContactAlias("Bela"),
            code = "token",
            codeBitLength = 20,
            expiresAt = DISTANT_FUTURE,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          ),
        buttonText = "Pending",
        onClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardInvitationExpired() {
  MoneyHomeCard(
    model =
      TrustedContactCardModel(
        contact =
          Invitation(
            relationshipId = "foo",
            trustedContactAlias = TrustedContactAlias("Bela"),
            code = "token",
            codeBitLength = 20,
            expiresAt = DISTANT_PAST,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          ),
        buttonText = "Expired",
        onClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardReplacementPending() {
  MoneyHomeCard(
    model =
      HardwareRecoveryCardModel(
        title = "Replacement pending...",
        subtitle = "2 days remaining",
        delayPeriodProgress = Progress.Half,
        delayPeriodRemainingSeconds = 0,
        onClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardReplacementReady() {
  MoneyHomeCard(
    model =
      HardwareRecoveryCardModel(
        title = "Replacement Ready",
        delayPeriodProgress = Progress.Full,
        delayPeriodRemainingSeconds = 0,
        onClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardInactiveWallet() {
  MoneyHomeCard(
    model =
      CardModel(
        title = null,
        content = null,
        style = CardModel.CardStyle.Callout(
          CalloutModel(
            title = "Funds in inactive wallet",
            subtitle = LabelModel.StringModel("Transfer funds now"),
            treatment = CalloutModel.Treatment.Warning,
            useMonochromeStyleInDesignSystemV2 = true,
            leadingIcon = Icon.SmallIconInformationFilled,
            trailingIcon = Icon.SmallIconArrowRight,
            onClick = StandardClick {}
          )
        )
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardBenefactorPendingClaim() {
  MoneyHomeCard(
    model =
      BenefactorPendingClaimCardModel(
        title = "Inheritance claim initiated",
        subtitle = "Decline claim by Apr 14, 2026 to retain control of your funds.",
        onClick = StandardClick {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardBenefactorApprovedClaim() {
  MoneyHomeCard(
    model =
      BenefactorLockedCompleteClaimCardModel(
        title = "Inheritance approved",
        subtitle = "To retain control of your funds, transfer them to a new wallet.",
        onClick = StandardClick {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardBeneficiaryPendingClaim() {
  MoneyHomeCard(
    model =
      BeneficiaryPendingClaimCardModel(
        title = "Inheritance claim pending",
        subtitle = "Funds available Apr 14, 2026.",
        isPendingClaim = true,
        timeRemaining = 1.days,
        progress = Progress.Half,
        onClick = null
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardBeneficiaryApprovedClaim() {
  MoneyHomeCard(
    model =
      BeneficiaryPendingClaimCardModel(
        title = "Claim approved",
        subtitle = "Transfer funds now.",
        isPendingClaim = false,
        timeRemaining = Duration.ZERO,
        progress = Progress.Full,
        onClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardWalletsProtecting() {
  MoneyHomeCard(
    model =
      WalletsProtectingMoneyHomeCardModel(
        protectedCustomers =
          immutableListOf(
            ProtectedCustomer(
              relationshipId = "",
              alias = ProtectedCustomerAlias("Alice"),
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            ),
            ProtectedCustomer(
              relationshipId = "",
              alias = ProtectedCustomerAlias("Bob"),
              roles = setOf(TrustedContactRole.SocialRecoveryContact)
            )
          ),
        onProtectedCustomerClick = {},
        onAcceptInviteClick = {}
      )
  )
}

@Preview
@Composable
fun PreviewMoneyHomeCardBuyOwnBitkey() {
  MoneyHomeCard(
    model = BuyOwnBitkeyMoneyHomeCardModel(onClick = {})
  )
}

@Preview
@Composable
fun PreviewInheritanceMoneyHomeCard() {
  MoneyHomeCard(
    model = InheritanceMoneyHomeCard(
      onIHaveABitkey = {},
      onGetABitkey = {}
    )
  )
}

private fun generateChartData(pointCount: Int): ImmutableList<DataPoint> {
  return buildImmutableList {
    for (i in 0 until pointCount) {
      val y = abs(sin(i * PI / 30) * 20 + cos(i * PI / 15) * 10)
      add(DataPoint(i.toLong(), y))
    }
  }
}
