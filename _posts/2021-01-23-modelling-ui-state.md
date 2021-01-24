---
layout: post
title: Modelling UI State on Android
author: Stojan Anastasov
tags:
- android
- kotlin
- functional
- fp
- cardinality
---

The recommended approach from Google for Android development is holding the UI state in a `ViewModel` and having the `View` observe it. To achieve that one can use `LiveData`, `StateFlow`, `RxJava` or a similar tool. But how to model the UI state? Use a data class or a sealed class? Use one observable property or many? I will describe the tradeoffs between the approaches and present a tool to help you decide which one to use. This article is heavily inspired by [Types as Sets][types-as-sets] from the Elm guide.

![Architecture](/images/posts/arch.jpg) Photo by [Marc-Olivier Jodoin][mark] on [Unsplash][unsplash]

## Types as sets

By [Making Data Structure][make-ds] we can make sure the *possible values in code* exactly match the *valid values in real life*. Doing that helps to avoid a whole class of bugs related to invalid  data. To achieve that, first we need to understand  the relationship between Types and Sets.

We can think of Types as sets of values, they contain unique elements and there is no ordering between them. For example:

- `Nothing` - the empty set, it contains no elements
- `Unit` - the singleton set, it contains one element - `Unit`
- `Boolean` - contains the elements `true` and `false`
- `Int` - contains the elements: ... `-2`, `-1`, `0`, `1`, `2` ...
- `Float` - contains the elements: `0.1`, `0.01`, `1.0` ....
- `String` - contains the elements: `""`, `"a"`, `"b"`, `"Kotlin"`, `"Android"`, `"Hello world!"`...

So when you write: `val x: Boolean` it means `x` belongs to the set of `Boolean` values and can be either `true` or `false`.

## Cardinality

In Mathematics, [Cardinality][cardinality] is the measure of "number of elements" of a Set. For example the set of `Boolean` contains the elements `[true, false]` so it has a cardinality = 2.

Let's take a look at the cardinality of the sets mentioned above:

- `Nothing` - 0
- `Unit` - 1
- `Boolean` - 2
- `Short` - 65535
- `Int` - ∞
- `Float` - ∞
- `String`- ∞

**Note**: The cardinality of `Int` and `Float` is not exactly infinity, it's 2^32 however that is a huge number.

When building apps, we use built-in types and create custom types using constructs like data classes and sealed classes.

## Product Types (*)

One flavor of product types in Kotlin are `Pair` and `Triple`. Let's take a look at their cardinality:

- `Pair<Unit, Boolean>` - cardinality(Unit) * cardinality(Boolean) = 1 * 2 = 2
- `Pair<Boolean, Boolean>` - 2 * 2 = 4

`Pair<Unit, Boolean>` contains the elements: `Pair(Unit, false)` and `Pair(Unit, true)`. 

- `Triple<Unit, Boolean, Boolean`> = 1 * 2 * 2 = 4
- `Pair<Int, Int>` = cardinality(Int) * cardinality(Int) = ∞ * ∞ = ∞

Well, that escalated quickly.

When combining types with `Pair/Triple` their cardinalities multiply (hence the name Product Types).

`Pair/Triple` are the generic version of data classes with 2 and 3 properties respectively.

`data class User(val emailVerified: Boolean, val isAdmin: Boolean)` has the same cardinality as `Pair<Boolean, Boolean>`, 4. The elements are: `User(false, false)`, `User(false, true)`, `User(true, false)` and `User(true, true)`.

## Sum Types (+)

In Kotlin we use sealed classes to implement Sum types. When combining types using sealed classes, the total cardinality is equal to the sum of the cardinality of the members. Some examples are:

```kotlin
sealed class NotificationSetting
object Disabled : NotificationSettings()    // an object has one element -> cardinality = 1
data class Enabled(val pushEnabled: Boolean, val emailEnabled: Boolean) : NotificationSettings()

// cardinality = cardinality (Disabled)  + cardinality(Enabled)
// cardinality = 1 + (2 * 2)
// cardinality = 1 + 4 = 5

sealed class Location
object Unknown : Location()
data class Somewhere(val lat: Float, val lng: Float) : Location()

// cardinality = cardinality (Unknown)  + cardinality(Somewhere)
// cardinality = 1 + (∞ * ∞)
// cardinality = 1 + ∞ = ∞
```

### Nullable Types

Another way to model the `Location` type is using nullable types `data class Location(val lat: Float, val lng: Float)` and represent it as: `val location: Location?`. In this scenario we use `null` when the location is unknown. These two representations have the same cardinality and we can convert between them without any information loss. A few more examples:

- `Unit?` - cardinality = 2 (1 + cardinality(Unit))
- `Boolean?` - 3 (1 + cardinality(Boolean))

