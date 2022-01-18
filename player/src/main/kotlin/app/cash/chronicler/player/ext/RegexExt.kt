package app.cash.chronicler.player.ext

fun String.parseFancyRegex(): Regex {
  check(this.startsWith("/"))
  val trimmed = this.substringAfter("/")
  val pattern = trimmed.substringBeforeLast("/")
  val flags = trimmed.substringAfterLast("/")
  val parsedFlags = flags.toCharArray().distinct().map { f ->
    when (f) {
      'i' -> RegexOption.IGNORE_CASE
      'm' -> RegexOption.MULTILINE
      else -> error("Unsupported regex flag: $f")
    }
  }
  return pattern.toRegex(parsedFlags.toSet())
}
