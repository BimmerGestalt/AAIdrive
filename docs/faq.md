---
layout: page
title: Foreseen Anticipated Queries
permalink: /faq
---

Why doesn't the app connect to the car?
: This app relies on a working connection from the official BMW/Mini Connected app to a compatible car. If the official Connected app has successfully added the Connected and the Calendar entries to the car, this app should successfully detect the car.

How do I fix the Bluetooth apps connection?
: The Bluetooth connection method, on compatible cars, is finicky. Newer versions of the Connected app keep improving, but in case of problems, some manual steps might be needed to start the app connection:

  - Open the Connected app
  - Initiate the Bluetooth connection from the car, by clicking the Apps checkbox within the connection menu
  - Try disabling and re-enabling the Music connection from the car

The Connected app should now show the notification with the Connected To Car status.

Is my phone compatible?
: This Android app has been successfully tested on Android Nougat, Oreo, and later. iPhone users do not need this app, because BMW supports more apps and Apple Carplay for iPhones.

Is my car compatible?
: Any model-year 2015 or later BMW or Mini with NBT and the navigation option should be compatible. As a sure test of compatibility, the official BMW or Mini Connected phone app should add a Calendar to the car's Connected Apps menu: This app uses that same protocol. However, Live Cockpit Professional (IDrive 7) is not compatible, but is compatible with standard Android Auto.

Is it safe? What is it doing to the car?
: This phone app runs on the same BMW Connected Apps connection as the official apps, such as Spotify, and does not modify the car in any way. The phone app connects to the car and sends new menus and labels to the display, and then updates the contents of the labels in response to user input, all remotely. Nothing permanent is changed in the car, and everything disappears when the phone is disconnected from the car.

Do I really need the Connected Classic app?
: Yes, the Connected Classic app provides the security module that this app uses to respond to the car's authentication challenge. The new Connected app doesn't export it for other apps on the phone to use. However, it is permissible to also install the Connected Classic app for the other brand than the car: For example, the BMW Connected can be installed for car features while the Mini Connected Classic app is installed for the security module.

How does the app detect passengers in the car?
: The car detects weight on the seat and tells any interested apps if someone is sitting on it.

Why isn't the Notification view scrollable?
: IDrive 4 has a Speedlock feature to disable interaction with certain screens if the car is not parked, a restriction which was removed in later versions. This app tries to disable the Notification screen's Speedlock setting, but the car must first be parked for this to work. By parking the car (including engaging the Parking Brake on a stickshift vehicle), the window should become scrollable and then stay scrollable after driving off.

Can you add support for this other music app?
: This app uses the standard Android MediaBrowser api to interact with music apps. It seems that some apps enforce a whitelist of which apps are allowed to connect and control its music. Please reach out to the music app author to grant an exception for this app. The app gained support (in version 1.2) to control any running music app, but can not start them with this whitelist in place.

What is different in this app's version of Spotify?
: The official Connected App of Spotify is not allowed to run if the phone is connected over USB, because the necessary Android Open Accessory 2.0 Audio protocol was removed in Android Oreo. This app provides a workaround to control Spotify over the USB Apps connection while listening over Bluetooth audio. Additionally, this interface provides Spotify's Search capabilities.

Why doesn't Spotify show cover art or browse my library?
: These features require that this app be granted access to control Spotify. Spotify can only verify the API key if it is online, and some ad blockers have been found to also block this API authentication.

Why aren't Google Maps compiled into the app by default?
: Google Maps support is mainly to demonstrate Android Auto features being implemented as Connected Apps. It is against the [general license](https://cloud.google.com/maps-platform/terms/#3-license) to show Google Maps in the car. The performance is also poor, because it is taking a screenshot of Google Maps on the phone and uploading it to the car as fast as possible, which goes about 10fps over USB and 1fps over Bluetooth.

Which version should I install?
: To help test this app, the APK labeled `sentry` should be installed, which will automatically upload crash reports to Sentry. The `nonalytics` version does not include any crash reporting functionality.

Why isn't this available on the Google Play Store?
: During this early stage, the app needs further testing before releasing to a wider anonymous public.

How do I get past this other problem?
: Please [reach out](mailto:hufman+androidautoidrive@gmail.com?subject=Android Auto IDrive Question) for support! If you can, please also provide suspicious `adb logcat` output, which will greatly help with any debugging.
