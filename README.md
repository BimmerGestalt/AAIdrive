AAIdrive
========

[![Build Status](https://img.shields.io/github/workflow/status/BimmerGestalt/AAIdrive/build.svg)](https://github.com/BimmerGestalt/AAIdrive/actions?query=workflow%3Abuild)
[![Code Coverage](https://img.shields.io/codecov/c/gh/BimmerGestalt/AAIdrive/main.svg)](https://codecov.io/gh/BimmerGestalt/AAIdrive)
[![Crowdin](https://badges.crowdin.net/androidautoidrive/localized.svg)](https://crowdin.com/project/androidautoidrive)
[![Release Download](https://img.shields.io/github/release/BimmerGestalt/AAIdrive.svg)](https://github.com/BimmerGestalt/AAIdrive/releases/latest)
[![Download Counter](https://img.shields.io/github/downloads/BimmerGestalt/AAIdrive/total.svg)](https://tooomm.github.io/github-release-stats/?username=BimmerGestalt&repository=AAIdrive)
[![Weekly Users](https://img.shields.io/endpoint?url=https://bimmergestalt.s3.amazonaws.com/aaidrive/usage/weekly_users.json)](https://bimmergestalt.s3.amazonaws.com/aaidrive/usage/car_report.html)
[![Gitter](https://badges.gitter.im/AndroidAutoIdrive/community.svg)](https://gitter.im/AndroidAutoIdrive/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Buy Me A Coffee](https://img.shields.io/badge/support-buymeacoffee-5f7fff)](https://www.buymeacoffee.com/q4JVoxz)
![MIT Licensed](https://img.shields.io/github/license/BimmerGestalt/AAIdrive)

<a href="https://play.google.com/store/apps/details?id=me.hufman.androidautoidrive"><img height="60" alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"/></a>

The BMW/Mini IDrive NBT does not offer native Android Auto integration, but does provide a very powerful Connected Apps convergence option with tight integration points into the car. This project is an effort to implement most of the features of Android Auto as unofficial BMW/Mini Connected Apps.

By relying on the Connected Apps technology, this app greatly extends the functionality of the car without any modifications or hacks. Any MY2014 or newer BMW or Mini equipped with NBT or NBT Evo and the "BMW Apps (6NR)" feature, an active [BMW ConnectedDrive subscription](https://bimmergestalt.github.io/AAIdrive/images/bmw-connected-subscription.png), or the "Mini Connected (SA6NM)" option should be compatible.

[![App List](docs/images/demo-applist.gif)<br />Gallery](https://bimmergestalt.github.io/AAIdrive/gallery.html)

Overview
--------

As part of the Connected Apps feature, when the phone connects to the car over USB (or Bluetooth in 2017+ models), enabled phone apps can show a special dashboard-optimized interface in the car.

AAIdrive, combined with the safety benefits of the tactile IDrive controller, builds on this protocol to allow the user to interact with their incoming notifications and control their phone's music while the phone is safely tucked away.

Getting Started
---------------

This app requires that the MyBMW or MINI app for your car is installed and that it can successfully enable your ID5 car's [Apps checkbox](app/src/main/res/drawable/pic_btapp_bmw.jpg), or if your ID4 car has the [Connection Assistant option](app/src/main/res/drawable/pic_connassistant_bmw.jpg).

Download the APK of the latest stable release from the [Releases page](https://github.com/BimmerGestalt/AAIdrive/releases/latest). Choose the one that says "sentry" to automatically upload crash reports, or choose "nonalytics" otherwise. It is also available on the [Google Play Store](https://play.google.com/store/apps/details?id=me.hufman.androidautoidrive). After starting, the app should detect the MyBMW app and start waiting for the car connection.

Also consider trying out the nightly build! It has the latest features and is a preview of the next release, so please consider installing the [Sentry build](https://bimmergestalt.s3.amazonaws.com/aaidrive/builds/androidautoidrive-latest-main-mapbox-sentry-release.apk) to automatically report crashes.
The [nonalytics](https://bimmergestalt.s3.amazonaws.com/aaidrive/builds/androidautoidrive-latest-main-mapbox-nonalytics-release.apk) build is available too.

Check out the [FAQ](https://bimmergestalt.github.io/AAIdrive/faq.html) if you run into problems.

User Guide
----------

![Phone Connection](https://bimmergestalt.github.io/AAIdrive/images/screenshot-connection.png)

After connecting the phone to the car, the official MyBMW app should show this car icon in the status bar. When this icon appears, this app should connect and add its functionality to the car.

See [this guide](https://bimmergestalt.github.io/AAIdrive/connection.html) for tips on improving the connection reliability of the MyBMW app.

![Phone App List](https://bimmergestalt.github.io/AAIdrive/images/screenshot-phoneapps.jpg)

After all the apps are connected, a bunch of new entries will show up in the car's Connected menu. There should be a new Audioplayer or Spotify icon and a book icon with no label. This book icon is the Notifications app, if enabled.

![Music App List](https://bimmergestalt.github.io/AAIdrive/images/screenshot-medialist.jpg)

Several new entries will be added to the Media section of the control screen. The Audioplayer icon is the one with the main functionality, while the other displayed apps above the Audioplayer are quick shortcuts to switch playback to the respective apps. This screenshot also shows the legacy Spotify app at the bottom of the list.

Implemented Features
--------------------

  - Calendar integration
    - View the phone's calendar in the car
    - Automatically trigger the car's navigation to upcoming appointments
  - Car Information
    - Remembers fuel level, window status, and car's location after parking
    - Shows live-updating speed and compass while connected
  - Car Navigation Integration
    - Android Navigation Intents and buttons can be handled by the connected car's navigation
    - Google Maps can share destinations to the connected car's navigation
    - Addresses can be entered in the phone interface to start the car's navigation
  - Google Assistant
    - Any voice assistant installed on the phone is added as a Connected App entry
    - Google Assistant works the best, but Alexa, Bixby, and Cortana are also compatible
    - The voice assistant app can be set as a hardware shortcut button for convenience
  - Phone Notifications
    - Popup about new notifications
    - Can play a notification sound through the car's speakers
    - Supports Dismiss, Mark As Read, or other notification actions
    - Supports replying, including emoji input
  - Google Maps (proof-of-concept)
    - Basic search and routing
    - Includes some dark themes
    - Poor performance due to the nature of the protocol
    - Not compiled by default, because showing Google Maps in a car is against the Maps API license
  - Mapbox custom map
    - Search and routing, satellite imagery and traffic, 3d tilt and buildings
    - Supports custom Mapbox style urls
    - Poor performance over Bluetooth
  - Control of Android Auto compatible music apps
    - Supports browsing and searching apps' music libraries, including a special Spotify integration
    - Supports selecting from a list of currently-queued songs, as well as basic back/next control
    - Integrates into the car's audio context, for automatic resume and hardware button control
    - Supports the ID5 music layout, enabling global coverart integration
    - Supports controlling any active music session, even apps that aren't Android Auto compatible
    - Automatically updates the screen to follow the active app
    - Recommended compatible apps:
      - Audiobooks and Podcasts:
        - [Acast Podcast Player](https://play.google.com/store/apps/details?id=com.acast.nativeapp)
        - [AntennaPod](https://play.google.com/store/apps/details?id=de.danoeh.antennapod)
        - [Audecibel](https://play.google.com/store/apps/details?id=com.podcastsapp)
        - [Blinkest](https://play.google.com/store/apps/details?id=com.blinkslabs.blinkist.android)
        - [The Bob & Tom Show](https://play.google.com/store/apps/details?id=com.radio.station.BOB.TOM)
        - [Castbox](https://play.google.com/store/apps/details?id=fm.castbox.audiobook.radio.podcast)
        - [Google Play Books](https://play.google.com/store/apps/details?id=com.google.android.apps.books)
        - [iVooz](https://play.google.com/store/apps/details?id=com.ivoox.app)
        - [Libro.fm Audiobooks](https://play.google.com/store/apps/details?id=fm.libro.librofm) up to version [3.2.2](https://apkpure.com/libro-fm-audiobooks/fm.libro.librofm/download/90-APK)
        - [Listen Audiobook Player](https://play.google.com/store/apps/details?id=com.acmeandroid.listen)
        - [Player FM](https://play.google.com/store/apps/details?id=fm.player)
        - [Podcast Addict](https://play.google.com/store/apps/details?id=com.bambuna.podcastaddict)
        - [Podcast Republic](https://play.google.com/store/apps/details?id=com.itunestoppodcastplayer.app)
        - [Stitcher](https://play.google.com/store/apps/details?id=com.stitcher.app)
        - [Voice Audiobook Player](https://play.google.com/store/apps/details?id=de.ph1b.audiobook)
      - Music Library
        - [AIMP](https://play.google.com/store/apps/details?id=com.aimp.player)
        - [Black Player](https://play.google.com/store/apps/details?id=com.musicplayer.blackplayerfree)
        - [DSub](https://play.google.com/store/apps/details?id=github.daneren2005.dsub)
        - [HiBy Music](https://play.google.com/store/apps/details?id=com.hiby.music)
        - [jetAudio HD](https://play.google.com/store/apps/details?id=com.jetappfactory.jetaudio)
        - [Media Monkey](https://play.google.com/store/apps/details?id=com.ventismedia.android.mediamonkey)
        - [Musicolet Music Player](https://play.google.com/store/apps/details?id=in.krosbits.musicolet)
        - [Neutron Music Player](https://play.google.com/store/apps/details?id=com.neutroncode.mp)
        - [Plex](https://play.google.com/store/apps/details?id=com.plexapp.android) (only music and podcasts)
        - [PlayerPro](https://play.google.com/store/apps/details?id=com.tbig.playerprotrial)
        - [PowerAmp](https://play.google.com/store/apps/details?id=com.maxmpz.audioplayer)
        - [Retro Music Player](https://play.google.com/store/apps/details?id=code.name.monkey.retromusic)
        - [Rocket Player](https://play.google.com/store/apps/details?id=com.jrtstudio.AnotherMusicPlayer)
        - [TimberX Music Player](https://play.google.com/store/apps/details?id=com.naman14.timberx)
        - [VLC For Android](https://play.google.com/store/apps/details?id=org.videolan.vlc)
      - Radio
        - [AP News](https://play.google.com/store/apps/details?id=mnn.Android)
        - [Antenne Bayern](https://play.google.com/store/apps/details?id=de.antenne.android)
        - [ARD Audiothek](https://play.google.com/store/apps/details?id=de.ard.audiothek)
        - [Audials Radio](https://play.google.com/store/apps/details?id=com.audials)
        - [BFM](https://play.google.com/store/apps/details?id=my.bfm.app)
        - [Dash Radio](https://play.google.com/store/apps/details?id=com.dashradio.dash)
        - [DI.FM](https://play.google.com/store/apps/details?id=com.audioaddict.di)
        - [Energy Radio](https://play.google.com/store/apps/details?id=radioenergy.app)
        - [Guardian](https://play.google.com/store/apps/details?id=com.guardian)
        - [HOT97 Official](https://play.google.com/store/apps/details?id=com.jacapps.whhl)
        - [Manchester United](https://play.google.com/store/apps/details?id=com.mu.muclubapp)
        - [myTuner](https://play.google.com/store/apps/details?id=com.appgeneration.itunerfree)
        - [Nederland.FM](https://play.google.com/store/apps/details?id=nl.nibbixsoft.app)
        - [NHL](https://play.google.com/store/apps/details?id=com.nhl.gc1112.free)
        - [ntv Nachrichten](https://play.google.com/store/apps/details?id=de.lineas.lit.ntv.android)
        - [NYTimes](https://play.google.com/store/apps/details?id=com.nytimes.android)
        - [R101](https://play.google.com/store/apps/details?id=it.r101)
        - [Radio 105](https://play.google.com/store/apps/details?id=it.froggy.android.radio105)
        - [Radio Bob](https://play.google.com/store/apps/details?id=de.radiobob.radio)
        - [Radio FM](https://play.google.com/store/apps/details?id=com.radio.fmradio)
        - [Radio Monte Carlo](https://play.google.com/store/apps/details?id=it.froggy.android.rmc)
        - [Radio Nowy Swiat](https://play.google.com/store/apps/details?id=com.thehouseofcode.radio_nowy_swiat)
        - [RTL 102.5](https://play.google.com/store/apps/details?id=com.rtl.rtlapp)
        - [Scanner Radio](https://play.google.com/store/apps/details?id=com.scannerradio)
        - [SiriusXM](https://play.google.com/store/apps/details?id=com.sirius)
        - [Simple Radio](https://play.google.com/store/apps/details?id=com.streema.simpleradio)
        - [SomaFM](https://play.google.com/store/apps/details?id=com.dgmltn.radiomg.somafm)
        - [SWR3](https://play.google.com/store/apps/details?id=de.swr.swr3radio)
        - [sunshine live](https://play.google.com/store/apps/details?id=app.sunshinelive.de.sunshinelive)
        - [Versuz Radio](https://play.google.com/store/apps/details?id=com.versuzradio)
        - [Virgin Radio Italy](https://play.google.com/store/apps/details?id=it.froggy.android.virginradio)
      - Streaming Services
        - [Apple Music](https://play.google.com/store/apps/details?id=com.apple.android.music)
        - [Anghami](https://play.google.com/store/apps/details?id=com.anghami)
        - [Gaana Music](https://play.google.com/store/apps/details?id=com.gaana)
        - [JioSaavn](https://play.google.com/store/apps/details?id=com.jio.media.jiobeats)
        - [SoundCloud](https://play.google.com/store/apps/details?id=com.soundcloud.android)
        - [Spotify](https://play.google.com/store/apps/details?id=com.spotify.music)
        - [Tidal](https://play.google.com/store/apps/details?id=com.aspiro.tidal)

Integration Points
------------------

Besides showing a self-contained remote UI, the IDrive system offers many exciting integration points. Here are a few that this project supports:

  - The UI widgets automatically take on the respective theme to fit the car
  - The Assistants, Map View, Notification List, and Music Playback screens can be assigned to the physical shortcut buttons in the dashboard
  - Car information is retained after disconnect, such as window status and parked location
  - New notifications trigger a statusbar icon
  - New notifications can trigger a popup
  - New notification popups can be disabled if a passenger is detected in the seat
  - Notifications can be read out by the car's TTS when received or when selected
  - The car's navigation system is available to handle Android Navigation Intents
  - The car's location and heading is shown in the custom map
  - The currently-playing app is displayed along the top of the IDrive screen
  - The currently-playing song title is shown in the IDrive4 Multimedia side panel
  - The currently-playing song coverart and progress is shown in the IDrive5 Home screen and Multimedia side panel
  - On a MY2017+ car supporting Bluetooth Apps, audio focus will be enabled which grants the following extra features:
    - The Media shortcut button opens this app when it is in control of the music
    - Automatically resumes playback when reconnecting to the car
    - Playback pauses when pushing the mute button or during calls
    - The physical back/next buttons can be held down to seek within a track or pressed to skip tracks
    - The steering wheel controls can skip tracks from the instrument cluster
    - Enqueued songs can be scrolled in the instrument cluster, depending on app support

Limitations
-----------

This project replicates some of the features of Android Auto using the IDrive interface, using the same APIs that Android Auto uses to talk to the music apps. It cannot currently provide more advanced Android Auto features, such as:

  - Integration with the car's Voice Assistant button
  - Screen-casting of arbitrary phone apps to the car (Google Maps, Waze, or any other apps)
  - Displaying the original Android Auto interface at all

Due to the unofficial reverse-engineered nature of this project, it has some limitations:

  - The main menu entries' icons and text can't be altered, and so do not look exactly correct
  - The individual music source icons sometimes don't open the Audioplayer interface in ID4, but they do switch the active music source
  - Android Oreo disabled Android Open Accessory Protocol 2 audio output, which is required to play audio over the app's USB connection in model years 2014-2017. Please listen over Bluetooth audio and use this app as a control interface.
  - Some Android Auto music apps enforce a list of allowed client apps, preventing this app from launching them or browsing their libraries. However, once they are running, they can be controlled. For example, these popular music apps can not be launched, they must be started manually:
    - Amazon Music
    - Audible
    - Bandcamp
    - CloudPlayer
    - Deezer
    - doubleTwist
    - Google Play Music
    - iHeartAuto
    - TuneIn Radio
    - Pandora
    - Scribd
    - Smart Audiobook Player
    - YouTube Music

Requirements
------------

To communicate to the car, this project relies on the proxy connection that is created by the main MyBMW or Mini app on the phone. Additionally, the legacy Connected and the Connected Classic apps have been tested as compatible for this purpose, but the Connected app is more resilient against Android's memory management.

Developer Guide
---------------

To build your own test versions of the app and help contribute to the project, follow [this Developer Guide](https://bimmergestalt.github.io/AAIdrive/developers) to set up your development environment.

Privacy
-------

This project contains no advertising or user tracking, and is developed entirely for fun and to enhance the usefulness of the BMW/Mini infotainment system.

The app uses the Internet Permission to make a TCP connection to the car, which is reachable through a localhost socket on the main Connected app. Additionally, some cover art and incoming picture notifications (such as from Hangouts) may be fetched from Internet URLs. No other Internet access is required for the app's functionality.

As part of its functionality, the app collects and remembers some information locally on your phone:

  - The Car Information tab remembers information about the most-recently-connected car, including model and available features, specific driving statistics and vehicle settings, and the last location reported by the car while it was connected
  - The music functionality remembers a list of installed music apps and what features they provide
  - The Spotify integration retains a token to access your Spotify Library

The app also needs some other information to function, but does not remember any of it and will never share it:

  - To establish the car connection, the app identifies which BMW and Mini apps are installed
  - The app watches Bluetooth connections to identify when the car is connected
  - Any installed Voice Assistants are discovered to be added to the car
  - Apps which act as AAIdrive Addons are discovered and presented in the Addons tab
  - If enabled, notifications are read from the phone while connected to the car and then forgotten when disconnected
  - If compiled into the app and then enabled, the custom map feature will use the car or phone's location while displaying the map

The analytics-disabled version will never share any information, and no analytics code is compiled into this version of the app.
The analytics-enabled version provides an option to share some anonymized information to [Sentry](https://www.sentry.io) to assist with debugging and development: If enabled, besides any rare and unfortunate crashes, the app will share any [installed music apps](app/src/sentry/java/me/hufman/androidautoidrive/Analytics.kt) and the capabilities each app provides, as well as the [model and capabilities](app/src/main/java/me/hufman/androidautoidrive/CarInformationDiscovery.kt#L36) of any connected car for usage statistics and feature prioritization.

Each release provides both an analytics-enabled and analytics-disabled option.

<details>
  <summary>Example Analytics Data</summary>

  ### Music App
```
{
  "appId": "github.daneren2005.dsub",
  "appName": "DSub",
  "controllable": "false",
  "connectable": "true",
  "browseable": "true",
  "searchable": "false",
  "playsearchable": "false"
}
```

  ### Car Connection
```
{
  "a4axl": "true",
  "alignment_right": "true",
  "hmi_display_height": "480",
  "hmi_display_width": "1280",
  "hmi_role": "HU",
  "hmi_type": "MINI ID5",
  "hmi_version": "EntryEvo_ID5_1903_Release ID5_1903-490-1837K Build 47 - Rev:203015 2018-11-14 08:39:42",
  "inbox": "true",
  "map": "true",
  "navi": "true",
  "pia": "true",
  "speech2text": "true",
  "speedlock": "true",
  "touch_command": "false",
  "tts": "true",
  "vehicle_country": "US",
  "vehicle_productiondate": "03.00",
  "vehicle_type": "F56",
  "voice": "false"
}
```
</details>