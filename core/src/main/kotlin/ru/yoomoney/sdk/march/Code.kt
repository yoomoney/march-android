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

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import ru.yoomoney.sdk.march.*
import kotlin.reflect.KClass

class RuntimeViewModel<STATE : Any, ACTION : Any, EFFECT : Any>(
    val states: LiveData<STATE>,
    val effects: ReceiveChannel<EFFECT>,
    val exceptions: ReceiveChannel<Throwable>,
    val actions: SendChannel<ACTION>
) : ViewModel() {

    fun handleAction(action: ACTION) {
        actions.offer(action)
    }
}

fun <STATE : Any, ACTION : Any, EFFECT : Any> CoroutineScope.launchRuntime(
    initial: Triple<STATE, Command<*, ACTION>?, EFFECT?>,
    businessLogic: BusinessLogic<STATE, ACTION, EFFECT>,
    commandProcessor: CommandProcessor<ACTION>,

    businessLogicExecutionStrategy: BusinessLogicExecutionStrategy<STATE, ACTION, EFFECT>,
    commandProcessorExecutionStrategy: CommandProcessorExecutionStrategy<ACTION>,
    businessLogicResultDeliveryStrategy: BusinessLogicResultDeliveryStrategy<STATE, ACTION, EFFECT>,

    actions: Channel<ACTION>,
    businessLogicOutput: Channel<Triple<STATE, Command<*, ACTION>?, EFFECT?>>,

    commands: Channel<Command<*, ACTION>?>,
    effects: Channel<EFFECT>,
    exceptions: Channel<Throwable>,
    sendState: (STATE) -> Unit,
    businessLogicDispatcher: CoroutineDispatcher,
    commandExecutorDispatcher: CoroutineDispatcher
) {
    launch(businessLogicDispatcher) {
        businessLogicExecutionStrategy(actions, businessLogicOutput, initial.first, businessLogic)
    }
    launch(commandExecutorDispatcher) {
        commandProcessorExecutionStrategy(commands, actions, exceptions, commandProcessor)
    }
    launch {
        businessLogicResultDeliveryStrategy(businessLogicDispatcher, businessLogicOutput, sendState, effects, commands)
    }
    launch { businessLogicOutput.send(initial) }
}

fun <STATE : Any, ACTION : Any, EFFECT : Any> RuntimeViewModel(
    featureName: String,
    initial: Triple<STATE, Command<*, ACTION>?, EFFECT?>,
    businessLogic: BusinessLogic<STATE, ACTION, EFFECT>,
    commandProcessor: CommandProcessor<ACTION>,

    log: (Any?) -> Unit = Defaults.log(featureName),

    businessLogicExecutionStrategy: BusinessLogicExecutionStrategy<STATE, ACTION, EFFECT> =
        Defaults.businessLogicExecutionStrategy(),
    commandProcessorExecutionStrategy: CommandProcessorExecutionStrategy<ACTION> =
        Defaults.commandProcessorExecutionStrategy(),
    businessLogicResultDeliveryStrategy: BusinessLogicResultDeliveryStrategy<STATE, ACTION, EFFECT> =
        Defaults.businessLogicResultDeliveryStrategy(),

    actions: Channel<ACTION> = Defaults.actionsChannel(log),
    businessLogicOutput: Channel<Triple<STATE, Command<*, ACTION>?, EFFECT?>> = Defaults.businessLogicOutput(),
    commands: Channel<Command<*, ACTION>?> = Defaults.commandsChannel(log),
    states: MutableLiveData<STATE> = MutableLiveData(),
    effects: Channel<EFFECT> = Defaults.effectsChannel(log),
    exceptions: Channel<Throwable> = Defaults.exceptionChannel(log),
    sendState: (STATE) -> Unit = Defaults.sendState(states::setValue, log),

    businessLogicDispatcher: CoroutineDispatcher = Dispatchers.Default,
    commandExecutorDispatcher: CoroutineDispatcher = Dispatchers.IO
): RuntimeViewModel<STATE, ACTION, EFFECT> = RuntimeViewModel(states, effects, exceptions, actions).apply {
    viewModelScope.launchRuntime(
        initial = initial,
        businessLogic = businessLogic,
        commandProcessor = commandProcessor,
        businessLogicExecutionStrategy = businessLogicExecutionStrategy,
        commandProcessorExecutionStrategy = commandProcessorExecutionStrategy,
        businessLogicResultDeliveryStrategy = businessLogicResultDeliveryStrategy,
        actions = actions,
        businessLogicOutput = businessLogicOutput,
        commands = commands,
        effects = effects,
        exceptions = exceptions,
        sendState = sendState,
        businessLogicDispatcher = businessLogicDispatcher,
        commandExecutorDispatcher = commandExecutorDispatcher
    )
}

class RuntimeViewModelDependencies<STATE : Any, ACTION : Any, EFFECT : Any>(
    val showState: suspend (STATE) -> ACTION,
    val showEffect: suspend (EFFECT) -> Nothing,
    val source: suspend () -> ACTION
)

