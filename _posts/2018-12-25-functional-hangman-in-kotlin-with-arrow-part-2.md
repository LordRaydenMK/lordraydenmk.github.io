---
layout: post
title: Functional Hangman in Kotlin with Arrow (part 2)
author: Stojan Anastasov
tags:
- kotlin
- fp
- arrowkt
---

Converting a [Functional Hangman][fp-hangman] game from Scala (ZIO) to Kotlin (with Arrow) was a nice exercise. I enjoyed working on it and I learned a lot. When I asked for feedback on the [#arrow][kotlinlang-arrow] channel, one of the maintainers, [Leandro][leandro] had an interesting suggestion. Instead of hard-coding the data type `IO` I should try and make the program polymorphic and use `Kind` instead. That means writing the code focusing on the domain logic, using abstractions, and deferring the decision for the concrete data type like `IO` or `Single` (from RxJava) until the main function.

# The journey

I was not familiar with that style of programming so I used this [example][poly-programs] from the excellent Arrow documentation as a guide.

## Writing to the the console

In the previous article I used `IO<A>` to interact with the console. `IO<A>` represents an operation that can be executed lazily, fail with an exception (the exception is captured inside `IO`), run forever or return a single `A`. Let's take a look at the original implementation:

{% highlight kotlin %}
fun putStrLn(line: String): IO<Unit> = IO { println(line) }

// Usage in main()
putStrLn("Hello world!").unsafeRunSync()
{% endhighlight %}

`putStrLn` is a function that take a `String` and return a `IO<Unit>`. `IO` takes a lambda that is lazily evaluated at the end of the world, when we call `unsafeRunSync()`. If we want to achieve the same thing with `Single` we could use `Single.fromCallable` wrap our lambda and evaluate it in the main function when we call `subscribe()`.

{% highlight kotlin %}
fun putStrLn(line: String): Single<Unit> = Single.fromCallable {
    println(line)
}

// Usage in main()
putStrLn("Hello World").subscribe()
{% endhighlight %}

Here both`IO` and `Single` have something in common. A set of **capabilities** like: **lazy evaluation**, **exception handling**, and **running forever** or **completing with a result** of type `A`. `IO` and `Single` do a lot more, but for this use case, we want something as simple as possible that has the same capabilities. There is a type-class in Arrow that can do just that and it's called `MonadDefer`([more info][monad-defer]). After a few iterations, and feedback from the Arrow team, this is the code I came up with for printing to the console.

{% highlight kotlin %}
fun <F> putStrLn(M: MonadDefer<F>, line: String): Kind<F, Unit> = M.delay {
    println(line)
}
{% endhighlight %}

The `putStrLn` function is generic with type `F`, but not any `F`. This `F`, whatever it is, need to do certain things like **lazy evaluation** and **error handling**. This `F` thing needs the **capabilities** of `MonadDefer`. The return value also needs a type parameter, and in Arrow we can do that by returning `Kind<F, SOMETHING>`. In the case of printing to command line, that `SOMETHING` is `Unit` (no return value). `MonadDefer` comes with an `delay` function that we can use to construct the value of `Kind<F, Unit>`. We pass a lambda inside which will be lazily evaluated.

Compared to the original implementation we have a few key differences:

* a type parameter `F`
* one more parameter of type `MonadDefer<F>`
* the return type is `Kind<F, Unit>` instead of `IO<Unit>`

Now we can use this function to print something to the console. In order to do that, we need a data type that has the **capabilities** of `MonadDefer`. `IO` can do that so we can use it. Arrow also ships with `SingleK`, a wrapper for `Single` that has the `MonadDefer` capabilities.

{% highlight kotlin %}
putStrLn(IO.monadDefer(), "Hello World!").fix()
        .unsafeRunSync()

putStrLn(SingleK.monadDefer(), "Hello World!").fix()
        .single.subscribe()
{% endhighlight %}

Arrow also ships with `ObservableK` for `Observable`, `DeferredK` for coroutines etc.

## Reading from the console

Reading from the console is similar to writing to it. We still need a type parameter `F`, we still need to return `Kind<F, SOMETHING>` and we need to perform the operation lazily with success or an error. `readLine()` returns a nullable `String?` and if that happens we need to signal an error.

{% highlight kotlin %}
// Original code
fun getStrLn(): IO<String> = IO { readLine() ?: throw IOException("Failed to read input!") }

// Polymorphic code
fun <F> getStrLn(M: MonadDefer<F>): Kind<F, String> = M.delay {
    readLine() ?: throw IOException("Failed to read input!")
}
{% endhighlight %}

## Reading from the console #2

I am throwing an `IOException` to indicate failure. `IO` would wrap that exception so it isn't that bad. But there is a better way (thanks [Leandro][leandro]).

I convert the nullable `String?` to an `Option<String>`. Then I use `fold` to check if the `Option` has a value. If it's empty I use `raiseError` to create `MonadDefer` which when evaluated returns an error. If it's not empty I create a `MonadDefer` that returns a `String` using `just`.

The key difference here is I am NOT throwing the `IOException`. Using exceptions can be [expensive][exceptions].

`M.defer` here means the `readLine()` happens lazily.

{% highlight kotlin %}
fun <F> getStrLn(M: MonadDefer<F>): Kind<F, String> = M.defer {
    readLine().toOption()
            .fold(
                    { M.raiseError<String>(IOException("Failed to read input!")) },
                    { M.just(it) }
            )
}
{% endhighlight %}

## The Hangman class

The next step is to make the `Hangman` class polymorphic. To do that I added a type parameter `F` and property of the type `MonadDefer<F>`.

{% highlight kotlin %}
class Hangman<F>(private val M: MonadDefer<F>) {
    ...
}
{% endhighlight %}

## Choosing a letter

To make the `getChoice()` function work in a polymorphic way we need a few changes. The return type changes from `IO<Char>` to `Kind<F, Char>`. `IO.binding` becomes `M.binding` (where `M` is the property of type `MonadDefer<F>`).

The `getStrLn()` and `putStrLn()` also need `M` as the parameter.

{% highlight kotlin %}
fun getChoice(): Kind<F, Char> = M.binding {
        putStrLn(M, "Please enter a letter").bind()
        val line = getStrLn(M).bind()
        val char = line.toLowerCase().first().toOption().fold(
                {
                        putStrLn(M, "Please enter a letter")
                                .flatMap { getChoice() }
                },
                { char ->
                        M { char }
                }
        ).bind()
        char
}
{% endhighlight %}

## Wrapping up

Updating all other functions follows the same pattern. Replace `IO<SOMETHING>` with `Kind<F, SOMETHING>`, replace `IO.binding` with `M.binding` and pass `M` as parameter for reading/writing to the console.

You can find the full code [here][ft-hangman].

## The main program

The main program, run with `Single`, looks like this:

{% highlight kotlin %}
Hangman(SingleK.monadDefer()).hangman.fix().single.subscribe()
{% endhighlight %}

or with `IO`

{% highlight kotlin %}
Hangman(IO.monadDefer()).hangman.fix().unsafeRunSync()
{% endhighlight %}

The decision in which type constructor to run is made at the point of execution. Switching from `Single` to `IO` or `Observable` requires only updating the main function.

## Incremental improvements

I am still learning FP and I don't know most of the type-classes in Arrow and what they can do. In my first attempt I used `Async` instead of `MonadDefer`. `Async` extend `MonadDefer` and adds additional capabilities to it. I asked for feedback on the [#arrow][kotlinlang-arrow] channel and they pointed me towards `MonadDefer`. Together with the Arrow maintainers we made a few more improvements improvements.

To avoid passing `M` to `printStrLn` every time I can convert it to an extension function on `MonadDefer`

{% highlight kotlin %}
fun <F> MonadDefer<F>.putStrLn(line: String): Kind<F, Unit> = delay {
    println(line)
}
{% endhighlight %}

and I can use [Implementation by delegation][implementation-delegation] and have the `Hangman` class implement `MonadDefer<F>`.

{% highlight kotlin %}
class Hangman<F>(private val M: MonadDefer<F>): MonadDefer<F> by M
{% endhighlight %}

This means everywhere inside the `Hangman` class I can use the methods of `MonadDefer` like `binding` and `delay` and extension methods like `putStrLn`.

{% highlight kotlin %}
val hangman: Kind<F, Unit> = binding {
        putStrLn("Welcome to purely functional hangman").bind()
        val name = getName.bind()
        putStrLn("Welcome $name. Let's begin!").bind()
        val word = chooseWord.bind()
        val state = State(name, word = word)
        renderState(state).bind()
        gameLoop(state).bind()
        Unit
    }
{% endhighlight %}

You can find the full implementation [here][hangman-part2].

_**Note**: the code samples here use the function `delay` which doesn't exist in the latest published version (0.8.1). In arrow 0.8.2 `invoke` (used in the code on Github) will be deprecated and replaced by `delay`._

# Conclusion

Working with abstractions like `MonadDefer` frees the business logic from implementation details like `IO` or `Single`. It can also enable easier composition of different modules because the decision for the concrete data type is delayed until the main program. 

[fp-hangman]: /2018/functional-hangman-in-kotlin-with-arrow
[kotlin-weekly]: https://mailchi.mp/kotlinweekly/kotlin-weekly-120
[poly-programs]: https://arrow-kt.io/docs/patterns/polymorphic_programs/
[monad-defer]: https://arrow-kt.io/docs/effects/monaddefer/
[paco]: https://twitter.com/pacoworks
[raul]: https://twitter.com/raulraja
[leandro]: https://twitter.com/mLeandroBF
[exceptions]: http://java-performance.info/throwing-an-exception-in-java-is-very-slow/
[ft-hangman]: https://gist.github.com/LordRaydenMK/0be8f70f860a862e69daf262b4a83e17
[kotlinlang-arrow]: https://kotlinlang.slack.com/messages/C5UPMM0A0/
[implementation-delegation]: https://kotlinlang.org/docs/reference/delegation.html#implementation-by-delegation
[hangman-part2]: https://github.com/LordRaydenMK/arrow-hangman/blob/master/src/main/kotlin/io/github/lordraydenmk/part2/Part2.kt