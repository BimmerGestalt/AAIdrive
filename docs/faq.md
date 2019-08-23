---
layout: page
title: Foreseen Anticipated Queries
permalink: /faq
---

What features does this app add to the car?
: This app adds music apps from the phone as music sources in the car dashboard, to allow control of the phone's music playback including app switching and browsing. It can also optionally show phone notifications in the car dashboard, and do some basic interactions with them (such as Mark As Read, Like, and so on)

Is my phone compatible?
: This Android app has been successfully tested on Android Nougat, Oreo, and Pi. iPhone users do not need this app, because BMW supports more apps and Apple Carplay for iPhones.

Is my car compatible?
: Any model-year 2015 or later BMW or Mini with the navigation option should be compatible. As a sure test of compatibility, the official BMW or Mini Connected phone app should add a Calendar to the car's Connected Apps menu: This app uses that same protocol.

Is it safe? What is it doing to the car?
: The phone app connects to the car and adds new menus and labels to the display, and then updates the contents of the labels in response to user input, all remotely. Nothing permanent is changed in the car, and everything disappears when the phone is disconnected from the car.

Do I really need the Connected Classic app?
: Yes, the Connected Classic app provides the security module that this app uses to respond to the car's authentication challenge. The new Connected app doesn't export it for other apps on the phone to use. However, it is permissible to also install the Connected Classic app for the other brand than the car: For example, the BMW Connected can be installed for car features while the Mini Connected Classic app is installed for the security module.

How does the app detect passengers in the car?
: The car detects weight on the seat and tells the app if someone is sitting on it.

Can you add support for this other music app?
: This app uses the standard Android MediaBrowser api to interact with music apps. It seems that some apps implement a whitelist of which apps are allowed to connect and control its music, and the authors of these apps have refused previous requests to alter their whitelists. Please reach out to the music app author to grant an exception for this app.

What does "Request Audio Focus" mean?
: This feature tells the car to listen to the phone's app connection for music, and enables a few tighter integrations like automatic music resuming and the seek buttons. However, cars that run the Connected Apps over USB also listen to the music over USB, which is only compatible with Android phones before Oreo. In this situation, the car should be set to play from Bluetooth and the "Request Audio Focus" should be disabled.

Why should I use this app's version of Spotify?
: The official Connected App of Spotify is not allowed to run when the phone is connected over USB, because the necessary Android Open Accessory 2.0 protocol was removed in Android Oreo. This app provides a workaround to play Spotify over Bluetooth audio.

Why does Spotify sometimes not show cover art?
: Sometimes Spotify sends the cover art as a URI link, and then blocks the request to download the cover art image. Other times, it sends the cover art as a regular image, and then it can be shown in the car.

Why aren't Google Maps compiled into the app by default?
: Google Maps support is mainly to demonstrate Android Auto features being implemented as Connected Apps. It is against the [general license](https://cloud.google.com/maps-platform/terms/#3-license) to show Google Maps in the car. The performance is also poor, because it is taking a screenshot of Google Maps on the phone and uploading it to the car as fast as possible, which goes about 10fps over USB and 1fps over Bluetooth.

Which version should I install?
: To help test this app, the APK labelled `sentry` should be installed, which will automatically upload crash reports to Sentry. The `nonalytics` version has all crash reporting functionality removed.
: The `release` and `debug` versions are signed by different keys, as some phones may refuse to install apps without a release signature.

Why isn't this available on the Google Play Store?
: During this early stage, the app needs further testing before releasing to a wider anonymous public. Additionally, it relies on a feature which enables to app to stay completely shut down until the car connects, which was removed in Android Oreo, and Google Play Store does not allow uploading apps that are targeting old API versions.

How do I get past this other problem?
: Please [reach out](mailto:hufman+androidautoidrive@gmail.com?subject=Android Auto IDrive Question) for support!
