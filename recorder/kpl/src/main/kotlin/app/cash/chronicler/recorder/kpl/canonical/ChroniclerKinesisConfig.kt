package app.cash.chronicler.recorder.kpl.canonical

data class ChroniclerKinesisConfig @JvmOverloads constructor(
  var endpoint: String = "",
  var region: String = "",
  var stream: String = ""
)
