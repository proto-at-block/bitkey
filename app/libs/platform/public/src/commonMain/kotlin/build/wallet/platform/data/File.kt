package build.wallet.platform.data

object File {
  /**
   * We only target iOS, JVM and Android, they all use forward slash as file separator.
   */
  private val fileSeparator: Char get() = '/'

  fun String.join(path: String): String {
    return this.trimEnd(fileSeparator) + fileSeparator + path.trimStart(fileSeparator)
  }
}