fun <STATE : Any, ACTION : Any, EFFECT : Any> RuntimeViewModel(
    featureName: String,
    initial: RuntimeViewModelDependencies<STATE, ACTION, EFFECT>.() -> Out<STATE, ACTION>,
    logic: RuntimeViewModelDependencies<STATE, ACTION, EFFECT>.() -> Logic<STATE, ACTION>,

    log: (Any?) -> Unit = Defaults.log(featureName),

    actions: Channel<ACTION> = Defaults.actionsChannel(log),
    states: MutableLiveData<STATE> = MutableLiveData(),
    effects: Channel<EFFECT> = Defaults.effectsChannel(log),
    exceptions: Channel<Throwable> = Defaults.exceptionChannel(log),

    sendState: (STATE) -> Unit = Defaults.sendState(states::setValue, log),

    mainCoroutineDispatcher: MainCoroutineDispatcher = Dispatchers.Main,

    runtimeViewModelDependencies: RuntimeViewModelDependencies<STATE, ACTION, EFFECT> = RuntimeViewModelDependencies(
        androidShowState(mainCoroutineDispatcher, sendState, actions),
        showEffect(effects),
        { actions.receive() }
    )
): RuntimeViewModel<STATE, ACTION, EFFECT> = RuntimeViewModel(states, effects, exceptions, actions).apply {
    (viewModelScope + Dispatchers.Default).launchRuntime(
        runtimeViewModelDependencies.initial(),
        runtimeViewModelDependencies.logic(),
        actions
    )
}

fun <STATE : Any, ACTION : Any, EFFECT : Any> RuntimeViewModelFactory(
    featureName: String,
    initial: Triple<STATE, Command<*, ACTION>?, EFFECT?>,
    businessLogic: (STATE, ACTION) -> Triple<STATE, Command<*, ACTION>?, EFFECT?>,
    commandProcessor: suspend (Command<*, ACTION>) -> ACTION
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            check(modelClass == RuntimeViewModel::class.java) {
                "Can't create ${modelClass.name}. Only ViewModels of type ${RuntimeViewModel::class.java.name} can be created"
            }
            @Suppress("UNCHECKED_CAST")
            return RuntimeViewModel(
                featureName = featureName,
                initial = initial,
                businessLogic = businessLogic,
                commandProcessor = commandProcessor
            ) as T
        }
    }
}

fun <STATE : Any, ACTION : Any, EFFECT : Any> RuntimeViewModelFactory(
    featureName: String,
    initial: RuntimeViewModelDependencies<STATE, ACTION, EFFECT>.() -> Out<STATE, ACTION>,
    logic: RuntimeViewModelDependencies<STATE, ACTION, EFFECT>.() -> Logic<STATE, ACTION>
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            check(modelClass == RuntimeViewModel::class.java) {
                "Can't create ${modelClass.name}. Only ViewModels of type ${RuntimeViewModel::class.java.name} can be created"
            }
            @Suppress("UNCHECKED_CAST")
            return RuntimeViewModel(
                featureName = featureName,
                initial = initial,
                logic = logic
            ) as T
        }
    }
}

fun <STATE : Any, ACTION : Any, EFFECT : Any> ViewModelStoreOwner.RuntimeViewModelProvider(
    featureName: String,
    initial: Triple<STATE, Command<*, ACTION>?, EFFECT?>,
    businessLogic: (STATE, ACTION) -> Triple<STATE, Command<*, ACTION>?, EFFECT?>,
    commandProcessor: suspend (Command<*, ACTION>) -> ACTION
): ViewModelProvider {
    return ViewModelProvider(this, RuntimeViewModelFactory(featureName, initial, businessLogic, commandProcessor))
}

fun <STATE : Any, ACTION : Any, EFFECT : Any> ViewModelStoreOwner.RuntimeViewModelProvider(
    featureName: String,
    initial: RuntimeViewModelDependencies<STATE, ACTION, EFFECT>.() -> Out<STATE, ACTION>,
    logic: RuntimeViewModelDependencies<STATE, ACTION, EFFECT>.() -> Logic<STATE, ACTION>
): ViewModelProvider {
    return ViewModelProvider(this, RuntimeViewModelFactory(featureName, initial, logic))
}

inline fun <reified STATE : Any, reified ACTION : Any> generateBusinessLogicTests(
    generateState: (KClass<out STATE>) -> STATE,
    generateAction: (KClass<out ACTION>) -> ACTION,
    generateExpectation: (STATE, ACTION) -> Any
): Collection<Array<out Any>> =
    STATE::class.sealedSubclasses
        .map(generateState)
        .flatMap { state -> ACTION::class.sealedSubclasses.map(generateAction).map { state to it } }
        .map { (state, action) ->
            arrayOf(
                "${state.javaClass.simpleName} ${action.javaClass.simpleName}",
                state,
                action,
                generateExpectation(state, action)
            )
        }


fun <E> ReceiveChannel<E>.observe(lifecycleOwner: LifecycleOwner, onElement: (E) -> Unit) {
    lifecycleOwner.lifecycleScope.launchWhenResumed {
        for (e in this@observe) {
            onElement(e)
        }
    }
}

fun <STATE : Any, ACTION : Any, EFFECT : Any> RuntimeViewModel<STATE, ACTION, EFFECT>.observe(
    lifecycleOwner: LifecycleOwner,
    onState: (STATE) -> Unit,
    onEffect: (EFFECT) -> Unit,
    onFail: (Throwable) -> Unit
) {
    states.observe(lifecycleOwner, Observer { it?.also(onState) })
    effects.observe(lifecycleOwner, onEffect)
    exceptions.observe(lifecycleOwner, onFail)
}