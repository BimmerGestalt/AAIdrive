---
layout: page
title: Foreseen Anticipated Queries
permalink: /faq
---

Is my car compatible?
: Any model-year 2015 or later BMW or Mini with the navigation option should be compatible. There should be a Connected Apps menu in the car, and the official BMW or Mini Connected phone app should add a Calendar app to the car.

Do I really need the Connected Classic app?
: Yes, the Connected Classic app provides the security module that this app uses to respond to the car's authentication challenge. The new Connected app doesn't export it for other apps on the phone to use.

How does the app detect passengers in the car?
: The car detects weight on the seat and tells the app if someone is sitting on it.

Can you add support for this other music app?
: This app uses the standard Android MediaBrowser api to interact with music apps. It seems that some apps implement a whitelist of which apps are allowed to connect and control its music, and the authors of these apps have refused previous requests to alter their whitelists. Please reach out to the music app author to grant an exception for this app.

What does "Request Audio Focus" mean?
: This feature tells the car to listen to the app connection for music, and enables a few tighter integrations like automatic music resuming and the seek buttons. However, cars that run the Connected Apps over USB also listen to the music over USB, which is only compatible with Android phones before Oreo. In this situation, the car should be set to play from Bluetooth and the "Request Audio Focus" should be disabled.

Why aren't Google Maps compiled into the app by default?
: Google Maps support is mainly to demonstrate Android Auto features being implemented as a Connected App. It is against the [license](https://cloud.google.com/maps-platform/terms/#3-license) to show Google Maps in the car. The performance is also poor, because it is taking a screenshot of Google Maps on the phone and uploading it to the car as fast as possible, which goes about 10fps over USB and 1fps over Bluetooth.

How do I get past this other problem?
: Please [reach out](mailto:hufman+androidautoidrive@gmail.com?subject=Android Auto IDrive Question) for support!
