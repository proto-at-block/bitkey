package build.wallet.compose.collections

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.collections.immutable.ImmutableList

class ImmutableCollectionsTests : FunSpec({

  test("emptyImmutableList returns empty immutable list") {
    emptyImmutableList<String>()
      .shouldBeInstanceOf<ImmutableList<String>>()
      .shouldBeEmpty()
  }

  context("buildImmutableList") {
    test("no values return empty immutable list") {
      buildImmutableList<String> { }
        .shouldBeInstanceOf<ImmutableList<String>>()
        .shouldBeEmpty()
    }

    test("added values return immutable list with values") {
      buildImmutableList {
        add("a")
        add("b")
      }
        .shouldBeInstanceOf<ImmutableList<String>>()
        .shouldContainExactly("a", "b")
    }

    test("added list values return immutable list with values") {
      buildImmutableList {
        addAll(listOf("a", "b"))
        add("c")
      }
        .shouldBeInstanceOf<ImmutableList<String>>()
        .shouldContainExactly("a", "b", "c")
    }

    test("removed values are not included in immutable list") {
      buildImmutableList {
        add("a")
        add("b")
        remove("a")
      }
        .shouldBeInstanceOf<ImmutableList<String>>()
        .shouldContainExactly("b")
    }
  }

  context("immutableListOfNotNull") {
    test("no values return empty immutable list") {
      immutableListOfNotNull<String>()
        .shouldBeInstanceOf<ImmutableList<String>>()
        .shouldBeEmpty()
    }

    test("null values only return an empty immutable list") {
      immutableListOfNotNull<String>(null, null)
        .shouldBeInstanceOf<ImmutableList<String>>()
        .shouldBeEmpty()
    }

    test("mixed null and non-null values return immutable list with only non-null values") {
      immutableListOfNotNull<String>(null, "a", null, "b", null)
        .shouldBeInstanceOf<ImmutableList<String>>()
        .shouldContainExactly("a", "b")
    }
  }
})
