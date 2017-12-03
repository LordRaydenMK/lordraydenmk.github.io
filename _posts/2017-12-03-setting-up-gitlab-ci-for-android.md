---
layout: post
title: Setting up GitLab CI for Android Projects
author: Stojan Anastasov
tags:
- androiddev
- continuous-integration
- gitlab-ci
---

My first experience with continuous integration was using Bitbucket in combination with [Jenkins][jenkins-ci]. I was pretty happy with my setup. Jenkins would run on every commit making sure my code compiles, run android lint and run my unit tests. I also set up continuous deployment using [Fabric][fabric].

![GitLab CI](https://docs.gitlab.com/ee/ci/img/cicd_pipeline_infograph.png "CI/CD pipeline")

Now, at work, we use GitLab as a code repository. GitLab also offers [continuous integration][gitlab-ci]. When we decided to start using continuous integration at work we decided to give GitLab a chance. It was already integrated with GitLab and to use it we just needed to install a [runner][runner].

Using CI with GitLab is simple, after you install a runner you need to add a .gitlab-ci.yml file at the root of the repository. GitLab even offers template .gitlab-ci.yml files for various languages and frameworks. The android template is based on [this][gitlab-android] blog post from 2016. It is a great guide but unfortunately today it doesn't work. Google introduced a few changes in the command line tools.

# Installing Android SDK

To install the Android SDK on a CI we need to install the [command line tools][cmd-tools] (scroll to the bottom to Get just the command line tools). The command line tools include the [sdkmanager][sdkmanager] - a command line tool that allows you to view, install, update, and uninstall packages for the Android SDK. So instead of

{% highlight yaml %}
  - wget --quiet --output-document=android-sdk.tgz https://dl.google.com/android/android-sdk_r${ANDROID_SDK_TOOLS}-linux.tgz
  - tar --extract --gzip --file=android-sdk.tgz
{% endhighlight %}

we can use

{% highlight yaml %}
  - wget --quiet --output-document=android-sdk.zip https://dl.google.com/android/repository/sdk-tools-linux-3859397.zip
  - unzip -q android-sdk.zip -d android-sdk-linux
{% endhighlight %}

to download and install the Android SDK tools.

There is also an improvement in accepting licenses for the Android SDK. After you accept the licenses on your development machine the tools will generate a licenses folder in the Android SDK root directory. You can transfer the licenses from your development machine to your CI server. To accept the licenses we can use:

{% highlight yaml %}
 - mkdir android-sdk-linux/licenses
  - printf "8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e" > android-sdk-linux/licenses/android-sdk-license
  - printf "84831b9409646a918e30573bab4c9c91346d8abd" > android-sdk-linux/licenses/android-sdk-preview-license
{% endhighlight %}

This will create a folder licenses with two files inside. The values written in the files are from my local machine. Depending on which components you use there might be other files on your machine like license for Google TV or Google Glass. Once the licenses are accepted we can install packages:

{% highlight yaml %}
  - android-sdk-linux/tools/bin/sdkmanager --update > update.log
  - android-sdk-linux/tools/bin/sdkmanager "platforms;android-${ANDROID_COMPILE_SDK}" "build-tools;${ANDROID_BUILD_TOOLS}" "extras;google;m2repository" "extras;android;m2repository" > installPlatform.log
{% endhighlight %}

A sample project based on the [Android Testing codelab][android-testing-codelab] with the full .gitlab-ci.yaml file can be found [here][android-gitlab-ci]. The rest of the script for building and unit testing is the same as the original post. The latest x86 emulator requires hardware acceleration to run so I'll skip the functional tests for now.

[jenkins-ci]: http://www.jenkins.io
[fabric]: http://www.fabric.io
[gitlab-ci]: https://about.gitlab.com/features/gitlab-ci-cd/
[runner]: https://docs.gitlab.com/runner/
[gitlab-android]: https://about.gitlab.com/2016/11/30/setting-up-gitlab-ci-for-android-projects/
[cmd-tools]: https://developer.android.com/studio/index.html#downloads
[sdkmanager]: https://developer.android.com/studio/command-line/sdkmanager.html
[android-gitlab-ci]: https://gitlab.com/stolea/android-gitlab-ci
[android-testing-codelab]: https://codelabs.developers.google.com/codelabs/android-testing/index.html