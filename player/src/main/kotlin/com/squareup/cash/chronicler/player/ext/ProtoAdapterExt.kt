package com.squareup.cash.chronicler.player.ext

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream
import com.squareup.wire.ProtoAdapter
import java.nio.ByteBuffer

fun <T> ProtoAdapter<T>.decode(buffer: ByteBuffer) = decode(ByteBufferBackedInputStream(buffer))
