# SocialDiet

Android-first social diet application with on-device nutrition analysis and friend-based adherence tracking.

## V1 scope

- Firebase email/password authentication
- Password reset, email verification and password change
- Google sign-in via Android Credential Manager
- Unique username claim stored in Firestore
- User profile with start/current/target weight and progress tracking
- In-app account deletion with owned Firestore data cleanup
- Meal photo from camera/gallery
- On-device AI calorie, portion and macro estimate + user correction
- Turkish food classification with TürKomp nutrition calibration
- Real daily calorie total and per-meal breakdown
- Username search, friend requests, accept/reject/remove flows
- Privacy-safe friend profile progress view
- Daily and weekly friend leaderboard based on target adherence
- Program completion when target weight is reached

## Firebase

Project: `socialdiet-1a7f8`

Android package: `com.ridvanozdemir.socialdiet`

The Android Firebase configuration is stored at `app/google-services.json`. Firebase Android API keys are app configuration identifiers; application data must be protected by Authentication, Firestore Security Rules and later App Check.

The repository contains `firestore.rules` and `firebase.json`. The current rules must be published in Firebase Console before testing the social features.

### Google sign-in setup

Google sign-in code is implemented with Credential Manager. The Firebase project still needs the following console configuration before the button can authenticate users:

1. Enable Google in Firebase Authentication > Sign-in method.
2. Add the Android app signing SHA-1 fingerprint in Firebase project settings.
3. Download the refreshed `google-services.json` and replace `app/google-services.json`.

The app detects a missing OAuth client configuration and shows a setup error instead of crashing.

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

publicProfiles/{uid}
  uid
  username
  displayName
  progressPercent
  programCompleted

meals/{mealId}
  userId
  mealType
  aiLabel
  aiConfidence
  calorieSource
  aiCalories
  confirmedCalories
  estimatedMassGrams
  fatGrams
  carbsGrams
  proteinGrams
  createdAt

friendships/{sortedUidPair}
  userA
  userB
  requesterId
  recipientId
  status
  createdAt
  updatedAt

dailyStats/{uid_yyyyMMdd}
  userId
  dateIso
  calorieTarget
  calorieTotal
  adherenceScore

publicDailyStats/{uid_yyyyMMdd}
  userId
  dateIso
  adherenceScore
```

`users`, weights, meals and private daily stats are owner-only. Social search and friend profile views use `publicProfiles`, which deliberately excludes email and raw weight values. The leaderboard shares only adherence scores through `publicDailyStats`.

## Leaderboard rule

The app does not reward eating the fewest calories. Ranking is based on closeness to each user's own daily calorie target.

- If daily intake is below 75% of target, adherence score = 0.
- Otherwise score falls as absolute deviation from target grows.
- A score of 100 means the daily total is exactly at target.
- The weekly leaderboard uses the average of the latest seven daily adherence scores.

This scoring rule is a game mechanic, not medical advice.

## Build

The project targets Android 16 / API 36, uses Kotlin + Jetpack Compose, CameraX, Firebase and LiteRT.

GitHub Actions builds a debug APK for `main` pushes and pull requests and uploads it as the `socialdiet-debug-apk` artifact.
