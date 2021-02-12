package ru.yoo.sdk.march

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Test

class LoggingFunctionTest {
    @Test
    fun `should send argument to log when invoked`() {
        // given
        val expected = "expected"
        val origin = mock<Listener>()
        val log = mock<Listener>()
        val loggingFunction = LoggingFunction(origin, log)

        // when
        loggingFunction(expected)

        // then
        verify(origin).invoke(expected)
        verify(log).invoke(expected)
    }

    interface Listener : (String) -> Unit
}