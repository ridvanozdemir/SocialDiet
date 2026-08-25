# SocialDiet

Android-first social diet application prototype.

## V1 scope

- Email/password authentication
- User profile with start/current/target weight
- Friend requests and accepted friends
- Meal photo from camera/gallery
- On-device AI calorie estimate + user correction
- Daily calorie total
- Daily and weekly leaderboard based on target adherence
- Program completion when target weight is reached

## Firestore draft schema

```text
users/{uid}
  username
  displayName
  photoUrl
  heightCm
  startWeightKg
  currentWeightKg
  targetWeightKg
  dailyCalorieTarget
  programCompleted

users/{uid}/weights/{entryId}
  date
  weightKg

meals/{mealId}
  userId
  mealType
  imageUrl
  aiLabel
  aiCalories
  confirmedCalories
  createdAt

friendships/{friendshipId}
  userA
  userB
  status
  createdAt

dailyStats/{uid_yyyyMMdd}
  userId
  date
  calorieTarget
  calorieTotal
  adherenceScore
```

## Leaderboard rule

The app should not reward eating the fewest calories. Ranking is based on closeness to the user's own daily calorie target and completion consistency.

Draft score rule:

- If daily intake is below 75% of target, adherence score = 0.
- Otherwise score falls as absolute deviation from target grows.
- A score of 100 means the daily total is exactly at target.
- The weekly leaderboard sums/averages valid daily adherence scores.

This scoring rule is a game mechanic, not medical advice, and will be reviewed before production.

## Firebase setup (next milestone)

1. Create Firebase project.
2. Add Android app with package `com.ridvanozdemir.socialdiet`.
3. Add `google-services.json` to `app/`.
4. Enable Authentication, Firestore and Storage.
5. Add Google Services Gradle plugin and security rules.

## Build

The project targets API 37, uses Kotlin + Jetpack Compose, CameraX and Firebase dependencies.
