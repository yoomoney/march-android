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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.reflect.KClass

class CommandWithReflectionNextActionTest {

    @Test
    fun print() {
        // given
        val expectedInputString = "input"
        val expectedActionClassString = Action::class.toString()
        val command = TestCommand(expectedInputString, Action::class)

        // when
        val string = command.toString()

        // then
        assertThat(string).isEqualTo("TestCommand(input=$expectedInputString, nextActionClass=$expectedActionClassString)")
    }

    @Test
    fun comparision() {
        // given
        val stringBuilder = StringBuilder("input").append(1)
        val command1 = TestCommand(stringBuilder.toString(), Action::class)
        val command2 = TestCommand(stringBuilder.toString(), Action::class)
        assert(command1.input !== command2.input)

        // when
        val result = command1 == command2

        // then
        assertThat(result).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should throw exception when Action have more than one constructor`() {
        // given
        @Suppress("unused")
        class ActionWithTwoConstructors {

            val value: String

            constructor(value: String) {
                this.value = value
            }

            constructor(value: Int) {
                this.value = value.toString()
            }
        }

        // when
        TestCommand("input", ActionWithTwoConstructors::class)

        // then assert that exception thrown
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should throw exception when Action's constructor have more than one parameter`() {
        // given
        @Suppress("unused")
        class ActionWithConstructorWithTwoParameters(value: String, other: Int) {
            val value: String = value + other
        }

        // when
        TestCommand("input", ActionWithConstructorWithTwoParameters::class)

        // then assert that exception thrown
    }

    data class Action(val response: String)

    data class TestCommand<ACTION : Any>(
        val input: String,
        val nextActionClass: KClass<ACTION>
    ) : CommandWithReflectionNextAction<String, ACTION>(String::class, nextActionClass)
}