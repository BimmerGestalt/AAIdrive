Android Auto for IDrive
=======================

The BMW/Mini IDrive does not offer native Android Auto integration, but does provide a much more powerful Connected Apps connectivity option, which offers many exciting integration points into the car.

Implemented Features
--------------------

  - Phone Notifications
    - Popup about new notifications
  - Google Maps
    - Basic search and routing
    - Not enabled by default, showing Google Maps in a car is against the API EULA
  - Control of Android Auto compatible music apps
    - Supports browsing and searching app libraries
    - Supports selecting from a list of currently-queued songs
    - Integrates into the car's audio context, for automatic resume and hardware button control

Limitations
-----------

Due to the unofficial reverse-engineered nature of this project, it has some limitations

  - Some Android Auto music apps enforce a whitelist of clients, preventing this app from connecting and controlling them
  - The main menu entries' icons and text can't be edited, and so do not look exactly correct
  - The individual music app icons are not fully functional, but they do switch the active music source
  - Android Oreo disabled Android Open Accessory Protocol 2 audio output, which is required to play audio over USB in IDrive 4++ used by Mini 2014-2017 and BMW i3 2014-2017. Please disable the app option "Request Audio Focus" and use Bluetooth audio

Screenshots
-----------

![Phone App Interface](https://hufman.github.io/AndroidAutoIdrive/screenshot-app.png)

![Music App List](https://hufman.github.io/AndroidAutoIdrive/screenshot-musicapplist.jpg)

![Music Playback Interface](https://hufman.github.io/AndroidAutoIdrive/screenshot-musicplayback.jpg)

![Music Browse](https://hufman.github.io/AndroidAutoIdrive/screenshot-musicbrowse.jpg)