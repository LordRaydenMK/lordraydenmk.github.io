---
layout: post
title: Contributing to OSS for Hacktoberfest
author: Stojan Anastasov
tags:
- development
- open-source
- hacktoberfest
---

Digital Ocean in partnership with Github are organizing the fourth [Hacktoberfest][hacktoberfest] event this October. If you make four pull requests between October 1 and October 31 to any Github hosted repository you get a Hacktoberfest T-shirt. This year I decided to take part and I already made two pull requests.

![hacktoberfest](https://assets.digitalocean.com/ghost/2017/09/Hacktoberfest17-Blog-01.png)

# My first OSS contribution

I created my first pull request to an OSS project 3 years after I registered on Github. I know it can be hard to start contributing. At first I didn't think my code was good enough, then I couldn't find the right project with the right issue I could fix.

One day an opportunity presented itself. At work I was using [auto-parcel][auto-parcel] to generate the boilerplate required for Parcebale implementation on Android. It's a cool library based on [auto-value][auto-value] by Google. At the time I was using version 0.3 - the version displayed in the README file on Github. A few days later while creating another value class I got an error. I googled the error and it led me to an issue on the project's Github page. The issue was closed and a new version, version 0.3.1 was released. The README file was outdated, it still pointed to the previous release containing the bug.

I spent 10 minutes because of a bug that was already fixed. What a waste of time. If I wasted time chances are someone else will also run into the same problem so I decided to do something about it. Opening an issue on Github would be nice but the solution was so simple I decided to fix it myself and make a [pull request][pull-request]. I also updated another dependency (android-apt) in the README to the latest version. The next day my pull request was accepted and it felt good.

# Hacktoberfest 2017

A few days after Hacktoberfest 2017 started I run into an interested project on Github - [TornadoFx][tornado-fx] a Lightweight JavaFX framework for Kotlin. I like Kotlin and it's Hacktoberfest so I decided to contribute and maybe get a cool T-Shirt in the process. I noticed the project didn't have enough unit tests so I decided to write a few. I found a file with some extension methods that were easy to test. They didn't have tests so a wrote a few and I even found a little bug. I opened a [pull request][tornado-pull] and my changes were merged. In my pull request I asked if more tests would be something they would be interested in. The owner replied:

> Thanks for your contribution. Tests are most welcome of course :)

I also opened another pull request fixing the bug I had found.

Getting started with contributing to OSS can be intimidating. Starting small can help gain some confidence. Helping with documentation or tests, like I did, is a good place to start. There are thousands of beginner friendly issues tagged with [hacktoberfest][hacktoberfest-issues] waiting for you.

Are you participating in Hacktoberfest? Do you remember your first OSS contribution?

[hacktoberfest]: https://blog.digitalocean.com/hacktoberfest-2017/
[auto-parcel]: https://github.com/frankiesardo/auto-parcel
[auto-value]: https://github.com/google/auto/tree/master/value
[pull-request]: https://github.com/frankiesardo/auto-parcel/pull/36
[tornado-fx]: https://github.com/edvin/tornadofx
[tornado-pull]: https://github.com/edvin/tornadofx/pull/486
[hacktoberfest-issues]: https://github.com/search?utf8=%E2%9C%93&q=state%3Aopen+label%3Ahacktoberfest+is%3Aissue&type=Issues&utm_source=DigitalOcean_Hacktoberfest2017