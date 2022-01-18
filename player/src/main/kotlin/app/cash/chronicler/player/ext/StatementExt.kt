package app.cash.chronicler.player.ext

import app.cash.chronicler.proto.Statement
import java.time.Duration

val Statement.duration: Duration get() = Duration.between(client_start, client_end)
