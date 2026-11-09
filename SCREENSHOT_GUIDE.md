# QueueLy - USIU Queue Management App (Mockup Screens)

Android Studio project with 6 screenshot-ready screens using USIU brand colors (Navy `#003366`, Gold `#FFD700`).

## Open in Android Studio

1. Open Android Studio → **File → Open**
2. Select folder: `C:\Users\Admin\queuely`
3. Wait for Gradle sync to finish
4. Run on emulator or physical device

## Screens (6 total)

| # | Screen | Activity | How to reach |
|---|--------|----------|--------------|
| 1 | Login | `LoginActivity` | App launch (default) |
| 2 | School Selection | `SchoolSelectionActivity` | Tap **Log In** |
| 3 | Request Type | `RequestTypeActivity` | Tap **Continue** |
| 4 | Queue Status | `QueueStatusActivity` | Tap **Join Queue** |
| 5 | Queue History | `QueueHistoryActivity` | Tap **View History** from Queue Status |
| 6 | Your Turn Alert | `YourTurnActivity` | Run activity directly (see below) |

## Screenshot "Your Turn" screen

Right-click `YourTurnActivity.java` in Android Studio → **Run 'YourTurnActivity'**

Or temporarily set it as launcher in `AndroidManifest.xml`.

## Navigation flow

```
Login → School Selection → Request Type → Queue Status → Queue History
                                              ↓
                                        Your Turn (run directly)
```

## USIU Colors

- Navy Blue: `#003366` (headers, backgrounds)
- Gold: `#FFD700` (CTAs, queue numbers, accents)
- White / Light Gray: cards and content areas
