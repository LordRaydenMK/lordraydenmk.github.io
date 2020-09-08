---
layout: post
title: Communicating with your Lifecycle Owner using RxJava
author: Stojan Anastasov
tags:
- kotlin
- android
- rxjava
- jetpack
---

Google introduced Jetpack, a family of opinionated libraries to make Android development easier a few years ago. One of the core classes in Jetpack is `LiveData` - an observable, lifecycle aware data holder. The typical use case is having a `ViewModel` that exposes `LiveData` as a property, and observing it from your lifecycle owner, a `Fragment` or an `Activity`.

A typical usage would look like this:

```kotlin
data class MyState(val value: String)

class MyViewModel : ViewModel {
    
    private val _state = MutableLiveData<MyState>()
    val state: LiveData<MyState>
    get() = _state
}

class MyFragment : Fragment {

    val viewModel by viewModels<MyViewModel>()

    override fun onViewCreated() {
        viewModel.state.observe(this, Observer(::handleState))
    }

    private fun handleState(state: MySate): Unit = TODO()
}
```

There are multiple benefits of using `LiveData`: 

- Your observer is notified when the data changes 
- The observer is only notified of changes when it's active
- Observers are notified when they become active again, like entering into foreground etc 

Check the [LiveData][live-data] docs for all benefits.

## LiveData and Events

In situations like showing a `Snackbar`/dialog or navigating to a different `Activity/Fragment` the `ViewModel` also needs to notify the `LifecycleOwner`. A plain old `LiveData` doesn't work well here because it caches the last item. As a workaround, in the official Android architecture samples there is a `SingleLiveEvent` implementation of `LiveData`.MySate

## Data Layer

But what about the rest of the app? You can use `LiveData` in your data layer, in fact Room, the persistence library from Jetpack, support `LiveData` as the return type natively. However while using `LiveData` across all the layer in the app is possible, it is less than ideal. The operations are always executed on the Main Thread and it comes with limited number of transformation functions compared to `RxJava` or `Flow`.

To fix this problem `LiveData` comes with adapters for both `RxJava` and `Flow` from KotlinX Coroutines. This means developers can use `RxJava` or `Flow` in their data layer and convert to `LiveData` in the `ViewModel` benefiting from the lifecycle awareness.

## State and RxJava

I was working on a sample app last week using the Marvel API, you can check the full code on [Github][superheroes]. It uses a fully reactive architecture powered by RxJava. My initial approach was to use `LiveData` for the `ViewModel` -> `Fragment` communication. However I also wanted to benefit from the `RxJava` excellent testing support, so I decided to try and do what `LiveData` does using `RxJava`.

For observing state, RxJava offer `BehaviorSubject`, a `Subject` that caches the last value it observer and emits it to each subscribed `Observer`. That takes care of the caching of the last value and observing changes. For observing on the Main Thread there is `RxAndroid`. What about the lifecycle part. To take care of that I created a [utility class][lifecycle-disposable], built atop `LifecycleOwner` to help me dispose of the subscription at the right lifecycle callback.

```kotlin
class LifecycleDisposable(
    private val disposable: Disposable
) : DefaultLifecycleObserver {

    override fun onDestroy(owner: LifecycleOwner) {
        disposable.dispose()
        super.onDestroy(owner)
    }
}
```

the code using this:

```kotlin
class RxViewModel : ViewModel() {
    
    private val _state = BehaviorSubject.create<MyState>()
    val state: Observable<MyState>
    get() = _state
}

class RxFragment : Fragment() {

    val viewModel by viewModels<MyViewModel>()

    fun onViewCreated() {
        viewModel.state
            .subscribe(::handleState)
            .autoDispose() // extension function, check the Github link
    }

    private fun handleState(state: MySate) = TODO()
}
```

Unlike `LiveData`, this starts observing in `onViewCreated` and unsubscribes in `onDestroyView`. The code would be similar to subscribe in `onStart` and stop observing in `onStop`, however in my experience (and I could be wrong) updating view state while the view is not active never caused me problems.

## Events and RxJava

Events are a bit different than state. The main requirement is to be delivered exactly once (they are consumable). Another important requirement is to be delivered ONLY when the view is active (between `onStart` and `onStop`). My first attempt was using `UnicastSubject`:

> A Subject that queues up events until a single Observer subscribes to it, replays those events to it until the Observer catches up and then switches to relaying events live to this single Observer until this UnicastSubject terminates or the Observer unsubscribes.

however that didn't work out well. The queuing of events when there was no observers was working great. Upon subscription all events would be delivered. When there was a subscriber events were getting delivered. The problem was when the subscriber unsubscribes and a new subscriber tries to subscribe (`onStart` and `onStop` can be triggered multiple times). That led me to [UnicastWorkSubject][unicast-work] from `RxJavaExtensions`:

> A Subject variant that buffers items and allows one Observer to consume it at a time, but unlike UnicastSubject, once the previous Observer disposes, a new Observer can subscribe and resume consuming the items.

which was exactly what I needed. For the lifecycle part, again I turned to `LifecycleObserver` and created:

```kotlin
class EffectsObserver<E>(
    private val effects: Observable<E>,
    private val executeEffect: (E) -> Unit
) : DefaultLifecycleObserver {

    private var disposable: Disposable? = null

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        disposable = effects.flatMap {
            Observable.fromCallable { executeEffect(it) }
        }.subscribe()
    }

    override fun onStop(owner: LifecycleOwner) {
        disposable!!.dispose()
        super.onStop(owner)
    }
}

```

the usage looks like:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycle.addObserver(EffectsObserver(viewModel.effects) { effect ->
        when (effect) {
            is NavigateToDetails -> TODO("Navigate")
        }
    })
}
```

this will make sure events such as navigation and showing a `Snackbar` are queued when the app is in the background and happen only when the app is in foreground. It helps avoid the dreaded [java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState][fragment-commit].

**Note:** when using this with a `Fragment` it is important to register it in `onCreate` (called exactly once) and not in `onViewCreated` (may be called multiple times with fragments on the backstack).

## Conclusion

You can emulate the way `LiveData` works for `ViewModel` -> `LifecycleOwner` communication with `RxJava`. However there are a few edge cases that you need to consider to do it right, like delivering events only when the `LifecycleOwner` is active.

If you have a different idea to achieve this leave a comment below. Happy coding!

[live-data]: https://developer.android.com/topic/libraries/architecture/livedata
[room]: https://developer.android.com/training/data-storage/room/defining-data
[single-live-event]: https://github.com/android/architecture-samples/blob/6419d4c523b67d020120fc400ed5a7372e5615f2/todoapp/app/src/main/java/com/example/android/architecture/blueprints/todoapp/SingleLiveEvent.java
[superheroes]: https://github.com/LordRaydenMK/SuperheroesAndroid
[lifecycle-disposable]: https://github.com/LordRaydenMK/SuperheroesAndroid/blob/7e627c1aa1b01c42b8a0c3baec7c32b70dcc6d8e/app/src/main/java/io/github/lordraydenmk/superheroesapp/common/lifecycleDisposable.kt
[unicast-work]: https://github.com/akarnokd/RxJavaExtensions#unicastworksubject
[fragment-commit]: https://www.androiddesignpatterns.com/2013/08/fragment-transaction-commit-state-loss.html