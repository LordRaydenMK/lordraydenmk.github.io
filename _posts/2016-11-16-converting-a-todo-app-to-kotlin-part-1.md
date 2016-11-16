---
layout: post
title: Converting a TODO app to Kotlin (part 1 - Intro)
author: Stole Anastasov
tags:
- android
- androiddev
- kotlin
- java
---
I first heard about Kotlin from a [document](https://docs.google.com/document/d/1ReS3ep-hjxWA8kZi0YqDbEhCqTt29hG8P44aA9W0DM8/edit?usp=sharing) written by [Jake Wharton](https://plus.google.com/+JakeWharton) about using Kotlin for Android development. I was intrigued by the language and I watched a few conference talks about it. I liked what I saw but due to various reasons I didn't start playing with the language right away.

![Kotlin Logo](https://kotlinlang.org/assets/images/twitter-card/kotlin_800x320.png  "Kotlin")

In September a decided to give Kotlin a shot. I started with completing the Kotlin Koans on-line on http://try.kotlinlang.org It's a series of 42 exercises, created as a failing unit test, designed to get you familiar with the Koltin syntax and features. They are also available on [Github](https://kotlinlang.org/docs/tutorials/koans.html).

The next step for me is trying it in an Android app. Kotlin is [interoperable](https://kotlinlang.org/docs/reference/java-interop.html) with Java, you can call Java code from Kotlin and you can call Kotlin code from Java. This is very useful because I can take an existing project and convert it to Kotlin one class/file at a time. The Kotlin plug-in has a tool that does this.

Engineers from Google, together with the community, maintain the [Android Architecture Blueprints project](https://github.com/googlesamples/android-architecture). It demonstrates different architectures for building a TO-DO application for Android. From the project README:

> The aim of the app is to be simple enough that it's understood quickly, but complex enough to showcase difficult design decisions and testing scenarios.

What if instead of comparing different architectures I compare different languages.
![Android Architecture Blueprints](https://github.com/googlesamples/android-architecture/wiki/images/aab-logo.png  "Android Architecture Blueprints")

I forked the project to my [Github account](https://github.com/LordRaydenMK/android-architecture)  and I'll post the code there. I will use the todo-mvp branch as a base for my conversion.

The first step is to configure Kotlin in the project. Assuming the [Kotlin Plugin](https://plugins.jetbrains.com/plugin/6954) is installed this step is accomplished with a single command: Configure Kotlin in project (Tools -> Kotlin -> Configure Kotlin in Project). The command adds the kotlin gradle plugin to the classpath (in the top level build.gradle file), applies the kotlin-android plugin, adds src/main/kotlin to the source sets and adds the kotlin standard library as a dependency. You can read more about setting up Kotlin with gradle in the [official documentation](https://kotlinlang.org/docs/reference/using-gradle.html). The commit with the changes is [here](https://github.com/LordRaydenMK/android-architecture/commit/22d6bdce9696390843f88d7acb153330f9ef877d).

In the next article I'll convert some Java code to Kotlin starting with the model layer (*data* package).
