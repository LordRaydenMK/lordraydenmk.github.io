---
layout: post
title: Side Effects and Composition
author: Stojan Anastasov
tags:
- kotlin
- functional
- arrowkt
- fp
cover_image: /images/posts/lego.jpg
---

![Compose code as it was lego](/images/posts/lego.jpg)
*Photo by [Ryan Yoo][ryan] on [Unsplash][unsplash]*

Functional Programing is amazing. It limits the things you can do (no nulls, no exceptions, no side effects...) and in return you get some benefits. Some benefits of FP are easy to explain and some not so much. I have been playing with FP for more than a year and only few weeks ago, when I started reading [Functional Programming in Scala][fp-scala], I found an amazing example the benefits of pure functions.

## The problem

Building a software for a Coffee Shop. For the initial MVP the requirements are:

- Sell coffee
- Pay with a card

so the first draft of the code looks like this:


```kotlin

import coffee.*

fun buyCoffee(cc: CreditCard): Coffee {
    val cup = Coffee()
    cc.charge(cup.price)
    return cup
}
```

The `buyCoffee` function receives a `CreditCard` as an input and returns a `Coffee` as an output. It creates a `Coffee` object, then executes a charge and returns the `Coffee` object.

## Testability

The call `cc.charge` uses an SDK/API to talk to the credit card company. It involves authorization, network call(s), persisting a record in the DB. The `buyCoffee` function returns a `Coffee` and the charging happens on the side hence the terms "side effect".

The side effect makes `buyCoffee` hard to test in isolation. It would be nice (and cheaper) to run tests without actually talking to the credit card company and doing an actual charge. One could also argue that a `CreditCard` should not know how it's being charged.

The testability problem can be fixed using Dependency Injection.

```kotlin

fun buyCoffee(cc: CreditCard, p: Payments): Coffee {
    val cup = Coffee()
    p.charge(cc, cup.price)
    return cup
}
```

The logic of charging the credit card is extracted into the `Payments` object. In production code the real `Payments` implementation is used and a mock version is injected for tests.

## New requirements

After a few weeks in business new requirements arrive. Some people buy more than one coffee so the next version of the software has additional requirements:

- Buying N coffees
- Single charge per Credit Card

## Composition

The problem of buying a single coffee is already solved with `buyCoffee`. It would be nice to reuse the code for one coffee to buy multiple coffees. The code is complex (could be), tested and debugged. Reusing the existing solution would also save time.

## Buying multiple coffees

The first draft of `buyCoffees` looks like this:

```kotlin

fun buyCoffees(
    cc: CreditCard,
    p: Payments,
    n: Int
): List<Coffee> = List(n) { buyCoffee(cc, p) }
```

it takes an additional parameter (compared to `buyCoffee`), the number of coffees to buy. It returns a `List<Coffee>` as expected. It uses `buyCoffee` to fill a list with `n` coffees.

Unfortunately this code charges the credit card multiple times. That is bad both for the customer and the business (each transaction costs money).

Again the problem is the side effect.

```kotlin
p.charge(cc, cup.price) // <-- Side effect
```

## Side Effects and Referential Transparency

I mentioned the word *side effect* multiple times. It's time to explain what it is. A side effect is something that breaks referential transparency (RT). Which leads to the question: What is *referential transparency*?

> An expression is called referentially transparent if it can be replaced with its corresponding value without changing the program's behavior. - [Wikipedia][wikipedia-rt]

### RT Example

Given this version of `buyCoffee`:

```kotlin

fun buyCoffee(cc: CreditCard, p: Payments): Coffee {
    val cup = Coffee()
    p.charge(cc, cup.price)
    return cup
}
```

RT means that the `coffeeA` can be replaced with it's result a `Coffee` without altering the program.

```kotlin
val coffeeA: Coffee = buyCoffee(cc, p)	// returns Coffee()

val coffeeB: Coffee = Coffee()
```

`coffeeA` and `coffeeB` are not equivalent. With `coffeeA` your credit card is charged, with `coffeeB` you get a free coffee. That means `buyCoffee` is not referentially transparent.

## Solving the problem

I can push the problem to the payment processor. I can create `BatchPaymentsProcessor` that batches the payments before executing. This opens questions like:

- How long does it wait
- How many payments do it batch
- Is `buyCoffee` responsible for indicating start/end batch

`BatchPaymentsProcessor` solves the code reuse problem but brings a whole set of new problems to the table. Can we do better?

## Removing the Side Effect

The side effect is preventing composition. If I remove it maybe I can regain composition.

```kotlin

fun buyCoffee(cc: CreditCard): Pair<Coffee, Charge> {
    val cup = Coffee()
    val charge = Charge(cc, cup.price)
    return Pair(cup, charge)
}
```

The side effect is gone now, so is the `Payments` object. The return type changed to `Pair<Coffee, Charge>`. A `Charge` object is returned in addition to the `Coffee`. The `Charge` object is a pure value (a data class) indicating a charge should be made. The `Charge` also contains the information needed to execute it like CC number and price.

The `buyCoffee` function is only responsible for creating the `Charge` which is pure (no side effects). The responsibility of executing it is in another part of the program.

## Combining charges

Since `Charge` is a regular value it can easily be combined.

```kotlin

fun combine(c1: Charge, c2: Charge): Charge =
    if (c1.cc == c2.cc) Charge(c1.cc, c1.price + c2.price)
    else throw IllegalArgumentException(
        "Can't combine charges with different cc"
    )
```

two charges, given they have the same Credit Card are combined by adding their price.

## Buying multiple coffees revisited

I can now implement the `buyCoffees` function in terms of the pure `buyCoffee` like this:

```kotlin

fun buyCoffees(
    cc: CreditCard,
    n: Int
): Pair<List<Coffee>, Charge> {
    val purchases: List<Pair<Coffee, Charge>> =
        List(n) { buyCoffee(cc) }
    val (coffees, charges) = purchases.unzip()
    val charge = charges.reduce { c1, c2 -> combine(c1, c2) }
    return Pair(coffees, charge)
}
```

The return type is `Pair<List<Coffee, Charge>>` containing a list of purchased coffees and a single charge for all of them. To create this the `buyCoffee` function is used to populate a list with `n` coffee items. The `unzip` function is used to separate the coffees from the charges. Charges are combined using the `combine` function defined above.

Again the concern of executing the purchase is pushed to another part of the program.

The benefits gained are:

- Better testability - the functions are testable without mocks
- Composition - the bigger function is implemented in terms of the smaller
- Separation of concerns - decision of purchase is separate of purchase execution

## Conclusion

By doing a simple thing like separating the decision of making a side effect and executing it the code is much more composable and reusable. This also improves testability as pure functions can be tested without using mocks. Being aware of the way side effects influence composition helps writing composable code.

[fp-scala]: https://www.manning.com/books/functional-programming-in-scala
[wikipedia-rt]: https://en.wikipedia.org/wiki/Referential_transparency
[ryan]: https://unsplash.com/@ryan_yoo
[unsplash]: https://unsplash.com/search/photos/lego
