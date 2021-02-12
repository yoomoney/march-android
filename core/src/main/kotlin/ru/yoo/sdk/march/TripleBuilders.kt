package ru.yoo.sdk.march

fun <STATE : Any, ACTION : Any, EFFECT : Any> just(state: STATE) =
    Triple<STATE, Command<*, ACTION>?, EFFECT?>(state, null, null)

infix fun <STATE : Any, ACTION : Any, EFFECT : Any> STATE.with(effect: EFFECT) =
    Triple<STATE, Command<*, ACTION>?, EFFECT>(this, null, effect)

infix fun <STATE : Any, ACTION : Any, EFFECT : Any> STATE.with(command: Command<*, ACTION>) =
    Triple<STATE, Command<*, ACTION>?, EFFECT?>(this, command, null)