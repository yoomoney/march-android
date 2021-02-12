package ru.yoo.sdk.march

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

sealed class Source<out A> {

    abstract val key: Any?

    sealed class Run<out A> : Source<A>() {
        class Func<A>(
            override val key: Any?,
            val func: suspend () -> A
        ) : Run<A>() {
            constructor(func: suspend () -> A) : this(null, func)
        }

        class Flow<A>(
            override val key: Any?,
            val flow: kotlinx.coroutines.flow.Flow<A>
        ) : Run<A>() {
            constructor(flow: kotlinx.coroutines.flow.Flow<A>) : this(null, flow)
        }
    }

    class Cancel<A>(
        override val key: Any
    ) : Source<A>()
}

sealed class Effect<out T> {
    sealed class Input<out T> : Effect<T>() {
        class Fun<out T>(val func: suspend () -> T) : Input<T>()
        class Sub<out T>(val key: Any, val flow: Flow<T>) : Input<T>()
    }

    class Output(val func: suspend () -> Unit) : Effect<Nothing>()
    class Cancel(val key: Any) : Effect<Nothing>()
}

class Out<out STATE, out INPUT>(
    val state: STATE,
    val sources: List<Effect<INPUT>>
) {
    companion object {
        operator fun <STATE, INPUT> invoke(state: STATE, builder: Builder<STATE, INPUT>.() -> Unit): Out<STATE, INPUT> {
            return Builder<STATE, INPUT>(state).apply(builder).build()
        }

        fun <STATE, INPUT> skip(state: STATE, source: suspend () -> INPUT): Out<STATE, INPUT> {
            return Out(state, listOf(Effect.Input.Fun(source)))
        }
    }

    class Builder<out STATE, INPUT>(
        val state: STATE,
        val sources: MutableList<Effect<INPUT>> = mutableListOf()
    ) {
        fun build(): Out<STATE, INPUT> = Out(state, sources)
    }
}

fun <STATE, INPUT> Out.Builder<STATE, INPUT>.input(func: suspend () -> INPUT) {
    sources.add(Effect.Input.Fun(func))
}

fun <STATE, INPUT> Out.Builder<STATE, INPUT>.output(func: suspend () -> Unit) {
    sources.add(Effect.Output(func))
}

typealias Logic<STATE, INPUT> = (STATE, INPUT) -> Out<STATE, INPUT>

fun <INPUT : Any> CoroutineScope.createSourcesListener(
    noKeysScope: CoroutineScope = this + SupervisorJob(),
    withKeysScope: CoroutineScope = this + SupervisorJob()
): (Map<Any, Job>, List<Effect<INPUT>>, SendChannel<INPUT>) -> Map<Any, Job> = { running, effects, inputs ->
    // launch all outputs
    launch { effects.filterIsInstance<Effect.Output>().forEach { it.func() } }

    // cancel inputs without keys
    noKeysScope.coroutineContext.cancelChildren(CancellationException("New jobs arrived"))

    // start listen functions
    effects.filterIsInstance<Effect.Input.Fun<INPUT>>().forEach { source ->
        noKeysScope.launch { inputs.send(source.func()) }
    }

    // cancel flows
    val cancelledJobs = effects.mapNotNull {
        when (it) {
            is Effect.Input.Sub -> running[it.key]?.apply { cancel(CancellationException("New func with same key ${it.key}")) }
            is Effect.Cancel -> running[it.key]?.apply { cancel(CancellationException("Cancelled by key ${it.key}")) }
            is Effect.Input.Fun -> null
            is Effect.Output -> null
        }
    }

    // remove cancelled flows
    val leftRunning = running - cancelledJobs

    // add new started flows
    leftRunning + effects
        .filterIsInstance<Effect.Input.Sub<INPUT>>()
        .map { it.key to withKeysScope.launch { it.flow.collect { input -> inputs.send(input) } } }
}

/*
 * Запуск логики
 */
fun <STATE : Any, INPUT : Any> CoroutineScope.launchRuntime(
    initial: Out<STATE, INPUT>,
    logic: Logic<STATE, INPUT>,
    inputChannel: Channel<INPUT> = Channel(Channel.CONFLATED),
    // todo может возвращать Flow или Deferred или просто сделать эту функцию suspend
    // но тогда мы не сможем следить за выполняющимися Job, как делаем это в случае с Map
    listenSources: (running: Map<Any, Job>, effects: List<Effect<INPUT>>, inputs: SendChannel<INPUT>) -> Map<Any, Job> = createSourcesListener()
): Job = launch {
    var state = initial.state
    var runningSources = listenSources(emptyMap(), initial.sources, inputChannel)

    for (input in inputChannel) {
        val new = logic(state, input)

        state = new.state
        runningSources = listenSources(runningSources, new.sources, inputChannel)

        if (new.sources.none { it is Effect.Input } && runningSources.isEmpty()) {
            inputChannel.cancel()
        }
    }
}

suspend fun suspendUntilCancelled(): Nothing = suspendCancellableCoroutine<Nothing> { }

fun <STATE : Any, INPUT : Any> androidShowState(
    main: MainCoroutineDispatcher,
    showState: (STATE) -> Unit,
    actions: ReceiveChannel<INPUT>
): suspend (STATE) -> INPUT = { state: STATE ->
    withContext(main) { showState(state) }
    actions.receive()
}

fun <EFFECT : Any> showEffect(
    effects: SendChannel<EFFECT>
): suspend (EFFECT) -> Nothing = { effect: EFFECT ->
    effects.send(effect)
    suspendUntilCancelled()
}
