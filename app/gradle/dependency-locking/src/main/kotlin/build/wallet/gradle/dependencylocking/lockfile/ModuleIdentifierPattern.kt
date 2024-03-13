package build.wallet.gradle.dependencylocking.lockfile

internal data class ModuleIdentifierPattern(val pattern: String) {
  private val split = pattern.split(":").also {
    if (it.size != 2) {
      throwArgumentError()
    }
  }

  private val groupPattern = PatternSegment(split[0]) ?: throwArgumentError()
  private val modulePattern = PatternSegment(split[1]) ?: throwArgumentError()

  fun matches(moduleIdentifier: ModuleIdentifier): Boolean {
    return groupPattern.matches(moduleIdentifier.group) && modulePattern.matches(moduleIdentifier.name)
  }

  private fun throwArgumentError(): Nothing {
    throw IllegalArgumentException(
      """
        Module identifier pattern '$pattern' has incorrect format. 
        Examples of allowed patterns:
            a.b.c:d.e
            a.b.c:d.*
            a.b.c:*
            a.*:d.e
            *:d.e
            *:*
      """.trimIndent()
    )
  }

  override fun toString(): String = pattern

  private class PatternSegment private constructor(patternSegment: String) {
    private val hasWildCard = patternSegment.endsWith("*")
    private val prefix = patternSegment.removeSuffix("*")

    fun matches(string: String): Boolean =
      if (hasWildCard) string.startsWith(prefix) else string == prefix

    companion object {
      operator fun invoke(patternSegment: String): PatternSegment? {
        if (patternSegment.isEmpty() &&
          patternSegment.count { it == '*' } > 1 &&
          patternSegment.indexOf("*") !in listOf(-1, patternSegment.lastIndex)
        ) {
          return null
        }

        return PatternSegment(patternSegment)
      }
    }
  }
}