The elements of a nullable type `A?` are `null` + the elements of the original type `A`. The cardinality of a nullable type is 1 + the cardinality of the original type. 

### Enums

Enums are another way of representing Sum types in Kotlin:

```kotlin
enum class Color { RED, YELLOW, GREEN }
```

The cardinality of `Color` is equal to the number of elements, in this case 3. An alternative representation using a sealed class is:

```kotlin
sealed class Color
object Red : Color()
object Yellow : Color()
object Green : Color()
```

and it has the same cardinality - 3.

## Why does it matter

Thinking about Types as Sets and their cardinality helps with data modelling to avoid a whole class of bugs related to Invalid Data. Let's say we are modelling a traffic light. The possible colors are: red, yellow and green. To represent them in code we could use:

- a `String` where `"red"`, `"yellow"` and `"green"` are the valid options and everything else is invalid data. But then the user types `"rad"` instead of red and we have an issue. Or `"yelow"` or `"RED"`. Should all functions validate their arguments? Should all functions have tests? The root cause of the issue here is cardinality. A String has cardinality of ∞ while the our problem has 3. There are ∞ - 3 possible invalid values.

- a data class `data class Color(val isRed: Boolean, val isYellow: Boolean, val isGreen: Boolean)` - here `Color(true, false, false)` represents red. Yet this still leaves room for invalid data e.g. `Color(true, true, true)`. Again you would need checks and test to ensure values are valid. The cardinality of the data class `Color` is 8 and it has 8 - 3 = 5 illegal values. It's much better then the `String`, but we can do better.

- an enum - `enum class Color { RED, YELLOW, GREEN }` - this has a cardinality = 3. It matches exactly the possible  valid values of the problem. Illegal values are now impossible, so there is no need for tests that check data validity.

By modelling the data in a way that rules out illegal values, the resulting code will also end up shorter, more clear and easier to test. 

> Make sure the set of possible values in code match the set of valid values of the problem and a lot of issues disappear.

## Exposing State from a ViewModel

The task: 

Build an app that calls a traffic light endpoint and shows the color of the traffic light (red, yellow or green). During the network call show a `ProgressBar`. On success show a `View` with the color and in case of an error show a `TextView` with a generic text. Only one view is visible at a time and there is no possibility to retry errors.

```kotlin
class TrafficLightViewModel : ViewModel() {

    val state: LiveData<TrafficLightState> = TODO()
}
```

I will represent the color as an enum with three values. How should the type `TrafficLightState` look like?

```kotlin
data class TrafficLightState(
    val isLoading: Boolean, 
    val isError: Boolean, 
    val color: Color?
)
```

one way is a data class with three properties. Yet this has possible invalid states e.g. `TrafficLightState(true, true, Color.RED)`. Both error and loading are true. Showing both the `ProgressBar` and the error `TextView` should not be possible in the UI. We also have a Color which is impossible in case of an error. The cardinality of `TrafficLightState` is 2 (Boolean) * 2 (Boolean) * 4 (Color?) = 16.

What about using many observable properties?

```kotlin
class TrafficLightViewModel : ViewModel() {

    val loading: LiveData<Boolean> = TODO()

    val error: LiveData<Boolean> = TODO()

    val color: LiveData<Color?> = TODO()
}
```

This has a cardinality of 2 (Boolean?) * 2 (Boolean?) * 4 (Color?) = 16. it has the same cardinality as the data class approach and enables illegal values.

Another approach is using a sealed class:

```kotlin
sealed class TrafficLightState
object Loading : TrafficLightState()
object Error : TrafficLightState()
data class Success(val color: Color) : TrafficLightState()
```

which has a cardinality of 1 (Loading) + 1 (Error) + 3 (Success) = 5 which exactly matches the possible states of the problem:

- during the network call: `Loading` and show the `ProgressBar`
- if the network call fails: `Error` and show a `TextView`
- on success: `Success` and show a view with the corresponding color

These are all valid approaches and when designing your UI state. But only one of them matches exactly the possible valid states of the problem. To eliminate bugs, simplify code and reduce the number of tests, use that one.

## Conclusion

To model problems in code we use built-in types like: `Boolean`, `Int`, `String`... and in Kotlin we also create custom types using constructs like data class and sealed class. When modelling a UI state pick the type (or combination of types) that has the same number of values as the possible states of the problem. This results in simpler and more robust code.

Thanks [Gaël][gael] for the review.

[types-as-sets]: https://guide.elm-lang.org/appendix/types_as_sets.html
[make-ds]: https://www.youtube.com/watch?v=x1FU3e0sT1I
[cardinality]: https://en.wikipedia.org/wiki/Cardinality
[mark]: https://unsplash.com/@marcojodoin
[unsplash]: https://unsplash.com/s/photos/architecture
[gael]: https://twitter.com/GaelMarhic
