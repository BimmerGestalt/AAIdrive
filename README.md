Android Auto for IDrive
=======================

[![Build Status](https://travis-ci.org/hufman/AndroidAutoIdrive.svg?branch=master)](https://travis-ci.org/hufman/AndroidAutoIdrive)

The BMW/Mini IDrive does not offer native Android Auto integration, but does provide a much more powerful Connected Apps connectivity option, which offers many exciting integration points into the car.
This project is an effort to implement most of the features of Android Auto as an unofficial IDrive Connected App.

Implemented Features
--------------------

  - Phone Notifications
    - Popup about new notifications
  - Google Maps
    - Basic search and routing
    - Not enabled by default, showing Google Maps in a car is against the API EULA
  - Control of Android Auto compatible music apps
    - Supports browsing and searching apps' music libraries
    - Supports selecting from a list of currently-queued songs, as well as basic back/next control
    - Integrates into the car's audio context, for automatic resume and hardware button control

Limitations
-----------

Due to the unofficial reverse-engineered nature of this project, it has some limitations:

  - Some Android Auto music apps enforce a whitelist of clients, preventing this app from connecting and controlling them
  - The main menu entries' icons and text can't be altered, and so do not look exactly correct
  - The individual music app icons are not fully functional, but they do switch the active music source
  - Android Oreo disabled Android Open Accessory Protocol 2 audio output, which is required to play audio over USB in IDrive 4++ used by Mini 2014-2017 and BMW i3 2014-2017. Please disable the app option "Request Audio Focus" and use Bluetooth audio

Build Instructions
------------------

  - (Optional) Add a [Google Maps API key](https://developers.google.com/maps/documentation/android-sdk/signup) to `~/.gradle/gradle.properties` as a property named `AndroidAutoIdrive_GmapsApiKey`
  - Check out the project in Android Studio, then `Build > Make Project`
  - From the commandline, with an Android build environment set up, `./gradlew assemble` should work too

Screenshots
-----------

![Phone App Interface](https://hufman.github.io/AndroidAutoIdrive/screenshot-app.png)

![Music App List](https://hufman.github.io/AndroidAutoIdrive/screenshot-musicapplist.jpg)

![Music Playback Interface](https://hufman.github.io/AndroidAutoIdrive/screenshot-musicplayback.jpg)

![Music Browse](https://hufman.github.io/AndroidAutoIdrive/screenshot-musicbrowse.jpg)