---
layout: post
title: Functional Hangman in Kotlin with Arrow (part 2)
author: Stojan Anastasov
tags:
- kotlin
- fp
- arrowkt
---

Converting a [Functional Hangman][fp-hangman] game from Scala (ZIO) to Kotlin (with Arrow) was a nice exercise. I enjoyed working on it and I learned a lot. When I asked for feedback on the #arrow channel, one of the maintainers, [Leandro][leandro] had an interesting suggestion. Instead of hard-coding the data type `IO` I should try and make the program polymorphic and use `Kind` instead. This means I can write the code focusing on the logic, using abstractions, and defer the decision for the concrete type like `IO` or `Observable` until the main function.

# The journey

I was not familiar with that style of programming so I used this [example][poly-programs] from the excellent Arrow documentation as a guide.

## Writing to the the console

In the previous article I used `IO<A>` to interact with the console. `IO<A>` represents an operation that can be executed lazily, fail with an exception (the exception can be captured inside `IO`), run forever or return a single `A`. Let's take a look at the original implementation:

{% highlight kotlin %}
fun putStrLn(line: String): IO<Unit> = IO { println(line) }
{% endhighlight %}

`putStrLn` is a function that take a `String` and return a `IO<Unit>`. `IO` takes a lambda that is lazily evaluated at the end of the world, when we call `unsafeRunSync()`. If we want to achieve the same thing with `Observable` we could use `Observable.fromCallable` wrap our lambda and evaluate it in the main function when we call `subscribe()`.

{% highlight kotlin %}
fun putStrLn(line: String): Observable<Unit> = Observable.fromCallable {
    println(line)
}

// Usage in main()
putStrLn("Hello World").subscribe()
{% endhighlight %}

Here both`IO` and `Observable` have something in common. A set of **capabilities** like: **lazy evaluation**, **exception handling** and **completing with a result** of type `A`. `IO` and `Observable` do a lot more, but for this use case, we want something as simple as possible that has the same capabilities. There is a type-class in Arrow that can do just that and it's called `MonadDefer`. After a few iterations, and feedback from the Arrow team, this is the code I came up with for printing to the console.

{% highlight kotlin %}
fun <F> putStrLn(M: MonadDefer<F>, line: String): Kind<F, Unit> = M.invoke {
    println(line)
}
{% endhighlight %}

The `putStrLn` function is generic with type `F`, but not any `F`. This `F`, whatever it is, need to do certain things like **lazy evaluation** and **error handling**. This `F` thing needs the **capabilities** of `MonadDefer`. The return value also needs a type parameter, and in Arrow we can do that by returning `Kind<F, SOMETHING>`. In the case of printing to command line, that `SOMETHING` is `Unit` (no return value). `MonadDefer` comes with an `invoke` function that we can use to construct the value of `Kind<F, Unit>`. We pass a lambda inside which will be lazily evaluated.

Compared to the original implementation we have a few key differences:

* a type parameter `F`
* one more parameter of type `MonadDefer<F>`
* the return type is `Kind<F, Unit>` instead of `IO<Unit>`

Now we can use this function to print something to the console. In order to do that, we need a data type that has the **capabilities** of `MonadDefer`. `IO` can do that so we can use it. Arrow also ships with `ObservableK`, a wrapper for `Observable` that has the `MonadDefer` capabilities.

{% highlight kotlin %}
putStrLn(IO.monadDefer(), "Hello World!").fix().unsafeRunSync()

putStrLn(ObservableK.monadDefer(), "Hello World!").fix()
            .observable.subscribe()
{% endhighlight %}

Arrow also ships with `SingleK` for `Single`, `DeferredK` for coroutines etc.

## Reading from the console

Reading from the console is similar to writing to it. We still need a type parameter `F`, we still need to return `Kind<F, SOMETHING>` and we need to perform the operation lazily with success or an error. `readLine()` returns a nullable `String?` and if that happens we need to signal an error. In the first part I trowed an `IOException` to indicate it. `IO` would wrap that exception so it wasn't that bad. But there is a better way (thanks [Leandro][leandro]). I convert the nullable `String?` to an `Option<String>`. Then I use `fold` to check if the `Option` has a value or it's empty. If it's empty I use `raiseError` to create `MonadDefer` which when evaluated returns an error. If it's not empty I create a `MonadDefer` that returns a `String` using `just`. The key difference here is I am NOT throwing the `IOException`. Using exceptions can be [expensive][exceptions]. `M.defer` here means the `readLine()` happens lazily.

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

## Choosing a letter

## Wrapping up

# Conclusion

[fp-hangman]: /2018/functional-hangman-in-kotlin-with-arrow
[kotlin-weekly]: https://mailchi.mp/kotlinweekly/kotlin-weekly-120
[poly-programs]: https://arrow-kt.io/docs/patterns/polymorphic_programs/
[paco]: https://twitter.com/pacoworks
[raul]: https://twitter.com/raulraja
[leandro]: https://twitter.com/mLeandroBF
[exceptions]: http://java-performance.info/throwing-an-exception-in-java-is-very-slow/