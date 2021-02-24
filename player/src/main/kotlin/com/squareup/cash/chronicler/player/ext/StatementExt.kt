package com.squareup.cash.chronicler.player.ext

import squareup.chronicler.Statement
import java.time.Duration

val Statement.duration: Duration get() = Duration.between(client_start, client_end)
