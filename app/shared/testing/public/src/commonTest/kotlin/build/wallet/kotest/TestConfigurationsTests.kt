package build.wallet.kotest

import io.kotest.core.extensions.Extension
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class TestConfigurationsTests : FunSpec({

  val testConfiguration = this

  context("extensionLazy") {

    test("register extension lazily") {
      val extension1 = ExtensionMock(id = "1")
      val extension2 = ExtensionMock(id = "2")

      testConfiguration.registeredExtensions().shouldNotContain(extension1)

      // Register and create extension for the first time
      testConfiguration.extensionLazy { extension1 }.shouldBe(extension1)
      testConfiguration.registeredExtensions().shouldContain(extension1)

      // Attempt to register and create the same extension instance
      testConfiguration.extensionLazy { extension1 }.shouldBe(extension1)
      testConfiguration.registeredExtensions().shouldContain(extension1)

      // Attempt to register extension of the same type but different instance - reuses previously
      // registered extension
      testConfiguration.extensionLazy { extension2 }.shouldBe(extension1)
      testConfiguration.registeredExtensions().shouldContain(extension1)
    }
  }
})

private data class ExtensionMock(val id: String) : Extension
