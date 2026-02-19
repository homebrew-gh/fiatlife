# FiatLife

A privacy-first Android application for tracking your fiat finances. All data is stored encrypted via Nostr events on your personal relay and synced across devices. Attachments (bills, statements) are stored on a Blossom server.

## Features

### Paycheck Calculator
- Configure hourly rate and standard hours (default 80 hours biweekly)
- Add overtime hours to see the impact on take-home pay
- Federal, state, county/local income tax withholding calculation
- FICA taxes (Social Security 6.2%, Medicare 1.45% + additional)
- Pre-tax deductions: medical/dental/vision insurance, HSA, FSA, traditional 401(k)
- Post-tax deductions: Roth 401(k), life insurance, AD&D, critical illness, disability
- Direct deposit allocation across multiple bank accounts
- Support for all US filing statuses and all 50 states

### Bills Tracker
- Track household bills by category (mortgage, utilities, insurance, subscriptions, etc.)
- Multiple billing frequencies (weekly through annually)
- Monthly cost normalization across all frequencies
- Mark bills as paid/unpaid
- Attach PDF/image statements via Blossom server
- Category filtering and breakdown

### Financial Goals
- Track goals: retirement, house down payment, emergency fund, vacation, car, etc.
- Progress tracking with visual indicators
- Monthly contribution projections and time-to-goal estimates
- Overall portfolio progress across all goals

### Dashboard
- At-a-glance take-home pay and tax summary
- Monthly bill total and disposable income
- Top financial goals with progress
- Upcoming unpaid bills
- Nostr relay connection status

### Privacy & Sync
- **NIP-42**: Automatic authentication with your personal relay
- **NIP-44**: All data encrypted with XChaCha20-Poly1305 before storage
- **Kind 30078**: Application-specific parameterized replaceable events
- **Blossom Protocol**: Decentralized file storage for bill attachments
- Local Room database cache for offline access
- Key generation and import

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt (Dagger)
- **Local Storage**: Room Database + DataStore Preferences
- **Networking**: OkHttp (WebSocket for Nostr, HTTP for Blossom)
- **Crypto**: secp256k1-kmp + Lazysodium (XChaCha20-Poly1305, HKDF)
- **Serialization**: kotlinx-serialization
- **Image Loading**: Coil

## Project Structure

```
app/src/main/java/com/fiatlife/app/
├── data/
│   ├── blossom/          # Blossom blob storage client
│   ├── local/            # Room database, DAOs, entities
│   ├── nostr/            # Nostr client, NIP-42, NIP-44
│   └── repository/       # Data repositories
├── di/                   # Hilt dependency injection modules
├── domain/model/         # Domain models and business logic
├── ui/
│   ├── components/       # Reusable UI components
│   ├── navigation/       # Navigation graph and screen routes
│   ├── screens/          # Feature screens (dashboard, salary, bills, goals, settings)
│   ├── theme/            # Material 3 green theme
│   └── viewmodel/        # ViewModels for each feature
├── FiatLifeApp.kt        # Application class
└── MainActivity.kt       # Entry point
```

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 35

### Local Build

```bash
# Clone the repository
git clone https://github.com/your-username/fiatlife.git
cd fiatlife

# Build debug APK
./gradlew assembleDebug

# Run tests
./gradlew testDebugUnitTest

# Install on connected device
./gradlew installDebug
```

### CI/CD

The project includes a GitHub Actions workflow that:

1. **On every push/PR**: Builds debug APK, runs unit tests, runs lint
2. **On push to main**: Additionally builds a release APK

Artifacts (APKs, test reports, lint reports) are uploaded as workflow artifacts.

#### Release Signing

For signed release builds, add these secrets to your GitHub repository:

- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_STORE_PASSWORD`

## Setup

1. Open the app and navigate to **Settings**
2. Generate a new Nostr key or import an existing one
3. Enter your personal relay URL (e.g., `wss://relay.example.com`)
4. Enter your Blossom server URL (e.g., `https://blossom.example.com`)
5. Tap **Save & Connect**

The app will authenticate with your relay via NIP-42 and begin syncing data.

## Nostr Data Format

All data is stored as kind `30078` (application-specific) events with encrypted content:

| Data Type | d-tag | Content |
|-----------|-------|---------|
| Salary Config | `fiatlife/salary` | NIP-44 encrypted JSON |
| Bill | `fiatlife/bill/{uuid}` | NIP-44 encrypted JSON |
| Financial Goal | `fiatlife/goal/{uuid}` | NIP-44 encrypted JSON |

## License

[MIT](LICENSE)
