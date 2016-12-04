---
layout: post
title: Converting a TODO app to Kotlin (part 3 - the Model Layer)
author: Stole Anastasov
tags:
- android
- androiddev
- kotlin
- java
---
In [part 2][part2] I wrote about converting the `Task` class from Java to Kotlin. The number of constructors went from four down to two thanks to default parameters. The boring assignments in the constructors are gone thanks to properties. I also mentioned data classes and using if as an expression. 

Before I start with the main topic for this article I just want to mention I am not the only one curious about a Kotlin version of this project. A few days after I started with my fork this [issue][issue235] was opened on Github. The author of the issue completed the conversion and you can check out the code [here][shyish-kotlin]. The reason I am continuing with my fork are these articles I am writing. I also plan to send a few pull requests to the fork of Shyish with a few suggestions I have like [this one][pull-2].

## TasksDataSource
For this interface the automatic conversion was perfect.

## TasksRemoteDataSource
The automatic conversion for this class results in build errors in the `getInstance` method. The fix is simple, just use the `!!` operator on the return value.

{% highlight kotlin linenos %}
val instance: TasksRemoteDataSource
    get() {
        if (INSTANCE == null) {
            INSTANCE = TasksRemoteDataSource()
        }
        return INSTANCE!!
    }
{% endhighlight %}

The `TasksRemoteDataSource` class uses the Singleton pattern. Kotlin does not have the `static` keyword. In Kotlin you can declare a singleton using [object declaration][object-declaration].

Another noteworthy change here is the use of `let`. It executes the code only if `task` is not null. It also does a smart cast from `Task?` to `Task` (from nullable to non-nullable type). This can also be replaced with a simple `if`.
{% highlight kotlin linenos %}
        val task = TASKS_SERVICE_DATA[taskId]

        // Simulate network by delaying the execution.
        val handler = Handler()
        task?.let {
            handler.postDelayed({ callback.onTaskLoaded(task) }, 
            	SERVICE_LATENCY_IN_MILLIS.toLong())
        }
{% endhighlight %}

The benefits of making `Task` a data class show here. When activating or completing a task we make a new copy with a change in a single field `isCompleted`. Instead of using a constructor we can use the copy function.
{% highlight kotlin linenos %}
//using a constructor
val completedTask = Task(task.title, task.description, task.id, true)

//using the copy function
val completedTask = task.copy(isCompleted = true)
{% endhighlight %}

## TasksPersistenceContract
The TasksPersistenceContract class becomes an object in Kotlin. The `TaskEntry` static class becomes a class with a companion object holding the constants. The `TaskEntry` java class implements `BaseColumns` to use the `_ID` static field. In Kotlin this does not happen, `TaskEntry` doesn't get the `_ID` field. My solution is not to implement `BaseColumns` and manually include an `_ID` field in the companion object of `TaskEntry`.
{% highlight kotlin linenos %}
    /* Inner class that defines the table contents */
    abstract class TaskEntry {
        companion object {
            val _ID = BaseColumns._ID
            val TABLE_NAME = "task"
            val COLUMN_NAME_ENTRY_ID = "entryid"
            val COLUMN_NAME_TITLE = "title"
            val COLUMN_NAME_DESCRIPTION = "description"
            val COLUMN_NAME_COMPLETED = "completed"
        }
    }
{% endhighlight %}
The benefit in this approach is that the client code will remain the same, you can still use `TaskEntry._ID` in the client code (e.g. `TasksDbHelper`).

## TasksDbHelper
Another straight forward automatic conversion. The static constants go into a companion object, there is no need for a constructor and null checks.

## TasksLocalDataSource
This is very similar to `TasksRemoteDataSource` with a few differences. First the constructor here actually does some work instead of just assigning arguments. Kotlin offers initializer blocks to achieve this.

{% highlight kotlin linenos %}
    init {
        mDbHelper = TasksDbHelper(context)
    }
{% endhighlight %}

The rest of the code is pretty much the same with null checks replaced with the `?` operator and no null checks for parameters.

## TasksRepository
Another singleton class which translates to a companion object in Kotlin. Some noteworthy changes here are `mCachedTasks` and `mChacheIsDirty` become public properties (no visibility modifier) to be accessible from the test written in Java and the use of `copy` for activating and completing tasks.

`mCachedTasks` is converted to a non-nullable variable and initialized as empty map. In `getTasks` the `isNotEmpty` function is used instead of a null check. I am not sure this is the correct behavior but all the unit tests pass.

## Final thoughts
I am not sure how I feel about the lack of a static modifier. On one hand it's a little bit weird when defining a singleton or a `private static final LOG_TAG` in your classes. On the other hand I started using dependency injection (Dagger) for my singletons and `Timber` for logging so I don't mind. There is also a Kotlin library for dependency injection called [KODEIN][kodein].

The code for this article can be found [here][github-part3].

[part2]: /2016/converting-a-todo-app-to-kotlin-part-2 
[issue235]: https://github.com/googlesamples/android-architecture/issues/235
[shyish-kotlin]: https://github.com/Shyish/android-architecture/tree/dev-todo-mvp-kotlin
[pull-2]: https://github.com/Shyish/android-architecture/pull/2
[object-declaration]: https://kotlinlang.org/docs/reference/object-declarations.html#object-declarations
[github-part3]: https://github.com/LordRaydenMK/android-architecture/releases/tag/part-3
[kodein]: https://github.com/SalomonBrys/Kodein