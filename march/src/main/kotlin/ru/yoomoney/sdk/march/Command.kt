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