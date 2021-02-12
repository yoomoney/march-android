package ru.yoo.sdk.march

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

@ExperimentalCoroutinesApi
class LaunchCommandExecutorTest {

    val commandExecutor = mock<CommandExecutor> {
        onBlocking { invoke(TestCommand) } doReturn Action
    }

    @Test
    fun `should call commandExecutor when command send`() = runBlockingTest {
        // given
        val input = Channel<Command<*, Action>>()
        val job = launch { CommandProcessorExecutionStrategyV1(input, Channel(), Channel()) { commandExecutor(it) } }

        // when
        input.send(TestCommand)

        // then
        verify(commandExecutor).invoke(TestCommand)
        job.cancelAndJoin()
    }

    @Test
    fun `should send result to output channel`() = runBlockingTest {
        // given
        val input = Channel<Command<*, Action>>()
        val output = Channel<Action>()
        val job = launch { CommandProcessorExecutionStrategyV1(input, output, Channel()) { commandExecutor(it) } }

        // when
        input.send(TestCommand)

        // then
        assertThat(output.receive()).isEqualTo(Action)
        job.cancelAndJoin()
    }

    @Test
    fun `should send exception to exceptions channel when commandExecutor failed`() = runBlockingTest {
        // given
        val expectedException = IllegalStateException("test")
        val input = Channel<Command<*, Action>>()
        val exceptions = Channel<Throwable>()
        commandExecutor.stub {
            onBlocking { invoke(TestCommand) } doThrow expectedException
        }
        val job = launch { CommandProcessorExecutionStrategyV1(input, Channel(), exceptions) { commandExecutor(it) } }

        // when
        input.send(TestCommand)

        // then
        assertThat(exceptions.receive()).isEqualTo(expectedException)
        job.cancelAndJoin()
    }

    @Test
    fun `should cancel coroutine when commandExecutor throws CancellationException`() = runBlockingTest {
        // given
        val input = Channel<Command<*, Action>>()
        val exceptions = Channel<Throwable>()
        val output = Channel<Action>()
        val job = launch {
            CommandProcessorExecutionStrategyV1(input, output, exceptions) {
                coroutineScope {
                    cancel()
                    Action
                }
            }
        }

        // when
        input.send(TestCommand)

        // then
        assertThat(output.poll()).isNull()
        assertThat(exceptions.poll()).isNull()
        assertThat(job.isCompleted).isTrue()
    }

    @Test
    fun `should do nothing when null sent as command`() = runBlockingTest {
        // given
        val input = Channel<Command<*, Action>?>()
        val output = mock<SendChannel<Action>>()
        val exceptions = mock<Channel<Throwable>>()
        val job = launch { CommandProcessorExecutionStrategyV1(input, output, exceptions) { commandExecutor(it) } }

        // when
        input.send(null)

        // then
        verifyZeroInteractions(commandExecutor, output, exceptions)
        job.cancelAndJoin()
    }

    @Test
    fun `should return when input channel is canceled`() = runBlockingTest {
        // given
        val input = Channel<Command<*, Action>?>()
        val job = launch {
            CommandProcessorExecutionStrategyV1(
                input,
                mock(),
                mock<Channel<Throwable>>()
            ) { commandExecutor(it) }
        }

        // when
        input.close()

        // then
        assertThat(job.isCompleted).isTrue()
    }

    object Action
    object TestCommand : Command<Any, Action> {
        override val transform: (Any) -> Action = { Action }
    }

    interface CommandExecutor {
        suspend operator fun invoke(command: Command<*, Action>?): Action
    }
}