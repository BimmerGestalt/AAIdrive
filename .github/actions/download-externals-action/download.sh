#!/bin/bash

[ -e 'BMW_Connected_Classic_v1.8_(usa_160214_1142)_apkpure.com.apk' ] ||
wget --quiet -P external 'https://bimmergestalt.s3.amazonaws.com/aaidrive/external/BMW_Connected_Classic_v1.8_(usa_160214_1142)_apkpure.com.apk'
[ -e 'MINI_Connected_Classic_v1.1.3_(usa_160214_448)_apkpure.com.apk' ] ||
wget --quiet -P external 'https://bimmergestalt.s3.amazonaws.com/aaidrive/external/MINI_Connected_Classic_v1.1.3_(usa_160214_448)_apkpure.com.apk'
[ -e 'BMW_Connected_v3.1.1.3078_apkpure.com.apk' ] ||
wget --quiet -P external 'https://bimmergestalt.s3.amazonaws.com/aaidrive/external/BMW_Connected_v3.1.1.3078_apkpure.com.apk'
[ -e 'MINI_Connected_v3.1.1.3078_apkpure.com.apk' ] ||
wget --quiet -P external 'https://bimmergestalt.s3.amazonaws.com/aaidrive/external/MINI_Connected_v3.1.1.3078_apkpure.com.apk'
[ -e 'SmartThings_Classic_v2.1.6_apkpure.com.apk' ] ||
wget --quiet -P external 'https://bimmergestalt.s3.amazonaws.com/aaidrive/external/SmartThings_Classic_v2.1.6_apkpure.com.apk'
[ -e 'Spotify_Music_v8.4.32.623_apkpure.com.apk' ] ||
wget --quiet -P external 'https://bimmergestalt.s3.amazonaws.com/aaidrive/external/Spotify_Music_v8.4.32.623_apkpure.com.apk'
