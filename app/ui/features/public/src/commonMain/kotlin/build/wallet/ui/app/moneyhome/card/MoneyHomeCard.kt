package build.wallet.ui.app.moneyhome.card

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet.Animation.Height
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet.Animation.Scale
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.BitcoinPrice
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.DrillList
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Gradient
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.card.CardContent
import build.wallet.ui.components.card.GradientCard
import build.wallet.ui.theme.WalletTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.DurationUnit.SECONDS

@Composable
fun MoneyHomeCard(
  modifier: Modifier = Modifier,
  model: CardModel,
) {
  // The real height of the card is intrinsic but we have to use an actual value here in
  // order for the animation to work
  val height = remember { Animatable(model.estimatedHeight()) }
  val scale = remember { Animatable(1f) }

  LaunchedEffect(model.animation) {
    model.animation?.forEach { animationSet ->
      // Give each animation set a scope so that they happen one after the other and
      // use `launch` within an animation set to ensure they happen at the same time
      coroutineScope {
        animationSet.animations.forEach {
          val animationSpec: TweenSpec<Float> =
            tween(
              durationMillis =
                Duration.convert(
                  animationSet.durationInSeconds,
                  SECONDS,
                  MILLISECONDS
                ).toInt()
            )
          when (val animation = it) {
            is Height ->
              launch {
                height.animateTo(animation.value, animationSpec)
              }
            is Scale ->
              launch {
                scale.animateTo(animation.value, animationSpec)
              }
            else -> Unit
          }
        }
      }
    }
  }

  // Only add the height modifier if we are in the middle of an animation
  var cardModifier =
    modifier.clickable(
      interactionSource = MutableInteractionSource(),
      indication = null,
      enabled = model.onClick != null,
      onClick = {
        model.onClick?.invoke()
      }
    )
  if (model.animation != null) {
    cardModifier =
      cardModifier
        .height(height.value.dp)
  }

  when (val style = model.style) {
    CardModel.CardStyle.Plain ->
      Card(
        modifier = cardModifier.scale(scale.value),
        backgroundColor = Color.Unspecified,
        paddingValues = PaddingValues(0.dp),
        borderWidth = 0.dp
      ) {
        CardContent(
          modifier = Modifier
            .fillMaxWidth(),
          model = model
        )
      }

    Outline ->
      Card(
        modifier = cardModifier.scale(scale.value),
        paddingValues = PaddingValues(0.dp)
      ) {
        CardContent(
          modifier = Modifier
            .fillMaxWidth()
            .padding(
              top = 20.dp,
              start = 20.dp,
              end = 20.dp,
              bottom = if (model.content == null) 20.dp else 0.dp
            ),
          model = model
        )
      }

    is Gradient ->
      GradientCard(
        modifier = cardModifier.scale(scale.value),
        backgroundColor = when (style.backgroundColor) {
          Gradient.BackgroundColor.Default -> WalletTheme.colors.calloutInformationBackground
          else -> WalletTheme.colors.containerBackgroundHighlight
        }
      ) {
        CardContent(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp),
          model = model
        )
      }

    is CardModel.CardStyle.Callout ->
      CardContent(
        modifier = cardModifier.scale(scale.value),
        model = model
      )
  }
}

private fun CardModel.estimatedHeight() =
  listOfNotNull(
    20f, // top padding
    17f, // title height
    subtitle?.let { 15f }, // subtitle height
    content?.let {
      when (it) {
        is DrillList ->
          // each row height + spacing in between rows
          (it.items.count() * 56f) + ((it.items.count() - 1) * 12f)
        is BitcoinPrice -> 100f
        is CardModel.CardContent.PendingClaim -> 104f
      }
    },
    if (content == null) {
      20f
    } else {
      null
    } // bottom padding if no content
  ).sum()
