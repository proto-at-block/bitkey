package build.wallet.platform.data

import build.wallet.platform.data.File.join
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FileTests : FunSpec({
  test("join") {
    "a".join("b").shouldBe("a/b")
    "a/".join("b").shouldBe("a/b")
    "a".join("/b").shouldBe("a/b")
    "a/".join("/b").shouldBe("a/b")
    "a".join("b/").shouldBe("a/b/")
    "a/".join("b/").shouldBe("a/b/")
    "a".join("/b/").shouldBe("a/b/")
    "a/".join("/b/").shouldBe("a/b/")
  }
})
