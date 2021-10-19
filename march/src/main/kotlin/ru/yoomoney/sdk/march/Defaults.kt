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

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

object Defaults {

    fun <STATE : Any, ACTION : Any, EFFECT : Any> businessLogicExecutionStrategy(): suspend (ReceiveChannel<ACTION>, SendChannel<Triple<STATE, Command<*, ACTION>?, EFFECT?>>, STATE, (STATE, ACTION) -> Triple<STATE, Command<*, ACTION>?, EFFECT?>) -> Unit =
        ::BusinessLogicExecutionStrategyV1

    fun <ACTION : Any> commandProcessorExecutionStrategy(): suspend (ReceiveChannel<Command<*, ACTION>?>, SendChannel<ACTION>, SendChannel<Throwable>, suspend (Command<*, ACTION>) -> ACTION) -> Unit =
        ::CommandProcessorExecutionStrategyV1

    /**
     * The delivery strategy of the result of the work of the business logic by default
     */
    fun <STATE : Any, ACTION : Any, EFFECT : Any> businessLogicResultDeliveryStrategy(): suspend (businessLogicDispatcher: CoroutineDispatcher, input: ReceiveChannel<Triple<STATE, Command<*, ACTION>?, EFFECT?>>, sendState: (STATE) -> Unit, effects: SendChannel<EFFECT>, commands: SendChannel<Command<*, ACTION>?>) -> Unit =
        ::BusinessLogicResultDeliveryStrategyV1

    fun <ACTION : Any> actionsChannel(log: (String) -> Unit): Channel<ACTION> =
        LoggingChannel(Channel(Channel.CONFLATED)) {
            if (BuildConfig.DEBUG) {
                log("Action:    $it")
            }
        }

    fun <STATE : Any, ACTION : Any, EFFECT : Any> businessLogicOutput(): Channel<Triple<STATE, Command<*, ACTION>?, EFFECT?>> =
        Channel(Channel.RENDEZVOUS)

    fun <ACTION : Any> commandsChannel(log: (String) -> Unit): Channel<Command<*, ACTION>?> =
        LoggingChannel(Channel(Channel.CONFLATED)) {
            if (BuildConfig.DEBUG) {
                log("Command:   ${it?.toString().orEmpty()}")
            }
        }

    fun <EFFECT> effectsChannel(log: (String) -> Unit): Channel<EFFECT> =
        LoggingChannel(Channel(Channel.CONFLATED)) {
            if (BuildConfig.DEBUG) {
                log("Effect:    $it")
            }
        }

    fun exceptionChannel(log: (String) -> Unit): Channel<Throwable> =
        LoggingChannel(Channel(Channel.CONFLATED)) {
            if (BuildConfig.DEBUG) {
                log("Exception: $it")
            }
        }

    fun <STATE> sendState(sendState: (STATE) -> Unit, log: (String) -> Unit): (STATE) -> Unit =
        LoggingFunction(sendState) {
            if (BuildConfig.DEBUG) {
                log("State:     $it")
            }
        }

    fun log(featureName: String): (Any?) -> Unit = {
        Log.d(featureName, it?.toString().orEmpty())
    }
}