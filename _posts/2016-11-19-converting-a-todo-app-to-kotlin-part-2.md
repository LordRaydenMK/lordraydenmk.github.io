---
layout: post
title: Converting a TODO app to Kotlin (part 2 - the Task class)
author: Stole Anastasov
tags:
- android
- androiddev
- kotlin
- java
---
In [part 1][part1] I wrote about what am I doing and why. I also configured Kotlin in the Android project. 

It is time to convert some classes to Kotlin. I will start with the model layer located in the `data` package. In the `data` package we have the Task class. It represents a single TODO item. Tasks are stored in the `TasksRepository` class which implements the `TasksDataSource` interface. The TasksRespsitory uses `TasksLocalDataSource` and `TasksRemoteDataSource` to retrieve the tasks from a SQLite database and from a server respectively. The local and remote data sources also implement the `TasksDataSource` interface.

![mvp](https://github.com/googlesamples/android-architecture/wiki/images/mvp.png)

Let's start with the Task class. Before I convert it to Kotlin I want to describe the [Task class][task.java]. It is an immutable class with four fields. The main constructor assigns the parameters to the fields. There are three additional constructors that call the main constructor with some default values. It overrides `equals` and `hashCode` using functions from `Guava`. One of the fields (mCompleted) is excluded from `equals` and `hashCode`. Overrides `toString` printing the task title. It has three other methods. Including comments and the license the file consists of 149 lines. This is basically a value class with an additional field and three additional methods. Most of the code in this class can be generated for us.

Code -> Convert Java File to Kotlin (Shift + Ctrl + Alt + K) will convert the code to Kotlin. After the automatic conversion the code looks like [this][task-auto-convert]. The code compiles and the unit tests are passing. After manually fixing some stuff the code looks like [this][task-kotlin].

The `Task.kt` class is an immutable class. It has one primary constructor and one secondary constructor. It overrides `equals` and `hashCode`. It overrides `toString` printing the task name. It also has the three additional properties. The auto-converted class has 102 lines including comments.

Let's see how Kotlin helps us to eliminate some of the boilerplate in Java.

{% highlight kotlin linenos %}
@JvmOverloads constructor(val title: String?, 
				val description: String?,
				val id: String = UUID.randomUUID().toString(), 
				val isCompleted: Boolean = false) {
{% endhighlight %}

The primary constructor for the `Task` class is part of the class header. It has four parameters just like the Java version. Unlike the Java version two of them have [default values][kotlin-default-values]. The compiler will generate a property for each of the parameters in the main constructor and will assign the values from the parameters to the properties. Classes in Kotlin [can't have fields][kotlin-fields] instead we use [properties][kotlin-properties]. This primary constructor in Kotlin replaces: the field declarations, three constructors and four getters from the Java version. Sweet.

In a pure Kotlin project this primary constructor would replace the four Java constructors. For this class the `@JvmOverloads` [annotation][kotlin-jvm-overloads] and a secondary constructor are used to maintain compatibility with Java. The primary constructor plus `@JvmOverloads` replace three of the Java constructors:

{% highlight java linenos %}
//Replaced by the primary constructor in Kotlin
public Task(@Nullable String title, @Nullable String description)
public Task(@Nullable String title, @Nullable String description, @NonNull String id)
public Task(@Nullable String title, @Nullable String description, 
	@NonNull String id, boolean completed)

//Replaced with the secondary Kotlin constructor
public Task(@Nullable String title, @Nullable String description, boolean completed)
{% endhighlight %}

For the fourth Java constructor is replaced with a secondary Kotlin constructor.

{% highlight kotlin linenos %}
constructor(title: String?, description: String?, completed: Boolean) : 
	this(title, description,UUID.randomUUID().toString(), completed) {
    }
{% endhighlight %}

The `getTitleForList` function from the Java version is replaced with a `titleForList` property. I modified the code to use `if` as an [expression][if-expression] (it returns a value). Kotlin does not have the ternary operator `? :` because it's function is achieved with a simple `if else`. 

{% highlight kotlin linenos %}
val titleForList: String?
        get() = if (!Strings.isNullOrEmpty(title)) title else description
{% endhighlight %}

The `isActive` and `isEmpty` functions are also replaced with properties. Nothing interesting happens here.

After converting the class to Kotlin there was a warning from IntelliJ. The parameter name in the `equals` method was named `o` just like in the Java version. The parameter name in the equals method of `Any` (similar to `Object` in Java) is `other`. This can cause a problem the function is called using [named parameters][named-parameters]. IntelliJ offers a quick fix so fixing it is trivial.  

The implementations of `toString` and `hashCode` are the same as the Java version.

While writing part 2 I ignored a **very very** important Kotlin feature: [Null Safety][kotlin-null-safety].

This article would not be complete if I don't mention [data classes][kotlin-data-classes] in Kotlin. It makes sense to make `Task` a data class. For this class the default implementation for `equals` and `hashCode` would not work because `isCompleted` is excluded. This can be overridden and the code would end up pretty much the same as now. Also the `toString` implementation in this case is different than the standard one. The benefit of making `Task` a data class would be in the generated `componentN` functions and `copy` function.

The `componentN` functions are useful in [destructing declarations][destructing-declarations] and `copy` is useful for creating a new Task with modified fields.

{% highlight kotlin linenos %}
val task = Task("Finish Part 2", "Finish the article and publish it on the blog")

//destructing declaration
val (name, description) = task

//the value of name is "Finish Part 2"
//the value of description is "Finish the article and publish it on the blog"

//copy
val part3task = task.copy(name = "Finish Part 3")
//part3task is a task with name "Finish Part 3". 
//The other properties are the same as task
{% endhighlight %}

I will continue converting the project to Kotlin and writing about it. Until next time.

[part1]: /2016/converting-a-todo-app-to-kotlin-part-1
[task.java]: https://github.com/LordRaydenMK/android-architecture/blob/todo-mvp/todoapp/app/src/main/java/com/example/android/architecture/blueprints/todoapp/data/Task.java
[task-auto-convert]: https://github.com/LordRaydenMK/android-architecture/commit/6f8835c170a9be64730b203685c09a0ff9925e90
[task-kotlin]: https://github.com/LordRaydenMK/android-architecture/blob/d9cf55d82070e814964d057bd4dd83a6e7a4aa37/todoapp/app/src/main/java/com/example/android/architecture/blueprints/todoapp/data/Task.kt
[kotlin-fields]: https://kotlinlang.org/docs/reference/properties.html#backing-fields
[kotlin-properties]: https://kotlinlang.org/docs/reference/properties.html
[kotlin-default-values]: https://kotlinlang.org/docs/reference/functions.html#default-arguments
[kotlin-jvm-overloads]: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.jvm/-jvm-overloads/#jvmoverloads
[if-expression]: https://kotlinlang.org/docs/reference/control-flow.html#if-expression
[named-parameters]: https://kotlinlang.org/docs/reference/functions.html#named-arguments
[kotlin-null-safety]: https://kotlinlang.org/docs/reference/null-safety.html
[kotlin-data-classes]: http://kotlinlang.org/docs/reference/data-classes.html
[destructing-declarations]: http://kotlinlang.org/docs/reference/multi-declarations.html