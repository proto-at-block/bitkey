package build.wallet.ui.model.list

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ListItemAccessoryTests : FunSpec({
  test("Contact Avatar uses string initials") {
    val accessory = ListItemAccessory.ContactAvatarAccessory(
      name = "John Doe",
      isLoading = false
    )

    accessory.initials.shouldBe("JD")
  }

  test("Contact Avatar uses only first and last name initials") {
    val accessory = ListItemAccessory.ContactAvatarAccessory(
      name = "John Michael Doe",
      isLoading = false
    )

    accessory.initials.shouldBe("JD")
  }

  test("Contact Avatar handles irregular characters") {
    val accessory = ListItemAccessory.ContactAvatarAccessory(
      name = "*",
      isLoading = false
    )

    accessory.initials.shouldBe("?")

    val emojiAccessory = ListItemAccessory.ContactAvatarAccessory(
      name = "🔥",
      isLoading = false
    )

    emojiAccessory.initials.shouldBe("?")
  }

  test("Contact Avatar handles irregular characters") {
  }

  test("Circular character uses first letter of string") {
    val accessory = ListItemAccessory.CircularCharacterAccessory.fromLetters(
      input = "Hello World"
    )

    accessory.text.shouldBe("H")
  }

  test("Circular character handles irregular characters") {
    val accessory = ListItemAccessory.CircularCharacterAccessory.fromLetters(
      input = "🔥"
    )

    accessory.text.shouldBe("?")
  }
})
