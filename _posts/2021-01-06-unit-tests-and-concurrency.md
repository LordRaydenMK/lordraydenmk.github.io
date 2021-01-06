---
layout: post
title: Unit Tests and Concurrency
author: Stojan Anastasov
tags:
- android
- testing
- rxjava
---

Once Retrofit added RxJava support, RxJava became my go-to concurrency framework for writing Android apps. One of the great things about RxJava is the excellent testing support. It includes `TestObserver`, `TestScheduler`, `RxJavaPlugins` so you can switch your schedulers in tests.

A common approach in testing RxJava code is using a [JUnit rule][rx-rule] that replaces the `Scheduler` pools with `Schedulers.trampoline()` before tests are run and resets them to the original thread pools after the tests. This makes the whole Observable chain runs on a single thread, the same thread the test runs on, which means we can write assertions without worrying about concurrency.
However the production code usually is not single threaded. IO operations are done on the IO thread pool, views are updated on the main thread and everything else happens on the computation pool. By using different schedulers in the tests and using a different strategy (single threaded) we make those unit tests useless in catching concurrency issues. 

## A real world scenario

I was working on a side project. The screen consists of a `RecyclerView` displaying a list of elements. To get the elements I need to perform two different API calls. The first API call returns a list with N elements, then for each item in the list I need to perform the second call. After combining the data I send it to the UI for displaying.
Using RxJava this looks like:

```kotlin
// Emits Loading then Content or Problem
private fun requestData(): Observable<ViewState> =
    service.firstApiCall()
        .observeOn(Schedulers.computation())
        .map { it.message }
        .flatMap(this::secondApiCall)
        .map<ViewState> { ViewState.Content(it) }
        .startWith(Single.just(ViewState.Loading))
        .onErrorReturn { ViewState.Problem }
        .toObservable()

// Concurrently executes secondApiCall for each element in list. 
// Transforms the result to ViewEntity, combines everything in a list
private fun secondApiCall(list: List<String>): Single<List<ViewEntity>> =
    Observable.fromIterable(list)
        .concatMapEager { item ->
            service.secondEndpoint(item)
                .observeOn(Schedulers.computation())
                .map { it.message }
                .map { ViewEntity(item, it.toHttpUrl()) }
                .onErrorReturn { DogViewEntity(item, null) }
                .toObservable()
        }
        .toList()
```

Since the list is 15-20 elements, I am using `concatMapEager` to execute the API calls for the second endpoint concurrently while maintaining the order of the elements. This worked great and I wrote my unit tests using the real schedulers. RxJava has amazing testing support that enables this. Thanks [Simon Vergauwen][simon] for the tips on how to do that.

```kotlin
@Test
fun `execute - first call succeeds, one failed call to #2 - one full item, one item with default value`() {
    val items = listOf("Item 1" to "https://item1.jpg", "Item 2" to "invalid url")
    val service = successService(items) // Fake implementation of a retrofit service, always returns success

    val viewModel = ViewModel(service)

    val expected = listOf(
        ViewEntity("Item 1", "https://item1.jpg".toHttpUrl()),
        ViewEntity("Item 2", null)
    )
    viewModel.state
        .test()
        .also { viewModel.execute() }
        .awaitCount(2)  // (0) Loading, (1) Content
        .assertValueAt(1, Content(expected))
}
```

Here, `awaitCount(2)` makes sure the assertions after it are ran only after it gets at least 2 items.

## The Refactor

This worked reasonably well, however I decided to try and optimise it further. 

My current code would: 

- start with a progress bar
- do API call #1 
- do API call #2 N times concurrently 
- combine the results 
- show the full list in the UI 

What if we display the data as it becomes available. 

- start with a progress bar 
- do API call #1 
- show the data from API call #1 in the UI 
- do API calls #2 N times concurrently without displaying progress 
- update the UI N times as the API calls for #2 are completing

I was already using `ListAdapter` which has built-in diffing support. I can send an updated list and it will do it's magic to update the changed elements only.

## Improving the solution

The original code was written as a single Rx chain. It emits a `Loading` state, then does the API calls, combines the data and emits a `Content` state (or an `Error` state in case of failure).
I modified it to: emit a `Content` state containing the list of elements from API call #1 right after the call is done. The data from call #2 had some default values for the time being.
After each invocation of call #2, get the current state, update the element and emit a new `Content` state.
I ran my tests and they failed as expected. I updated the tests to include the intermittent states (the changes caused emitting partial `Content` states). Ran the tests again, two worked, one failed. Ran again, all green.

The updated test:

```kotlin
@Test
fun `execute - first call succeeds, one failed call to #2 - one full item, one item with default value`() {
    val items = listOf("Item 1" to "https://item1.jpg", "Item 2" to "invalid url")
    val service = successService(items)

    val viewModel = ViewModel(service)

    val expected = listOf(
        ViewEntity("Item 1", "https://item1.jpg".toHttpUrl()),
        ViewEntity("Item 2", null)
    )
    viewModel.state
        .test()
        .also { viewModel.execute() }
        .awaitCount(4)  // (0) Loading, (1) Content, (2) Content (3) Content
        .assertValueAt(3, Content(expected))
}
```

the only changes in the test are: the argument to `awaitCount` went from 2 to 4 (to account for the intermittent `Content` values) and the final result is now at index 3.

My refactoring made my tests flaky. Depending of the scheduling and the order of the requests for API call #2 I would get the correct or wrong result. My code had a concurrency issue.

![Multithreading theory VS practice](/images/posts/concurrency.jpg)

My state lives in a serialized `BehaviorSubject`. I would update the state by reading it from the subject, create an immutable copy of the updated state, write back the updated state to the `Subject`. However this is NOT thread safe. Since the state updates happened on the computation pool, multiple threads could do it at the same time. This resulted in inconsistent state, the results of some API calls would get overwritten by the next call.

## Conclusion

When testing RxJava code, you do NOT have to replace all schedulers with Trampoline. Doing that will make you miss possible concurrency issues. Using the real schedulers can help you catch those concurrency issues. It is not a perfect solution, but better than nothing.
Don't replace all schedulers with trampoline by default, make it a conscious choice to do so, or not do it.

Thanks to [Marcello][marcello] and [Gaël][gael] for the review of this post.

[rx-rule]: https://github.com/Plastix/RxSchedulerRule
[simon]: https://twitter.com/vergauwen_simon
[marcello]: https://twitter.com/marcellogalhard
[gael]: https://twitter.com/GaelMarhic