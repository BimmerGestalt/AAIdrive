Android Auto for IDrive
=======================

[![Build Status](https://travis-ci.org/hufman/AndroidAutoIdrive.svg?branch=master)](https://travis-ci.org/hufman/AndroidAutoIdrive)
[![Coverage Status](https://coveralls.io/repos/github/hufman/AndroidAutoIdrive/badge.svg?branch=master)](https://coveralls.io/github/hufman/AndroidAutoIdrive?branch=master)

The BMW/Mini IDrive does not offer native Android Auto integration, but does provide a much more powerful Connected Apps connectivity option, which offers many exciting integration points into the car.
This project is an effort to implement most of the features of Android Auto as an unofficial IDrive Connected App.

Overview
--------

As part of the Connected Apps feature, when the phone connects over USB (or Bluetooth in 2017+ models), a tunnel to the car is created to allow other apps on the phone to interact with the car.
A Connected App uses this connection to upload its widget layout to the car, receive event callbacks from user selections, and update widget contents in response.
This remote UI framework effectively creates a custom application in the car, while enabling tight user integration and excellent data availability due to actually running all logic on the phone.

Android Auto for IDrive, combined with the safety benefits of the tactile IDrive controller, allows the user to safely interact with their incoming notifications and control their phone's music, while the phone is tucked out of reach.

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
    - Tested working apps:
      - [Black Player](https://play.google.com/store/apps/details?id=com.kodarkooperativet.blackplayerfree)
      - [Dash Player](https://play.google.com/store/apps/details?id=com.dashradio.dash)
      - [DSub](https://play.google.com/store/apps/details?id=github.daneren2005.dsub)
      - [Media Monkey](https://play.google.com/store/apps/details?id=com.ventismedia.android.mediamonkey)
      - [Player FM](https://play.google.com/store/apps/details?id=fm.player)
      - [Plex](https://play.google.com/store/apps/details?id=com.plexapp.android) (only music)
      - [PowerAmp](https://play.google.com/store/apps/details?id=com.maxmpz.audioplayer)
      - [Rocket Player](https://play.google.com/store/apps/details?id=com.jrtstudio.AnotherMusicPlayer)
      - [Scanner Radio](https://play.google.com/store/apps/details?id=com.scannerradio)
      - [Spotify](https://play.google.com/store/apps/details?id=com.spotify.music)
      - [Tidal](https://play.google.com/store/apps/details?id=com.aspiro.tidal)
      - [YouTube](https://play.google.com/store/apps/details?id=com.google.android.youtube) (only music previous/next control, no video)

Limitations
-----------

Due to the unofficial reverse-engineered nature of this project, it has some limitations:

  - The main menu entries' icons and text can't be altered, and so do not look exactly correct
  - The individual music app icons are not fully functional, but they do switch the active music source
  - Android Oreo disabled Android Open Accessory Protocol 2 audio output, which is required to play audio over USB in IDrive 4++ used by Mini 2014-2017 and BMW i3 2014-2017. Please disable the app option "Request Audio Focus" and use Bluetooth audio
  - Some Android Auto music apps enforce a whitelist of clients, preventing this app from connecting and controlling them. Some tested unavailable apps are:
    - Audible
    - Bandcamp
    - CloudPlayer
    - Deezer
    - doubleTwist
    - Google Play Music
    - iHeartAuto
    - TuneIn Radio
    - Pandora
    - Smart Audiobook Player
    - YouTube Music

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
