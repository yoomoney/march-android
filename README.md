# march

[![GitHub release](https://img.shields.io/github/release/yoomoney-tech/march-android.svg)](https://github.com/yoomoney-tech/march-android/releases/)
[![MIT license](https://img.shields.io/badge/License-MIT-blue.svg)](https://lbesson.mit-license.org/)

## Gradle

march доступна в [Maven Central](https://search.maven.org/search?q=g:ru.yoomoney.sdk%20AND%20a:march).

Добавьте следующее в свой файл build.gradle для использования:

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation 'ru.yoomoney.sdk:march:1.0.2'
}
```

## Тезаурус

**Action** - одно воздействие на систему.

**State** - состояние системы в каждый момент времени.

**Effect** - событие, которое может произойти, в результате обработки **Action**.

**Command** - данные для выполнения какого-то запроса к "внешним" системам. Например, запросы в сеть или БД, копирование в буффер обмена.

**BusinessLogic** - логика фичи, описывает переходы между **State** на основе **Action**.
Представлена, как функция `( State, Action ) -> Triple< State, Command?, Effect? >`

**CommandProcessor** - исполнитель для **Command**.
Представлен, как функция `( Command ) -> Action`

**Initial State** - начальное состояние системы.

**Domain model** - структура данных, которая описывает сущности в рамках фичи.

## Как описать фичу

Сначала надо нарисовать схему возможных состояний и переходов между ними.
Рекомендуется использовать овалы для описания State и стрелки для описания Action.
Для описания Effect и Command можно делать подписи на соответствующих стрелках.

При переносе состояний, действий и эффектов в код, надо создать объект с названием фичи, и уже внутри него описывать все остальные типы. **На этом этапе описываются только сущности, но не данные.** Все sealed class содержат только объекты.

```kotlin
object MyFeature {
    sealed class State {
        object First : State()
        object Second : State()
    }

    sealed class Action {
        object First : Action()
        object Second : Action()
    }

    sealed class Effect {
        object First : Effect()
        object Second : Effect()
    }
}
```

Теперь, когда есть сущности, можно написать заглушку логики и тесты на нее. Для создания тестов есть специальный генератор `generateBusinessLogicTests()`.
В него передаются три функции:

* `generateState: (KClass<out STATE>) -> STATE` - генерация объекта State, на основе класса.

```kotlin
{ kClass: KClass<out State> ->
    when (kClass) {
        // создаем состояние через конструктор
        SomeState::class -> SomeState(1, 2, 3)
        // по-умолчанию пытаемся взять объект и падаем, если не получается
        else -> it.objectInstance ?: error(it)
    }
}
```

* `generateAction: (KClass<out ACTION>) -> ACTION` - генерация объекта Action, на основе класса.

```kotlin
{ kClass: KClass<out Action> ->
    when (kClass) {
        // создаем действие через конструктор
        SomeAction::class -> SomeAction(1, 2, 3)
        // по-умолчанию пытаемся взять объект и падаем, если не получается
        else -> it.objectInstance ?: error(it)
    }
}
```

* `generateExpectation: (STATE, ACTION) -> Triple<STATE, Any?, EFFECT?>` - генерация ожидаемых результатов.

```kotlin
{ state: STATE, action: ACTION ->
    when (state to action) {
        // создаем проверку перехода State1 + Action1 = State2
        State1() to Action1() -> just(State2())

        // создаем проверку перехода State2 + Action2 = State3 + Effect
        State2() to Action2() -> State3() with Effect

        // создаем проверку перехода State3 + Action3 = State4 + Command(Action5)
        State3() to Action3() -> State4() with SomeCommand(params, Action4())

        // создаем проверку перехода State4 + Action4 = State5 + CustomCommandMatcher()
        // CustomCommandMatcher нужен для проверки Command с лямбдами, чтобы проверить все без них
        // CustomCommandMatcher - наследник BaseMatcher<T>
        State4() to Action4() -> State5() with CustomCommandMatcher(params) { Action6(it) }

        // по-умолчанию действие не меняет состояние и не вызывает эффектов
        else -> just<State, Action, Effect>(state)
    }
}
```

Пример:

```kotlin

class MyFeatureBusinessLogic : BusinessLogic<MyFeature.State, MyFeature.Action, MyFeature.Effect> {
    override fun invoke(
        state: MyFeature.State,
        action: MyFeature.Action
    ): Triple<MyFeature.State, Command<*, MyFeature.Action>?, MyFeature.Effect?> {
        return when (state) {
            MyFeature.State.First -> TODO()
            MyFeature.State.Second -> TODO()
        }
    }

}

@RunWith(Parameterized::class)
class MyFeatureBusinessLogicTest(
    @Suppress("unused") val testName: String,
    val state: MyFeature.State,
    val action: MyFeature.Action,
    val expected: Triple<MyFeature.State, Any?, MyFeature.Effect?>
) {
    companion object {
        @[Parameterized.Parameters(name = "{0}") JvmStatic]
        fun data(): Collection<Array<out Any>> {
            return generateBusinessLogicTests<MyFeature.State, MyFeature.Action>(
                generateState = {
                    when (it) {
                        else -> it.objectInstance ?: error(it)
                    }
                },
                generateAction = {
                    when (it) {
                        else -> it.objectInstance ?: error(it)
                    }
                },
                generateExpectation = { state, action ->
                    when (state to action) {
                        MyFeature.State.First to MyFeature.Action.First -> just(MyFeature.State.Second)

                        MyFeature.State.Second to MyFeature.Action.Second ->
                            MyFeature.State.Second with MyFeature.Effect.First

                        // по-умолчанию действие не меняет состояние и не вызывает эффектов
                        else -> just<MyFeature.State, MyFeature.Action, MyFeature.Effect>(state)
                    }
                }
            )
        }
    }

    private val logic = MyFeatureBusinessLogic()

    @Test
    fun test() {
        // given arguments in constructor

        // when
        val actual = logic(state, action)

        // then
        assertThat(actual.first, CoreMatchers.equalTo(expected.first))

        if (actual.second is Matcher<*>)
            assertThat(actual.second, expected.second?.let { Is.`is`(it) } ?: nullValue())
        else
            assertThat(actual.second, CoreMatchers.equalTo(actual.second))

        assertThat(actual.third, CoreMatchers.equalTo(expected.third))
    }
}
```

После того, как описаны тесты для логики, надо ее реализовать так, чтобы тесты проходили.

Теперь, когда реализована логика, можно перезодить реализации команд.
Каждая команда представляет собой две сущности Command - описание данных для выполнения команды и функции преобразования результата к Action, и CommandProcessor - сущность, которая может выполнять эти Command и создержит в себе зависимости для их выполнения.
Также как и в случае с логикой, для CommandProcessor необзодимо написать тесты.
**Убедитесь, что Command и CommandProcessor не имеют жесткой привязки к классам фичи, иначе их будет сложно переисполоьховать в будущем.** Исключением является legacy-код, в котором уже есть реализованные мезанизмы работы с данными и их просто надо обернуть в CommandProcessor, чтобы настроить интеграцию с mArch.

Когда реализована логика и команды, можно приступать к интеграции с UI.
Для этого, в самом простом случае, можно использовать `ViewModelStoreOwner.RuntimeViewModelProvider()`, **но это не рекомендуется, потому что тогда Fragment будет жестко привязан ко всем зависомостям и его не удастся протестировать.**
Лучше получать `ViewModelProvider` или `ViewModelProvider.Factory` снаружи, как зависимость, из Activity или через DI.

```kotlin
// в DI-классе
val factory = RuntimeViewModelFactory(
    featureName = "MyFeature",
    initial = State1() with SomeCommand(),
    businessLogic = MyFeatureBusinessLogic(), // или ::myFeatureBusinessLogic, если логика описана функцией,
    commandProcessor = MyFeatureCommandProcessor() // CommandProcessor должен присутствовать обязательно, хотя бы как заглушка
)

// в точке внедрения зависимости
fragment.factory = factory

// в Fragment

internal typealias MyFeatureViewModel = RuntimeViewModel<MyFeature.State, MyFeature.Action, MyFeature.Effect>

class MyFeatureFragment : Fragment() {

    lateinit var factory: ViewModelProvider.Factory

    private val viewModel: MyFeatureViewModel by lazy { ViewModelProvider(this, factory).get(RuntimeViewModel::class.java) as MyFeatureViewModel }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.observe(
            lifecycleOwner = viewLifecycleOwner,
            onState = ::showState,
            onEffect = ::showEffect,
            onFail = ::showFail
        )
    }

}
```

По умолчанию логирование логики выключено параметром `isLoggingEnable`. Для включения логирования можно переопределить флаг `isLoggingEnable` на `true` или создать собственный логгер, передав в его в параметры `RuntimeViewModel`.

## Нерешенные вопросы

* Сохранение состояния
* Восстановление при падении CommandProcessor
* написать подробнее про Command, его наследников и зачем они нужны