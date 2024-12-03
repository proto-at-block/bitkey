// DO NOT EDIT.
// This file is generated from design tokens in the wallet/style folder.

package build.wallet.ui.tokens

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import build.wallet.ui.typography.font.robotoMonoFontFamily

enum class LabelType {
  Display1,
  Display2,
  Display3,
  Title1,
  Title2,
  Title3,
  Body1Regular,
  Body1Medium,
  Body1Bold,
  Body2Regular,
  Body2Medium,
  Body2Bold,
  Body2Mono,
  Body2Link,
  Body3Regular,
  Body3Medium,
  Body3Bold,
  Body3Link,
  Body3Mono,
  Body4Regular,
  Body4Medium,
  Label1,
  Label1Bold,
  Label2,
  Label3,
  Keypad,
}

@Composable
fun LabelType.style(baseStyle: TextStyle) =
  when (this) {
    LabelType.Display1 ->
      baseStyle.copy(
        fontWeight = FontWeight.W700,
        fontSize = 64.sp,
        lineHeight = 76.sp,
        letterSpacing = (-1.43).sp
      )
    LabelType.Display2 ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 48.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.07).sp
      )
    LabelType.Display3 ->
      baseStyle.copy(
        fontWeight = FontWeight.W500,
        fontSize = 32.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.07).sp
      )
    LabelType.Title1 ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.47).sp
      )
    LabelType.Title2 ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.22).sp
      )
    LabelType.Title3 ->
      baseStyle.copy(
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.09).sp
      )
    LabelType.Body1Regular ->
      baseStyle.copy(
        fontWeight = FontWeight.W400,
        fontSize = 20.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.33).sp
      )
    LabelType.Body1Medium ->
      baseStyle.copy(
        fontWeight = FontWeight.W500,
        fontSize = 20.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.33).sp
      )
    LabelType.Body1Bold ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 20.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.33).sp
      )
    LabelType.Body2Regular ->
      baseStyle.copy(
        fontWeight = FontWeight.W400,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.22).sp
      )
    LabelType.Body2Medium ->
      baseStyle.copy(
        fontWeight = FontWeight.W500,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.22).sp
      )
    LabelType.Body2Bold ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.22).sp
      )
    LabelType.Body2Mono ->
      baseStyle.copy(
        fontFamily = robotoMonoFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (0).sp
      )
    LabelType.Body2Link ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.22).sp
      )
    LabelType.Body3Regular ->
      baseStyle.copy(
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.13).sp
      )
    LabelType.Body3Medium ->
      baseStyle.copy(
        fontWeight = FontWeight.W500,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.13).sp
      )
    LabelType.Body3Bold ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.13).sp
      )
    LabelType.Body3Link ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.13).sp
      )
    LabelType.Body3Mono ->
      baseStyle.copy(
        fontFamily = robotoMonoFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = (0).sp
      )
    LabelType.Body4Regular ->
      baseStyle.copy(
        fontWeight = FontWeight.W400,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.13).sp
      )
    LabelType.Body4Medium ->
      baseStyle.copy(
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.13).sp
      )
    LabelType.Label1 ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.18).sp
      )
    LabelType.Label1Bold ->
      baseStyle.copy(
        fontWeight = FontWeight.W700,
        fontSize = 16.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
      )
    LabelType.Label2 ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.09).sp
      )
    LabelType.Label3 ->
      baseStyle.copy(
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 14.sp,
        letterSpacing = (-0.04).sp
      )
    LabelType.Keypad ->
      baseStyle.copy(
        fontWeight = FontWeight.W700,
        fontSize = 24.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.47).sp
      )
  }
