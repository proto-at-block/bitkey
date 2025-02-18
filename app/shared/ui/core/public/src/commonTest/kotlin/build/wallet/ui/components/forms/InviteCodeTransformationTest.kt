package build.wallet.ui.components.forms

import androidx.compose.ui.text.AnnotatedString
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.ints.shouldBeExactly

class InviteCodeTransformationTest : FunSpec({

  // A map of test cases where the key is the transformed text
  // and the value is the original text.
  // Use | to represent the expected cursor index in both values.
  val mappingTestCases = mapOf(
    "xxx|x-xxxx-xxxx" to "xxx|xxxxxxxx",
    "xxxx-x|xxx-xxxx" to "xxxxx|xxxxxxx",
    "xxxx-xxxx-x|xxx" to "xxxxxxxxx|xxx",
    "xxxx-xxxx-xxxx|" to "xxxxxxxxxxxx|",
    "|xxxx-xxxx-xxxx" to "|xxxxxxxxxxxx"
  )

  mappingTestCases.onEachIndexed { index, entry ->
    val (transformed, original) = entry
    val transformedIndex = transformed.indexOf("|")
    val originalIndex = original.indexOf("|")

    val testLabel = when (originalIndex) {
      0 -> "string start"
      original.lastIndex -> "string end"
      else -> "chunk ${index + 1}"
    }

    test("map to original - $testLabel") {
      InviteCodeOffsetMapping(original.replace("|", ""))
        .transformedToOriginal(transformedIndex)
        .shouldBeExactly(originalIndex)
    }

    test("map to transformed - $testLabel") {
      InviteCodeOffsetMapping(original.replace("|", ""))
        .originalToTransformed(originalIndex)
        .shouldBeExactly(transformedIndex)
    }
  }

  test("transformed text formatting") {
    val transformTestCases = mapOf(
      "xxxx" to "xxxx",
      "xxxx-xx" to "xxxxxx",
      "xxxx-xxxx" to "xxxxxxxx",
      "xxxx-xxxx-xxxx" to "xxxxxxxxxxxx"
    )
    val transformation = InviteCodeTransformation

    transformTestCases.forEach { (transformed, original) ->
      transformation
        .filter(AnnotatedString(original))
        .text
        .text
        .shouldBeEqual(transformed)
    }
  }
})
