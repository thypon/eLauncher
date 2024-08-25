# eLauncher

eLauncher is an extremely lightweight and minimal launcher for Android, based on NoLauncher and inspired by [OLauncher Light](https://github.com/tanujnotes/Ultra/), and OLauncher in general. It is even more barebones than OLauncher Light, and aims to provide only the most basic features.

eLauncher favours easy readibility on eInk/ePaper devices, such as the Onyx Boox Note series, and the Bigme HiBreak.

## Features

- Extremely lightweight: only 779KB
- eInk friendly: uses a light theme by default

- Homescreen and app drawer: swipe up on homescreen to enter the app drawer
- Long press an app field on the homescreen to assign an app, app can be renamed
- Type to search in app drawer, if only one result is left, it is automatically launched (like OLauncher)
- Gestures: swipe down for notification center, left for browser app, right for phone app, double tap to open app drawer with keyboard
- Hold on empty space to change the number of apps on homescreen

## apk size differences with OLauncher Light

This might have been done on purpose, but OLauncher Light uses long deprecated APIs, like ListView to achieve its impressive 23 KB apk size. eLauncher uses RecyclerView, which is much better for performance and memory usage, and also uses many other newer APIs. Thus, the APK size is much larger than with OLauncher Light, but still really small - 1.8 MB.

## Download

You can download the apk file directly from the releases tab and install it manually.

## Contributing

Feel free to contribute if you found a bug or have a way to make the code more efficient or minimal, but please don't add massive new features. If you feel like adding a lot of customization options, widgets, etc. please start your own fork, as the scope of this project is to be as (reasonably) barebones of a launcher as possible.
