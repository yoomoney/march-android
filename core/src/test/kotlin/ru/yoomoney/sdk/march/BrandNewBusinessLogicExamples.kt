/*
 * The MIT License (MIT)
 * Copyright © 2021 YooMoney Tech division by NBCO YooMoney LLC
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.yoomoney.sdk.march

import ru.yoomoney.sdk.march.Out.Companion.skip
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

@ExperimentalCoroutinesApi
class BrandNewBusinessLogicExamples {

    private val display: DisplayStub = mock()
    private val source: SourceStub = mock()
    private val load: LoadStub = mock()

    private val businessLogic = ExampleBusinessLogic(
        display = { state ->
            display(state)
            suspendUntilCancelled()
        },
        source = source::invoke,
        load = load::invoke
    )

    @Test
    fun `change initial state with first action`() {
        // given

        // when
        val out = businessLogic(State.Initial, Input.First)

        // then
        assertThat(out.state).isEqualTo(State.First)
    }

    @Test
    fun `should show state on ui after First action for First state`() = runBlockingTest {
        // given
        val expectedInput = Input.First
        source.stub { onBlocking { invoke() } doReturn expectedInput }
        val out = businessLogic(State.Initial, Input.First)

        // when
        val result = out.sources.map {
            withTimeoutOrNull(10) {
                when (it) {
                    is Effect.Input.Fun -> it.func()
                    is Effect.Output -> it.func()
                    is Effect.Input.Sub -> error("unexpected")
                    is Effect.Cancel -> error("unexpected")
                }
            }
        }

        // then
        verify(display).invoke(State.First)
        assertThat(result).isEqualTo(listOf(null, expectedInput))
    }

    @Test
    fun `change First state to Second on First action`() {
        // given

        // when
        val out = businessLogic(State.First, Input.First)

        // then
        assertThat(out.state).isEqualTo(State.Second)
    }

    @Test
    fun `should show effect and state on ui after First action for First state`() = runBlockingTest {
        // given
        val expectedInput = Input.First
        source.stub { onBlocking { invoke() } doReturn expectedInput }
        val out = businessLogic(State.First, Input.First)

        // when
        val results = out.sources.map {
            withTimeoutOrNull(10) {
                when (it) {
                    is Effect.Input.Fun -> it.func()
                    is Effect.Output -> it.func()
                    is Effect.Input.Sub -> error("unexpected")
                    is Effect.Cancel -> error("unexpected")
                }
            }
        }

        // then
        display.inOrder {
            verify().invoke(State.First)
            verify().invoke(State.Second)
            verifyNoMoreInteractions()
        }
        assertThat(results).isEqualTo(listOf(null, null, expectedInput))
    }

    @Test
    fun `not change Second state to Third on First action`() {
        // given

        // when
        val out = businessLogic(State.Second, Input.First)

        // then
        assertThat(out.state).isEqualTo(State.Second)
    }

    @Test
    fun `should start loading after First action for Second state`() = runBlockingTest {
        // given
        val expectedValue = Input.Second(1)
        load.stub { onBlocking { invoke(any()) } doReturn expectedValue.value }
        val out = businessLogic(State.Second, Input.First)

        // when
        val results = out.sources.map { (it as Effect.Input.Fun).func() }

        // then
        verify(load).invoke("1")
        verifyZeroInteractions(display, source)

        assertThat(results).isEqualTo(listOf(expectedValue))
    }

    @Test
    fun `change Second state to Third on Second action`() {
        // given
        val expected = State.Third(1)

        // when
        val out = businessLogic(State.Second, Input.Second(expected.value))

        // then
        assertThat(out.state).isEqualTo(expected)
    }

    @Test
    fun `should show state on ui after Second state on Second action`() = runBlockingTest {
        // given
        val expectedInput = Input.Second(1)
        source.stub { onBlocking { invoke() } doReturn expectedInput }
        val out = businessLogic(State.Second, expectedInput)

        // when
        val results = out.sources.map {
            withTimeoutOrNull(10) {
                when (it) {
                    is Effect.Input.Fun -> it.func()
                    is Effect.Output -> it.func()
                    is Effect.Input.Sub -> error("unexpected")
                    is Effect.Cancel -> error("unexpected")
                }
            }
        }

        // then
        verify(display).invoke(State.Third(expectedInput.value))

        assertThat(results).isEqualTo(listOf(null, expectedInput))
    }

    class ExampleBusinessLogic(
        val display: suspend (State) -> Unit,
        val source: suspend () -> Input,
        val load: suspend (String) -> Int
    ) : Logic<State, Input> {
        override operator fun invoke(state: State, input: Input): Out<State, Input> {
            return when (state) {
                is State.Initial -> when (input) {
                    // just show and wait input
                    is Input.First -> Out(State.First) {
                        output { display(this.state) }
                        input(source)
                    }
                    else -> skip(state, source)
                }
                is State.First -> when (input) {
                    // show effect and state, then wait input
                    is Input.First -> Out(State.Second) {
                        output { display(state) }
                        output { display(this.state) }
                        input(source)
                    }
                    is Input.Second -> skip(state, source)
                }
                is State.Second -> when (input) {
                    // do not change ui and start loading
                    is Input.First -> Out(state) {
                        input { Input.Second(load("1")) }
                    }
                    is Input.Second -> Out(State.Third(input.value)) {
                        output { display(this.state) }
                        input(source)
                    }
                }
                is State.Third -> skip(state, source)
            }
        }
    }

    sealed class State {
        object Initial : State() {
            override fun toString(): String = javaClass.simpleName
        }

        object First : State() {
            override fun toString(): String = javaClass.simpleName
        }

        object Second : State() {
            override fun toString(): String = javaClass.simpleName
        }

        data class Third(val value: Int) : State()
    }

    sealed class Input {
        object First : Input() {
            override fun toString(): String = javaClass.simpleName
        }

        data class Second(val value: Int) : Input()
    }

    // suspend - защита от выполнения в "чистой" среде, то есть эту функцию нельзя выполнить в логике
    interface DisplayStub {
        suspend operator fun invoke(state: State)
    }

    interface SourceStub {
        suspend operator fun invoke(): Input
    }

    interface LoadStub {
        suspend operator fun invoke(value: String): Int
    }
}