package ru.yoo.sdk.march

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.times

@ExperimentalCoroutinesApi
@RunWith(Parameterized::class)
class LaunchBusinessLogicTest(
    @Suppress("UNUSED_PARAMETER") name: String,
    val strategy: BusinessLogicExecutionStrategy<State, Action, Effect>
) {

    companion object {
        @[Parameterized.Parameters(name = "{0}") JvmStatic]
        fun data(): Collection<Array<out Any>> {
            val strategyV1: BusinessLogicExecutionStrategy<State, Action, Effect> = ::BusinessLogicExecutionStrategyV1
            val strategyV2: BusinessLogicExecutionStrategy<State, Action, Effect> = ::BusinessLogicExecutionStrategyV2
            return listOf(
                arrayOf("BusinessLogicExecutionStrategyV1", strategyV1),
                arrayOf("BusinessLogicExecutionStrategyV2", strategyV2)
            )
        }
    }

    @Test
    fun `should call businessLogic on input with initial state`() = runBlockingTest {
        // given
        val input = Channel<Action>()
        val businessLogic = mock<BusinessLogic> {
            on { invoke(any(), any()) } doReturn just(State.Initial)
        }
        val expected = State.Initial
        val job = launch { strategy(input, Channel(), expected, businessLogic) }

        // when
        input.send(Action.First)

        // then
        verify(businessLogic).invoke(expected, Action.First)
        job.cancelAndJoin()
    }

    @Test
    fun `should send result of businessLogic when businessLogic executed`() = runBlockingTest {
        // given
        val input = Channel<Action>()
        val output = Channel<Triple<State, Command<*, Action>?, Effect?>>()
        val businessLogic = mock<BusinessLogic> {
            on { invoke(State.Initial, Action.First) } doReturn just(State.Next)
        }
        val job = launch { strategy(input, output, State.Initial, businessLogic) }

        // when
        input.send(Action.First)

        // then
        assertThat(output.receive()).isEqualTo(just<State, Action, Effect>(State.Next))
        job.cancelAndJoin()
    }

    @Test
    fun `should invoke with Next state when action send after state changed`() = runBlockingTest {
        // given
        val input = Channel<Action>()
        val output = mock<SendChannel<Triple<State, Command<*, Action>?, Effect?>>>()
        val businessLogic = mock<BusinessLogic> {
            on { invoke(State.Initial, Action.First) } doReturn just(State.Next)
            on { invoke(State.Next, Action.First) } doReturn just(State.Next)
        }
        val job = launch { strategy(input, output, State.Initial, businessLogic) }

        // when
        input.send(Action.First)
        input.send(Action.First)

        // then
        verify(output, times(2)).send(just<State, Action, Effect>(State.Next))
        job.cancelAndJoin()
    }

    @Test
    fun `should return when input channel closed`() = runBlockingTest {
        // given
        val input = Channel<Action>()
        val businessLogic = mock<BusinessLogic>()
        val job = launch { strategy(input, mock(), State.Initial, businessLogic) }

        // when
        input.close()

        // then
        assertThat(job.isCompleted).isTrue()
    }

    sealed class State {
        object Initial : State()
        object Next : State()
    }

    sealed class Action {
        object First : Action()
    }

    sealed class Effect

    interface BusinessLogic : (State, Action) -> Triple<State, Command<*, Action>?, Effect?>
}