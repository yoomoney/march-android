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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

typealias BusinessLogic<STATE, ACTION, EFFECT> = (state: STATE, action: ACTION) -> Triple<STATE, Command<*, ACTION>?, EFFECT?>

typealias CommandProcessor<ACTION> = suspend (command: Command<*, ACTION>) -> ACTION

typealias BusinessLogicExecutionStrategy<STATE, ACTION, EFFECT> =
        suspend (
            actions: ReceiveChannel<ACTION>,
            output: SendChannel<Triple<STATE, Command<*, ACTION>?, EFFECT?>>,
            initialState: STATE,
            logic: BusinessLogic<STATE, ACTION, EFFECT>
        ) -> Unit

typealias CommandProcessorExecutionStrategy<ACTION> =
        suspend (
            commands: ReceiveChannel<Command<*, ACTION>?>,
            output: SendChannel<ACTION>,
            errors: SendChannel<Throwable>,
            commandProcessor: CommandProcessor<ACTION>
        ) -> Unit

typealias BusinessLogicResultDeliveryStrategy<STATE, ACTION, EFFECT> =
        suspend (
            businessLogicDispatcher: CoroutineDispatcher,
            input: ReceiveChannel<Triple<STATE, Command<*, ACTION>?, EFFECT?>>,
            sendState: (STATE) -> Unit,
            effects: SendChannel<EFFECT>,
            commands: SendChannel<Command<*, ACTION>?>
        ) -> Unit

suspend fun <STATE : Any, ACTION : Any, EFFECT : Any> BusinessLogicExecutionStrategyV1(
    input: ReceiveChannel<ACTION>,
    output: SendChannel<Triple<STATE, Command<*, ACTION>?, EFFECT?>>,
    initial: STATE,
    businessLogic: (STATE, ACTION) -> Triple<STATE, Command<*, ACTION>?, EFFECT?>
) {
    var state = initial

    for (action in input) {
        state = businessLogic(state, action)
            .also { output.send(it) }
            .first
    }
}

suspend fun <STATE : Any, ACTION : Any, EFFECT : Any> BusinessLogicExecutionStrategyV2(
    input: ReceiveChannel<ACTION>,
    output: SendChannel<Triple<STATE, Command<*, ACTION>?, EFFECT?>>,
    initial: STATE,
    businessLogic: (STATE, ACTION) -> Triple<STATE, Command<*, ACTION>?, EFFECT?>
) {
    // we are waiting for the results of the commands here not explicitly, because the result of the command goes to actions
    try {
        val firstAction = input.receive()
        coroutineScope {
            launchRuntime(
                initial = Out(
                    initial,
                    listOf(Effect.Input.Fun { firstAction })
                ),
                logic = { state, action ->
                    val result = businessLogic(state, action)
                    Out(
                        result.first,
                        listOf(
                            Effect.Input.Fun {
                                output.send(result)
                                input.receive()
                            }
                        )
                    )
                }
            ).join()
        }
    } catch (e: ClosedReceiveChannelException) {
        // just ignore for backward compatibility
    }
}


suspend fun <ACTION : Any> CommandProcessorExecutionStrategyV1(
    input: ReceiveChannel<Command<*, ACTION>?>,
    output: SendChannel<ACTION>,
    exceptions: SendChannel<Throwable>,
    commandExecutor: suspend (Command<*, ACTION>) -> ACTION
) {
    for (command in input) {
        runCatching {
            command?.also { command ->
                output.send(commandExecutor(command))
            }
        }.onFailure {
            if (it !is CancellationException) {
                exceptions.send(it)
            } else {
                throw it
            }
        }
    }
}

/**
 * The strategy for delivering the result of the business logic.
 *
 * - doesn't send duplicate states (compares states to [businessLogicDispatcher])
 * - send null-commands
 * - doesn't send null-effects
 */
suspend fun <STATE : Any, ACTION : Any, EFFECT : Any> BusinessLogicResultDeliveryStrategyV1(
    businessLogicDispatcher: CoroutineDispatcher,
    input: ReceiveChannel<Triple<STATE, Command<*, ACTION>?, EFFECT?>>,
    sendState: (STATE) -> Unit,
    effects: SendChannel<EFFECT>,
    commands: SendChannel<Command<*, ACTION>?>
) {
    var lastState: STATE? = null
    for ((state, command, effect) in input) {
        if (withContext(businessLogicDispatcher) { lastState != state }) {
            sendState(state)
            lastState = state
        }
        effect?.also { effects.send(it) }
        commands.send(command)
    }
}
