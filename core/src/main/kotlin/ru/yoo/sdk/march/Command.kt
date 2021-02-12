package ru.yoo.sdk.march

import kotlin.reflect.KClass

interface Command<in R : Any, out ACTION : Any> {
    val transform: (R) -> ACTION
}

abstract class CommandWithNextObject<out ACTION : Any>(
    private val nextActionObject: ACTION
) : Command<Unit, ACTION> {
    final override val transform: (Unit) -> ACTION get() = { nextActionObject }
}

abstract class CommandWithReflectionNextAction<in R : Any, ACTION : Any>(
    returnClass: KClass<R>,
    nextActionClass: KClass<ACTION>
) : Command<R, ACTION> {

    final override val transform: (R) -> ACTION

    init {
        val constructor = nextActionClass.constructors.singleOrNull()
        require(constructor != null && constructor.parameters.singleOrNull()?.type?.classifier == returnClass) {
            "$nextActionClass should have single constructor with single parameter of type $returnClass"
        }
        transform = { constructor.call(it) }
    }
}