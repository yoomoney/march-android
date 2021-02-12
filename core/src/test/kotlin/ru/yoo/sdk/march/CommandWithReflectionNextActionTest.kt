package ru.yoo.sdk.march

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