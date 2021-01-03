---
layout: post
title: Fragments, ViewBinding and memory leaks
author: Stojan Anastasov
tags:
- android
- view-binding
- kotlin
- kotlin-android-extensions
---

As an Android engineer one of the basic things you need to do is bind the views (written in XML) with Kotlin/Java code. You can do this with the basic primitive -`findViewById()`, using a library like [ButterKnife][butter-knife], using a compiler plugin like Kotlin Android Extensions or starting with Android Studio/AGP 3.6 [ViewBinding][view-binding]. There are a few other options out there but in my experience these are the most common.

The Kotlin Android Extensions plugin will be [deprecated][deprecate-android-ext] (except the `@Parcelize` functionality) in favor of ViewBinding soon. I see this as a positive change, but not everyone shares my opinion. One of the common arguments against ViewBinding (according to a comment in the ticket, a discussion on reddit and a friend of mine) is: 

> ViewBinding introduces memory leaks in Fragments.

Let's take a look into the usage example from the official docs:

```kotlin
private var _binding: ResultProfileBinding? = null
// This property is only valid between onCreateView and
// onDestroyView.
private val binding get() = _binding!!

override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
): View? {
    _binding = ResultProfileBinding.inflate(inflater, container, false)
    val view = binding.root
    return view
}

override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
}
```

The fragment outlives the view, which means that if we forget to clear the binding reference in `onDestroyView` this will cause a memory leak. Now I do agree that this is error prone, you have to remember to clear the binding reference in each fragment you create. However this problem is not inherent to ViewBinding, but to this kind of usage. The problem here is: a component with a larger scope (the fragment) keeps a reference to a component with a smaller scope (the binding).

Clearing the reference is a workaround, the proper solution is to move the reference to the correct scope:

```kotlin
private val viewModel by viewModels<ProfileViewModel>()

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val binding = ResultProfileBinding.bind(view)

    TODO("Set any (click) listeners")

    viewModel.liveData.observe(viewLifecycleOwner) { data ->
        TODO("Bind the data to views")
    }
}
```

If you move the binding reference from the `Fragment` scope to a function scope, you don't have to worry about clearing it because the function scope is smaller then the view scope. Now this does assume the MVVM pattern for the UI layer.

![You don't have to clear a reference that doesn't exist](/images/posts/clear-reference.jpg)

Another argument against ViewBinding that I have seen is that is more code, you have to type `binding.view` instead of just `view` (using kotlin android extensions):

```kotlin
// kotlin-android-extensions
viewModel.liveData.observe(viewLifecycleOwner) { data ->
    textView.text = data.title
    imageView.load(data.imageUrl)
}

// view-binding
val binding = ResultProfileBinding.bind(view)

viewModel.liveData.observe(viewLifecycleOwner) { data ->
    binding.textView.text = data.title
    binding.imageView.load(data.imageUrl)
}
```

To ease the pain here you can use kotlin's scoping function `with`:

```kotlin
// view-binding
val binding = ResultProfileBinding.bind(view)

viewModel.liveData.observe(viewLifecycleOwner) { data ->
    with(binding) {
        textView.text = data.title
        imageView.load(data.imageUrl)
    }
}
```

and the repetitive `binding` is gone. The more views you have, the better this scales - you only write `with(binding)` once.

## Conclusion

Using ViewBinding with Fragments can be error prone, but it doesn't have to be. The solution is moving the reference to the binding from a Fragment scope to a local function scope.

[deprecate-android-ext]: https://youtrack.jetbrains.com/issue/KT-42121
[butter-knife]: https://jakewharton.github.io/butterknife/
[view-binding]: https://developer.android.com/topic/libraries/view-binding