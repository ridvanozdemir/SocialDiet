# SocialDiet

Android-first social diet application prototype.

## V1 scope

- Firebase email/password authentication
- Unique username claim stored in Firestore
- User profile with start/current/target weight
- Friend requests and accepted friends (next implementation milestone)
- Meal photo from camera/gallery (next implementation milestone)
- On-device AI calorie estimate + user correction (next implementation milestone)
- Daily calorie total
- Daily and weekly leaderboard based on target adherence
- Program completion when target weight is reached

## Firebase

Project: `socialdiet-1a7f8`

Android package: `com.ridvanozdemir.socialdiet`

The Android Firebase configuration is stored at `app/google-services.json`. Firebase Android API keys are app configuration identifiers; application data must be protected by Authentication, Firestore Security Rules and later App Check.

The repository contains `firestore.rules` and `firebase.json`. The rules must also be published in Firebase Console (Firestore > Rules) before the app can create profiles.

## Firestore schema

```text
usernames/{normalizedUsername}
  uid
  username

users/{uid}
  uid
  email
  username
  displayName
  heightCm
  startWeightKg
  currentWeightKg
  targetWeightKg
  dailyCalorieTarget
  programCompleted

users/{uid}/weights/{entryId}
  userId
  weightKg
  createdAt

meals/{mealId}
friendships/{friendshipId}
dailyStats/{uid_yyyyMMdd}
```

## Leaderboard rule

The app should not reward eating the fewest calories. Ranking will be based on closeness to the user's own daily calorie target and completion consistency.

Draft score rule:

- If daily intake is below 75% of target, adherence score = 0.
- Otherwise score falls as absolute deviation from target grows.
- A score of 100 means the daily total is exactly at target.
- The weekly leaderboard sums/averages valid daily adherence scores.

This scoring rule is a game mechanic, not medical advice, and will be reviewed before production.

## Build

The project targets Android 16 / API 36, uses Kotlin + Jetpack Compose, CameraX and Firebase.

GitHub Actions builds a debug APK on every push to `main` and uploads it as the `socialdiet-debug-apk` artifact.
