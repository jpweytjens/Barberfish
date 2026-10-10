package com.jpweytjens.barberfish

import com.jpweytjens.barberfish.datatype.shared.whileOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigGateTest {

    private data class Config(val enabled: Boolean, val colour: String = "red")

    /** A source that counts who is subscribed to it right now, and how often anyone was. */
    private class CountingSource {
        var active = 0
        var subscriptions = 0

        fun flow(): Flow<String> = flow {
            active++
            subscriptions++
            try {
                emit("data")
                awaitCancellation()
            } finally {
                active--
            }
        }
    }

    private fun run(
        config: MutableStateFlow<Config>,
        source: CountingSource,
        body: suspend (seen: List<String>) -> Unit,
    ) = runBlocking {
        val seen = mutableListOf<String>()
        val collector =
            launch(Dispatchers.Unconfined) {
                config.whileOn({ it.enabled }, off = "off") { source.flow() }.collect { seen += it }
            }
        yield()
        body(seen)
        collector.cancel()
    }

    @Test
    fun off_subscribes_to_nothing() {
        val source = CountingSource()
        run(MutableStateFlow(Config(enabled = false)), source) { seen ->
            assertEquals(listOf("off"), seen)
            assertEquals(0, source.subscriptions)
        }
    }

    @Test
    fun on_subscribes_once_and_passes_the_data() {
        val source = CountingSource()
        run(MutableStateFlow(Config(enabled = true)), source) { seen ->
            assertEquals(listOf("data"), seen)
            assertEquals(1, source.active)
        }
    }

    @Test
    fun switching_off_cancels_the_subscription_and_on_starts_it_afresh() {
        val source = CountingSource()
        val config = MutableStateFlow(Config(enabled = true))
        run(config, source) { seen ->
            config.value = Config(enabled = false)
            yield()
            assertEquals(0, source.active)
            assertEquals("off", seen.last())
            config.value = Config(enabled = true)
            yield()
            assertEquals(1, source.active)
            assertEquals(2, source.subscriptions)
        }
    }

    @Test
    fun other_config_changes_do_not_resubscribe() {
        val source = CountingSource()
        val config = MutableStateFlow(Config(enabled = true))
        run(config, source) { _ ->
            config.value = Config(enabled = true, colour = "blue")
            yield()
            assertEquals(1, source.subscriptions)
        }
    }
}
