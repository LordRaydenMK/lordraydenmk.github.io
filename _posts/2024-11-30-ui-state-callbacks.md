---
layout: post
title: UI State and Callbacks
author: Stojan Anastasov
tags:
- android
- kotlin
---

Yesterday a friend asked me to review his blog post on [Function Properties in Data Classes are Code Smells][1]. We then discussed how we would solve the issue in the context of UI state and callbacks. And I thought it might be useful to write it in form of a blog post.

## An example

To better explain the problem, I'll start with a [sample app][2] I'm maintaining. It shows a list of superheroes from the Marvel API. See the screenshot below.

![Superheroes List Screenshot](https://raw.githubusercontent.com/LordRaydenMK/SuperheroesAndroid/refs/heads/main/images/superheroes.png)

Currently the UI state is modeled like:

```kotlin
data class SuperheroViewEntity(
    val id: Long, 
    val name: String, 
    val imageUrl: HttpUrl
)

Content(
    val superheroes: List<SuperheroViewEntity>,
    val copyright: String,
)

// some states omitted for brevity
```

## A Naive Solution

Let's say we have a new requirement, we want to add a favorite button to each superhero so we can keep track of our favorite superheroes. To do that we will introduce some functions:

```kotlin
fun addToFavorites(superheroId: Long) = TODO()

fun removeFromFavorites(superheroId: Long) = TODO()
```

These functions can be part of the ViewModel/Presenter that delegates to a repository to store the IDs.

To achieve this we might be tempted to update our `SuperheroViewEntity` to include:

```kotlin
data class SuperheroViewEntity(
    val id: Long, 
    val name: String, 
    val imageUrl: HttpUrl,
    val onFavoriteClicked: () -> Unit
)

// ViewModel
fun Superhero.toViewEntity() =
    SuperheroViewEntity(
        id,
        name,
        imageUrl,
        if (favorite) { removeFromFavorite(id) } else { addToFavorite(id) }
    )

// View layer
Modifier.clickable { entity.onFavoriteClicked() }
```

However this approach has some significant drawbacks. Functions in Kotlin are equal only if they are the same reference.

```kotlin
val fa = { println(1) }
val fb = { println(1) }
println(fa == fb) // false, does the same job, but different reference

val fc = fa
println(fc == fa) // true, same reference
```

Equality in data classes is derived based on the equality of each property. A data class with a function in its constructor may have surprising equality. 

For our example, this means that our view entities, created with `Superhero.toViewEntity()` for the same superhero will NEVER be equal, as we always create new instances for `onFavoritesClicked`.

In cases where we have semantically the same superhero, we want equals to return `true`, otherwise it will result in an update operation for the element in the list, which might be expensive. In a unidirectional data flow architecture, for every single change in the UI we might update the whole UI state (which may include all list of elements) and this may happen often. Doing too much (unnecessary) work too often can cause performance and/or correctness issues when using these classes in `RecyclerView` or Lazy lists (compose). 

Some workarounds include:

- custom `equals/hashCode`
- making sure we use the same function instance per ID

## Alternative solution

Those workarounds are IMO error prone. So let's explore if we can do better.

We can remove the function, and add the `val favorite: Boolean` instead. The if check moves to the view layer.

```kotlin
data class SuperheroViewEntity(
    val id: Long, 
    val name: String, 
    val imageUrl: HttpUrl,
    val favorite: Boolean,
)

// View layer

if (entity.favorite) {
    Modifier.clickable { onRemoveFromFavorite(entity.id) }
} else {
    Modifier.clickable { onAddToFavorite(entity.id) }
}

```

It's not that bad for a simple check as `favorite`, but it feels dirty. It adds logic to the View layer, and scales poorly with the complexity of that logic.

## Preferred solution

What we ideally want is for the logic to be in the ViewModel/Presenter, the view to be as simple as possible, and no equality foot guns.

An alternative approach:

```kotlin
sealed class Action
data class AddToFavorites(id: Long): Action
data class RemoveFromFavorites(id: Long): Action

data class SuperheroViewEntity(
    val id: Long, 
    val name: String, 
    val imageUrl: HttpUrl,
    val action: Action,
)

// ViewModel
fun Superhero.toViewEntity() =
    SuperheroViewEntity(
        id,
        name,
        imageUrl,
        if (favorite) RemoveFromFavorites(id) else AddToFavorites(id)
    )

// View layer

Modifier.clickable { onAction(entity.action) }

```

We are now deciding the action we want to take, based on `favorite` (or whatever other conditions) in the ViewModel/Presenter. We are 'recording' the decision in the `ViewEntity` in a form of a sealed class with `AddToFavorites` and `RemoveFromFavorites` options. This is simple data and does NOT break equality.
The View layer is still simple, as it makes no decisions. The ViewModel/Presenter can now execute the decision on the way up from the view.

We went from:

- ViewModel/Presenter -> creates action (function) based on data
- View -> notifies ViewModel/Presenter to execute
- ViewModel/Presenter -> executes action

to:

- ViewModel/Presenter -> creates action description (sealed class) based on data
- View -> notifies ViewModel/Presenter to execute
- ViewModel/Presenter -> creates and executes action

By delaying the time where the action is created, on the way up from the view instead of on the way down towards the view, we removed the equality foot gun and gained performance and/or correctness.

## Summary

Managing UI state in Android requires careful handling of equality while maintaining separation of concerns. This post explores why using function properties in data classes is problematic, particularly for maintaining equality in list-based UI components like RecyclerView or Lazy lists. By replacing function properties with a sealed class to describe actions, we eliminate equality pitfalls, simplify the View layer, and improve maintainability. This approach ensures that logic remains in the ViewModel/Presenter, enabling better performance and correctness in UI rendering.

[1]: https://marcellogalhardo.dev/posts/function-properties-in-data-classes-are-code-smells
[2]: https://github.com/LordRaydenMK/SuperheroesAndroid