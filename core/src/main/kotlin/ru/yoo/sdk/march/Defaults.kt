package ru.yoo.sdk.march

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
     * Стратегия доставки результата работы бизнес-логики по-умолчанию
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