<p align="center">
  <img src="art/potato.svg" width="190" alt="Potato, a pink octopus in sunglasses">
</p>

<h1 align="center">octo potato</h1>

<p align="center">
  an android app that reads your screenshots so you can find them again<br>
  <sub>bins the ones you only needed for a day, and lets you swipe through old photos like it's a dating app</sub>
</p>

<p align="center">
  <a href="https://github.com/teamomuito/octo-potato/releases/latest"><img src="https://img.shields.io/badge/get%20the-apk-e8508f?style=flat-square" alt="get the apk"></a>
  <img src="https://img.shields.io/badge/android-11%2B-ffb3d1?style=flat-square" alt="android 11 and up">
  <img src="https://img.shields.io/badge/reads-on%20device-c9b6ff?style=flat-square" alt="reads on device">
</p>

<br>

You screenshot something so you won't forget it. A few months later there are 2,000 screenshots and you can't find the one you wanted.

Octo Potato reads the words in every screenshot on your phone and lets you search them. Type `wifi` and there's the router password. Type `pasta` and there's that recipe from someone's story. Potato is the octopus. Potato does the reading.

And for the rest of the camera roll there's swipe cleanup: old photos and videos one month at a time, right to keep, left to say bye. Then there's a deep clean tab for caches, thumbnails and all the other junk a phone collects.

## what it does

- Searches the text in all your screenshots, old and new. New ones get read shortly after you take them.
- Also searches the file name, so on phones that put the app name in there (`Screenshot_..._Instagram.jpg`) you can just type `instagram`.
- Notices the throwaway stuff: QR codes, boarding passes and login codes.
- A week later those go to the trash. Not deleted outright, Android keeps trashed files for 30 days in case you change your mind.
- Anything you flip to **keep** stays forever. You can change the week, pick which kinds get tidied, or turn the whole thing off.

## the one week thing

A QR code for a parking lot, a boarding pass, `your code is 482913`. Useful for ten minutes, then they sit in your gallery for years.

Potato gives them a week, counted from whenever it first saw them. So installing the app doesn't suddenly flag last year's boarding passes, they get a week too.

Android doesn't let an app delete other apps' files from the background, so once a day Potato checks what's due and sends a notification. Tap it and they're gone. If you give it "media management" access in settings (Android 12 and up), it skips the confirm popup and tidies by itself whenever you open the app.

It tries hard not to be wrong. A tiny QR code in the corner of a web page doesn't count, a random 4 digit number doesn't make something a login code, and years like 2024 are ignored. If it still gets one wrong, the **keep** switch is right there.

## swipe cleanup

Your camera roll, one month at a time, like a dating app for old photos. Swipe right to keep, left to say bye. Oldest months come first, since that's where the forgotten stuff lives (blurry concert videos, fourteen photos of the same sunset).

- Every month shows how many photos and videos it has, how much space they take and how far you got.
- Swipes are remembered. Stop halfway through March 2019 and it picks up right there, and anything you kept never comes back.
- There's an undo button, and before anything goes you get the whole bye pile to look over. Tap one to keep it after all.
- Deleting happens in one go when the month is done, and a counter keeps track of how much space you've freed so far.
- By default things go to the phone's trash first, so you still have 30 days to change your mind. If you'd rather get the space back right away, there's a switch for that in settings.

## deep clean

The third tab looks for the junk every phone piles up. It shows how much cache each app is sitting on, with one button that opens Android's own "clear all app caches" screen (or tap an app to clear just that one). It also finds the cache folders and temp files apps leave lying around in your storage, the thumbnail copies your gallery makes, empty folders left behind by apps you removed, big files you haven't touched in three months, and apps you haven't opened in over a month.

Everything gets listed before anything happens. The safe stuff starts ticked, big files never do, and nothing is deleted until you say so. Standard folders like DCIM and Download are never removed, hidden folders are left to the apps that made them, and anything in Documents counts as yours (only big files show up from there, unticked). Uninstalling goes through Android's usual prompt.

It needs two special permissions you switch on yourself: all files access, to look through your storage, and usage access, for app sizes and when each app was last opened. Both are optional and the other tabs don't use them. Android no longer lets any app delete other apps' private caches directly, which is why that part hands over to Android's own screen.

## install

Download `octo-potato.apk` from [releases](https://github.com/teamomuito/octo-potato/releases/latest) and open it on your phone. Android will ask if your browser is allowed to install apps, that's normal for anything that isn't from the Play Store.

Needs Android 11 or newer.

## build it yourself

You need JDK 17 and the Android SDK. Android Studio sets up both.

```sh
./gradlew assembleDebug
```

The apk lands in `app/build/outputs/apk/debug/`. The tests for the "is this a boarding pass?" logic run with `./gradlew testDebugUnitTest`.

## how it works

- MediaStore query for anything in a Screenshots folder (or named like one)
- [ML Kit](https://developers.google.com/ml-kit) text recognition and barcode scanning, both on the phone. Long scrolling screenshots are read in slices.
- The words go into a SQLite FTS4 table, which is why search is instant even with thousands of screenshots
- WorkManager wakes up when a new image lands, plus once a day for the tidy check
- Swipe cleanup reads photos and videos straight from MediaStore, and your swipes live in a small SQLite table so they survive restarts
- Deep clean walks shared storage once per scan, and the rules for what counts as junk are plain functions with their own tests
- Jetpack Compose for the UI, [Sniglet](https://fonts.google.com/specimen/Sniglet) for the round letters
- Potato is a hand-drawn SVG, the same shapes are used for the app icon. See [`art/`](art)

## questions

**Does it upload my screenshots?**
No. There's no server and no account. The reading happens on the phone. ML Kit, the Google library that does the reading, can send Google basic stats about how the library itself is performing, but never your images or the text in them.

**It can't read some of my screenshots.**
Right now it reads Latin script, so English, Portuguese, Spanish, French and friends. Chinese, Japanese, Korean and Devanagari aren't in yet.

**It thought my concert ticket was a boarding pass.**
Flip **keep** on it and it's safe. If you open an issue with what the screenshot roughly looked like (minus anything personal), the rules can get better.

**Does swipe cleanup delete things as I swipe?**
No, swiping left only puts things in the bye pile. Nothing is deleted until you tap delete at the end, and Android asks you to confirm (unless you gave Potato "media management" access in settings).

**Why is the octopus called Potato?**
Look at that face.

## making a release

Push a tag like `v1.0` and the build workflow attaches the apk to a GitHub release. To sign it with a real key instead of a throwaway debug one, add these repo secrets: `KEYSTORE_BASE64` (the .jks file, base64 encoded), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Keep using the same key, otherwise phones won't accept the update over the old version.

## license

The code is MIT. Sniglet is under the SIL Open Font License, see [`licenses/`](licenses).

<p align="center"><sub>made by <a href="https://github.com/teamomuito">@teamomuito</a></sub></p>
