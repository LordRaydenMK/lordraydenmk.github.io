---
layout: post
title: Functional Hangman in Kotlin with Arrow
author: Stojan Anastasov
tags:
- kotlin
- fp
- arrowkt
---

A few days ago I run into this [blog post][hangman-zio] about implementing a console Hangman game using [Scala ZIO][scala-zio]. The blog post is based on [this talk][hangman-talk] delivered by [John De Goes][john-de-goes], for the Scala Kyiv meetup, where he codes a console Hangman game using functional programming.

After I saw the post I was curious how the code would look like in Koltin with [arrow][arrow-kt] so I decided to try and write it. You can find the result [here][arrow-hangman].

I am still a beginner in functional programming so I asked for [feedback][feedback] from the arrow maintainers on the kotlinlang slack workspace. They were very helpful and I made a few improvements based on their feedback.

# Key differences

I would like to point out that the programs in Scala and Kotlin are not 100% equivalent. In Scala ZIO `IO[E, A]` describes an effect that may fail with an `E`, run forever, or produce a single `A`. In the Kotlin version I am using `IO<A>` - describes an effect that can fail with `Throwable` or produce a single `A`. Both type classes produce an `A`. The key difference the error. In Scala ZIO you can use any error type or even `Nothing` to indicate the program never ends. In Arrow the `E` type is always `Throwable` so you don't have to specify it.

## Reading and writing from the Console

Scala ZIO comes with built in primitives for interacting with the [console][zio-console]. In the Kotlin version I had to implement the `readStrLn` and `putStrLn` functions myself.

{% highlight kotlin linenos %}
fun putStrLn(line: String): IO<Unit> = IO { println(line) }

fun getStrLn(): IO<String> = IO { 
    readLine() ?: throw IOException("Failed to read input!") 
}
{% endhighlight %}

## ExitStatus

`ExitStatus` is another Scala ZIO specific type. I replaced it with a simple enum which is not equivalent but for this simple use case it does the job.

## For comprehensions

[For comprehensions][scala-for] are built into the scala language. Unfortunately Kotlin doesn't have the same feature. But Kotlin has Coroutines so the Arrow team built [Comprehensions over coroutines][arrow-comp] which can be used in a similar way to make the code more readable.

{% highlight scala linenos %}
//Scala with For comprehensions
val hangman : IO[IOException, Unit] = for {
    _ <- putStrLn("Welcome to purely functional hangman")
    name <- getName
    _ <- putStrLn(s"Welcome $name. Let's begin!")
    word <- chooseWord
    state = State(name, Set(), word)
    _ <- renderState(state)
    _ <- gameLoop(state)
} yield()
{% endhighlight %}

{% highlight kotlin linenos %}
//Kotlin with Arrow (Comprehensions over coroutines)
val hangman: IO<Unit> = IO.monad().binding {
    putStrLn("Welcome to purely functional hangman").bind()
    val name = getName.bind()
    putStrLn("Welcome $name. Let's begin!").bind()
    val word = chooseWord.bind()
    val state = State(name, word = word)
    renderState(state).bind()
    gameLoop(state).bind()
    Unit
}.fix()
{% endhighlight %}

# Conclusion

Rewriting Functional Hangman from Scala to Kotlin was a nice exercise for learning FP. There are differences between Scala and Kotlin and the `IO` type class in Scala ZIO and Arrow but the core principles are the same, we write programs by solving small problems and then combine the solutions. We use lazy evaluation and move the side effects to the edge of the system.

Thanks to John who wrote this functional hangman, [Abhishek][abhishes] for writing the article and [Paco][paco], [Raul][raul] and [Leandro][leandro], from the Arrow team, for the feedback about my code.

[hangman-zio]: https://abhsrivastava.github.io/2018/11/03/Hangman-Game-Using-ZIO/
[scala-zio]: https://scalaz.github.io/scalaz-zio/
[hangman-talk]: https://www.youtube.com/watch?v=XONTFZ4afY0
[john-de-goes]: https://twitter.com/jdegoes
[arrow-kt]: https://arrow-kt.io
[arrow-hangman]: https://github.com/LordRaydenMK/arrow-hangman
[feedback]: https://kotlinlang.slack.com/archives/C5UPMM0A0/p1541707359083600
[zio-console]: https://scalaz.github.io/scalaz-zio/usage/console.html
[scala-for]: https://docs.scala-lang.org/tour/for-comprehensions.html
[arrow-comp]: https://arrow-kt.io/docs/patterns/monad_comprehensions/#comprehensions-over-coroutines
[abhishes]: https://twitter.com/abhishes
[paco]: https://twitter.com/pacoworks
[raul]: https://twitter.com/raulraja
[leandro]: https://twitter.com/mLeandroBF