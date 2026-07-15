# RR License Generator - Setup

## 1. Firebase Console - Register App Baru

1. Buka https://console.firebase.google.com → Project RR Billing Pro
2. Project Settings → General → Add app → Android
3. **Package name**: `com.billingps.licensegen`
4. **App nickname**: `RR License Generator`
5. **Debug signing certificate SHA-1**: (isi dengan SHA-1 debug.keystore)
   ```
   keytool -list -v -keystore $env:USERPROFILE/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | findstr "SHA1"
   ```
6. Download `google-services.json` → letakkan di `license-generator/app/`
7. Klik Next sampai selesai

## 2. Firestore - Setup Admin

Di Firebase Console → Firestore → buat collection `admins`:

- Document ID: (user UID dari Firebase Auth)
- Fields:
  - `isAdmin`: `true` (boolean)
  - `username`: `"rrgaming"` (string)

Cara dapat UID:
1. Firebase Console → Authentication → cari email admin → copy UID
2. Atau login dulu di generator app, lalu cek logcat untuk UID

## 3. Build & Install

```bash
cd license-generator
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## 4. Firestore Security Rules

Di Firebase Console → Firestore → Rules → update:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /licenses/{docId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null && exists(/databases/$(database)/documents/admins/$(request.auth.uid));
      allow update: if request.auth != null;
    }
    match /admins/{userId} {
      allow read: if request.auth != null && request.auth.uid == userId;
      allow write: if request.auth != null && request.auth.token.email == 'admin@example.com';
    }
  }
}
```

## 5. Akun Admin

Buat akun di Firebase Console → Authentication → Add user:

- Email: (email admin)
- Password: (password kuat)
- Verifikasi email melalui console (lebih mudah)

Lalu di Firestore → admins → buat dokumen dengan ID = UID user tersebut.
