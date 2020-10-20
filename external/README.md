Car Resources
=============

The car requires that all Connected Apps authenticate with BMW-signed certificate files.
This project needs copies of these official certificate files to log in to the car.
These certificates and other resource files can be found in official Connected Apps, and every compatible Connected App will have a `assets/carapplications` directory inside it's APK File.
This project's build script automatically extracts the needed files from these official APKs during compilation.

Generally, the exact version of the APK doesn't matter, as long as it contains the necessary resources.
The main challenge is that some apps have removed their car compatibility, such as Spotify and SmartThings, and so specific older versions need to be found.

Please place copies of the following Android APKs in this `external` directory:
  - [BMW Connected Classic](https://apkpure.com/bmw-connected-classic/com.bmwgroup.connected.bmw.usa/download?from=details)
  - [BMW Connected](https://apkpure.com/bmw-connected/de.bmw.connected.na/download?from=details)
  - [MINI Connected Classic](https://apkpure.com/mini-connected-classic/com.bmwgroup.connected.mini.usa/download?from=details)
  - [MINI Connected](https://apkpure.com/mini-connected/de.mini.connected.na/download?from=details)
  - [SmartThings Classic](https://apkpure.com/smartthings-classic/com.smartthings.android/download/211001-APK?from=versions%2Fversion) v2.3.1 or earlier
  - [Spotify](https://spotify.en.uptodown.com/android/download/2292403) v8.5.66 or earlier

After placing these files in this `external` directory, re-run the build process and it should complete successfully.
The build process should automatically extract the necessary files to `app/src/main/assets/carapplications` and `app/src/test/resources`.
