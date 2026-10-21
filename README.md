
![Glass Home](docs/home.png)

# Glass Home: Android TV launcher

A clean, light home screen for Android TV with a glass highlight, an app dock, a clock and in-app customization. Free, with no ads and no tracking.

> **No internet permission.** Glass Home cannot send any data anywhere. The only sensitive permission is location, which is optional and off by default. It is used only to show your Wi-Fi network name.

**Requirements:** Android TV / Google TV with Android 8.0 or newer. Samsung Tizen, LG webOS and Fire TV are not supported.


Android only installs an update over Glass Home if it is signed with this same certificate.

## Install
1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. Go to **Settings → Apps → Special app access → Install unknown apps**, then turn it on for **Downloader** only.
3. Open Downloader, enter the release download link, and install.
4. If Play Protect shows "unknown app", choose **Install anyway**. This happens with any app installed from outside the store.
5. Press **Home** and choose **Glass Home**, then **Always**.

### If the TV doesn't ask which home screen to use
Some TVs, including newer Google TV devices, don't show the choice. Set it from a computer with ADB:
```
adb connect <TV-IP>:5555
adb shell cmd package set-home-activity com.glasshome.tv/.MainActivity
```

## Use
- **D-pad:** moves between apps. Up from the dock goes to the top bar.
- **OK:** opens the selected app.
- **Menu, or hold OK:** add an app to the dock, move it, remove it, hide it, or open its App info.
- **Customize** (top bar): wallpaper, glow, grid columns, app names, clock, greeting, date, Wi-Fi name and hidden apps.

## Uninstall
Go to **Settings → Apps → Glass Home → Uninstall**. The TV goes back to its original home screen.

---

# Glass Home: لانشر لتلفزيونات أندرويد

شاشة رئيسية خفيفة وأنيقة لأجهزة Android TV: تحديد زجاجي، وشريط تطبيقات، وساعة، وتخصيص من داخل التطبيق. مجاني، بدون إعلانات وبدون تتبع.

> **بدون إذن إنترنت.** Glass Home ما يقدر يرسل أي بيانات لأي مكان. الإذن الحساس الوحيد هو الموقع، وهو اختياري ومطفي افتراضياً، ويُستخدم فقط لعرض اسم شبكة الـ Wi-Fi.

**المتطلبات:** جهاز Android TV أو Google TV بإصدار أندرويد 8.0 أو أحدث. ما يدعم تلفزيونات سامسونج (Tizen) وإل جي (webOS) وأجهزة Fire TV.


أندرويد ما يقبل تثبيت تحديث فوق Glass Home إلا إذا كان موقّعاً بنفس هذه الشهادة.

## التثبيت
1. ثبّت تطبيق **Downloader** (من AFTVnews) من متجر Play في التلفزيون.
2. ادخل **الإعدادات ← التطبيقات ← الوصول الخاص ← تثبيت تطبيقات غير معروفة**، وفعّل الخيار لـ **Downloader** فقط.
3. افتح Downloader، واكتب رابط التحميل، وثبّت التطبيق.
4. إذا طلع تحذير Play Protect "تطبيق غير معروف"، اختر **تثبيت على أي حال**. هذا يصير مع أي تطبيق من برا المتجر.
5. اضغط زر **Home** واختر **Glass Home**، ثم **دائماً**.

### إذا ما سألك التلفزيون عن الشاشة الرئيسية
بعض التلفزيونات، ومنها أجهزة Google TV الجديدة، ما تعرض هذا الخيار. فعّلها من الكمبيوتر عن طريق ADB:
```
adb connect <IP-التلفزيون>:5555
adb shell cmd package set-home-activity com.glasshome.tv/.MainActivity
```

## الاستخدام
- **الأسهم:** للتنقل بين التطبيقات. السهم الأعلى من الشريط يوصلك للأزرار العلوية.
- **OK:** يفتح التطبيق المحدد.
- **زر القائمة أو الضغط المطوّل على OK:** إضافة التطبيق للشريط، أو تحريكه، أو إزالته، أو إخفاؤه، أو فتح معلومات التطبيق.
- **تخصيص** (في الأعلى): الخلفية، والتوهج، وعدد الأعمدة، وأسماء التطبيقات، والساعة، والتحية، والتاريخ، واسم الشبكة، والتطبيقات المخفية.

## الحذف
**الإعدادات ← التطبيقات ← Glass Home ← إلغاء التثبيت**، ويرجع التلفزيون للشاشة الرئيسية الأصلية.


## Screenshots

![Aurora wallpaper](docs/aurora.png)
![Customize](docs/customize.png)

## Build from source

Requires JDK 17 and the Android SDK. Put your own signing key in `../signing/keystore.properties` and run `tools/build-release.ps1`.

Font: [Outfit](https://github.com/Outfitio/Outfit-Fonts), SIL Open Font License 1.1 (`launcher/app/src/main/assets/OFL.txt`).
