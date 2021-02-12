package ru.yoo.sdk.march

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.only
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

@ExperimentalCoroutinesApi
class LoggingChannelTest {
    @Test
    fun `offer should log element and send to origin`() {
        // given
        val expected = "expected"
        val origin = mock<Channel<String>>()
        val log = mock<Listener>()
        val loggingChannel = LoggingChannel(origin, log)

        // when
        loggingChannel.offer(expected)

        // then
        verify(log).invoke(expected)
        verify(origin, only()).offer(expected)
    }

    @Test
    fun `send should log element and send to origin`() = runBlockingTest {
        // given
        val expected = "expected"
        val origin = mock<Channel<String>>()
        val log = mock<Listener>()
        val loggingChannel = LoggingChannel(origin, log)

        // when
        loggingChannel.send(expected)

        // then
        verify(log).invoke(expected)
        verify(origin, only()).send(expected)
    }

    interface Listener : (String) -> Unit
}