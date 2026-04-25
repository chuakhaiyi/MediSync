# MediSync — AI-Powered Post-Discharge Patient Care

> **MediSync** is an Android application designed to support recently discharged hospital patients in managing their recovery at home. It uses a multi-agent AI architecture to monitor medications, vitals, symptoms, and appointments — automatically triaging risk and alerting care teams when intervention is needed.

---

## 🎬 Pitch Video

> **Watch our product pitch to see MediSync in action.**

### ▶️ [Click here to watch the MediSync Pitch Video](YOUR_GOOGLE_DRIVE_LINK_HERE)

---

## Table of Contents

- [Pitch Video](#-pitch-video)
- [Overview](#overview)
- [Key Features](#key-features)
- [AI Agent Architecture](#ai-agent-architecture)
- [Screens & Navigation](#screens--navigation)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Data Models](#data-models)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Notification System](#notification-system)
- [Risk Levels](#risk-levels)
- [Security Notes](#security-notes)

---

## Overview

MediSync targets high-risk post-discharge patients — specifically those with conditions like Congestive Heart Failure (CHF) — who require continuous monitoring after leaving hospital. The app acts as a virtual care companion, combining a local Room database with an LLM-powered backend to proactively identify deterioration before it becomes an emergency.

The app is designed around a single patient profile (`P001`, seeded at launch) and connects to a configurable LLM API endpoint (Anthropic-compatible or the bundled `ilmu.ai` service) to run clinical reasoning agents.

---

## Key Features

| Feature | Description |
|---|---|
| **AI Chat Nurse** | Conversational AI assistant that answers patient questions, detects reported symptoms, and escalates when needed |
| **Medication Tracking** | Scheduled medication reminders, taken/missed logging, and AI-driven adherence scoring |
| **Vitals Monitoring** | Log weight, blood pressure, blood sugar, pulse, SpO₂, and temperature with automated risk flagging |
| **Symptom Reporting** | Guided symptom submission with AI risk analysis and immediate escalation for CHF red flags |
| **Daily Checklist** | Personalised morning/afternoon/evening care tasks with vital input prompts |
| **Appointment Management** | View, request, reschedule, and receive reminders for follow-up visits |
| **Discharge Note Parser** | Converts raw hospital discharge text into a structured care plan |
| **Risk Trajectory Prediction** | 48-hour risk forecasting based on vitals trends, adherence, and symptom history |
| **Agent Audit Trail** | Full transparency log of every AI decision, tool call, and risk assessment |
| **SOS Escalation** | One-tap emergency escalation with care team notification |
| **Boot-persistent Notifications** | Medication and checklist reminders survive device restarts |

---

## AI Agent Architecture

MediSync uses a **multi-agent orchestration** pattern. The `AgentOrchestrator` is the central coordinator, delegating tasks to specialist agents based on the clinical context.

```
AgentOrchestrator
├── MedicineAgent          — Adherence scoring, missed medication alerts
├── SymptomAgent           — Risk classification of reported symptoms
├── ChatAgent              — Conversational nurse interface
├── ChecklistAgent         — Daily task completion analysis
├── FollowUpAgent          — Appointment tracking and reminders
├── RiskTrajectoryAgent    — 7-day longitudinal risk trend prediction
└── DischargeAgent         — Discharge note interpretation → structured care plan
```

### How Agents Work

Each agent follows the same pattern:

1. Receives a structured prompt with patient context
2. Calls the `AgentToolRegistry` to fetch real data (medications, vitals, logs, etc.) via named tools
3. Sends the enriched prompt to the LLM API via `LlmApiService`
4. Parses the structured JSON response
5. Persists outcomes (EMR proposals, symptom records, audit entries) back to the Room database

All agent decisions are recorded in the `agent_audit_trail` table for clinician review.

### Available Agent Tools

Agents can invoke the following tools via the `AgentToolRegistry`:

- `get_patient_profile` — Fetch patient demographics and risk level
- `get_medications` — Retrieve active medication list
- `get_medication_logs` — Query taken/missed medication history
- `get_vitals` — Fetch recent vital sign readings
- `get_symptom_reports` — Retrieve logged symptom history
- `get_appointments` — List upcoming and past appointments
- `record_symptom` — Persist a new symptom report
- `request_appointment` — Create an appointment request
- `propose_emr_update` — Submit a proposed change to the electronic medical record
- `send_clinician_alert` — Trigger an urgent alert to the care team
- `log_vital` — Record a new vital sign reading

### Morning Check Orchestration

The `runFullMorningCheck()` method runs all agents in parallel each morning:

```
runMedicineAgent() ──┐
runFollowUpAgent() ──┤──► determineOverallRisk() ──► MorningCheckResult
runChecklistAgent()──┤
runRiskTrajectoryAgent()┘
```

The overall risk (`STABLE` / `WARNING` / `CRITICAL`) is surfaced on the Home screen.

---

## Screens & Navigation

The app uses a bottom navigation bar with 5 primary tabs, plus additional screens accessible via navigation:

| Screen | Route | Description |
|---|---|---|
| **Home** | `home` | Dashboard: patient summary, morning check result, quick medication actions |
| **Checklist** | `checklist` | Daily care task list with inline vital recording |
| **Vitals** | `vitals` | Vital sign history and new entry forms |
| **Appointments** | `appointments` | Upcoming visits, care team reminders, appointment requests |
| **AI Chat** | `chat` | Conversational chat with the AI nurse |
| Medications | `medications` | Full medication schedule and history (accessible from Home) |
| Symptoms | `symptoms` | Guided symptom report submission |
| Audit Trail | `audit` | Agent decision log for transparency |

Navigation uses animated transitions (`fadeIn` + `slideInHorizontally`) and preserves state across tab switches.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0.0 |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM with `MediSyncViewModel` as single source of truth |
| **Dependency Injection** | Hilt 2.51.1 |
| **Local Database** | Room 2.6.1 |
| **Networking** | Retrofit 2.11.0 + OkHttp 4.12.0 |
| **Background Work** | WorkManager 2.9.0 (Hilt-integrated) |
| **Serialisation** | Gson 2.11.0 |
| **Image Loading** | Coil 2.6.0 |
| **Permissions** | Accompanist Permissions 0.34.0 |
| **Local Storage** | DataStore Preferences 1.1.1 |
| **Coroutines** | Kotlin Coroutines 1.8.1 |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **Build System** | Gradle with Version Catalog (`libs.versions.toml`) |
| **Annotation Processing** | KSP 2.0.0-1.0.21 |

---

## Project Structure

```
MediSync/
├── app/
│   ├── build.gradle.kts          # App-level Gradle config, SDK versions, dependencies
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/medisyncplus/
│           ├── MainActivity.kt
│           ├── ai/
│           │   ├── AgentOrchestrator.kt        # Central agent coordinator
│           │   ├── AgentOrchestratorHelper.kt  # Shared LLM call + JSON parse utilities
│           │   ├── AgentPrompts.kt             # System prompts for each agent
│           │   ├── AgentToolRegistry.kt        # Tool dispatch + DB read/write
│           │   ├── InputProcessor.kt           # User input pre-processing
│           │   ├── LlmApiService.kt            # Retrofit API client
│           │   ├── ResponseValidator.kt        # LLM response validation
│           │   └── agents/
│           │       ├── ChatAgent.kt
│           │       ├── ChecklistAgent.kt
│           │       ├── DischargeAgent.kt
│           │       ├── FollowUpAgent.kt
│           │       ├── MedicineAgent.kt
│           │       ├── RiskTrajectoryAgent.kt
│           │       └── SymptomAgent.kt
│           ├── data/
│           │   ├── database/
│           │   │   ├── Daos.kt                 # Room DAO interfaces
│           │   │   ├── DatabaseSeeder.kt       # Seeds demo patient data on first launch
│           │   │   └── MediSyncDatabase.kt     # Room database definition
│           │   ├── models/
│           │   │   └── Entities.kt             # All Room entity data classes
│           │   └── repository/
│           │       └── MediSyncRepository.kt   # Single data access layer
│           ├── di/
│           │   └── AppModule.kt                # Hilt module bindings
│           ├── navigation/
│           │   └── Navigation.kt               # NavGraph + bottom bar + global dialogs
│           ├── ui/
│           │   ├── components/
│           │   │   └── Components.kt           # Shared Composable UI components
│           │   ├── screens/
│           │   │   ├── AllScreens.kt           # Symptom, Appointment, Audit, Chat screens
│           │   │   ├── ChecklistScreen.kt
│           │   │   ├── HomeScreen.kt
│           │   │   ├── MedicationScreen.kt
│           │   │   └── VitalsScreen.kt
│           │   └── theme/
│           │       └── Theme.kt                # Colour palette, typography, Material theme
│           ├── viewmodel/
│           │   └── MediSyncViewModel.kt        # All UI state and agent trigger logic
│           └── workers/
│               └── NotificationWorkers.kt      # WorkManager workers + notification channels
├── build.gradle.kts              # Project-level Gradle
├── gradle/
│   ├── libs.versions.toml        # Centralised version catalog
│   └── wrapper/
└── settings.gradle.kts
```

---

## Data Models

All entities are stored in a Room database. Foreign key constraints with `CASCADE` delete ensure data integrity.

| Table | Description |
|---|---|
| `patients` | Core patient demographics, ward, primary condition, risk level |
| `care_team` | Doctors, nurses, and specialists with contact details |
| `medications` | Prescribed medications with schedule, dosage, and critical flags |
| `medication_logs` | Per-dose taken/missed/skipped records with streak tracking |
| `vitals` | Weight, BP, blood sugar, pulse, SpO₂, temperature readings |
| `symptom_reports` | Patient-reported symptoms with AI risk classification |
| `appointments` | Scheduled visits with status, type, and source tracking |
| `checklist_tasks` | Daily care tasks per patient, linked to vital input requirements |
| `hospital_stays` | Inpatient admission history and discharge note storage |
| `care_team_reminders` | Messages from clinicians to the patient |
| `emr_proposals` | AI-proposed electronic medical record updates awaiting review |
| `chat_messages` | Full conversation history between patient and AI nurse |
| `agent_audit_trail` | Immutable log of every agent action, decision, and tool call |

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK 35
- A device or emulator running Android 8.0+ (API 26+)
- An LLM API key (Anthropic-compatible endpoint)

### Build & Run

1. Clone the repository and open the `MediSync` directory in Android Studio.

2. Configure your API key (see [Configuration](#configuration) below).

3. Sync Gradle and run on a device or emulator:

```bash
./gradlew assembleDebug
```

4. On first launch, the `DatabaseSeeder` will automatically populate the database with a demo patient profile (Margaret Chen), medications, appointments, and checklist tasks.

---

## Configuration

API credentials are configured as `buildConfigField` entries in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    // Replace with your own API key and endpoint
    buildConfigField("String", "LLM_API_KEY", "\"your-api-key-here\"")
    buildConfigField("String", "LLM_BASE_URL", "\"https://api.anthropic.com/v1/\"")
    buildConfigField("String", "LLM_MODEL",    "\"claude-sonnet-4-20250514\"")
}
```

The app is pre-configured to use `ilmu.ai` (a Malaysian-hosted, Anthropic-compatible API). To switch to Anthropic directly, update all three fields accordingly.

> **Warning:** Never commit a real API key to version control. Use environment variables or a secrets manager for production builds.

---

## Notification System

MediSync uses four notification channels, all created at app startup:

| Channel ID | Name | Importance | Purpose |
|---|---|---|---|
| `medication_reminders` | Medication Reminders | HIGH | Scheduled dose reminders |
| `checklist_reminders` | Daily Checklist | DEFAULT | Morning/afternoon/evening task nudges |
| `clinical_alerts` | Clinical Alerts | HIGH | Urgent care team escalations |
| `appointment_reminders` | Appointment Reminders | HIGH | Upcoming visit alerts |

WorkManager workers are Hilt-injected (`@HiltWorker`) and survive device reboots via a `BootReceiver` registered in the manifest. Custom appointment reminders can be scheduled to the minute using `scheduleCustomReminder()`.

---

## Risk Levels

The app uses a three-tier risk system throughout:

| Level | Colour | Meaning |
|---|---|---|
| `STABLE` | Green | Patient is within expected parameters |
| `WARNING` | Amber | Elevated concern; care team notification triggered |
| `CRITICAL` | Red | Immediate escalation required; patient instructed to call emergency services |

Risk is assessed independently by each agent and aggregated by the orchestrator. The worst-case level across all agents determines the overall patient risk displayed on the Home screen.

### CHF-Specific Red Flags (auto-escalate to CRITICAL)

- Chest pain
- Severe breathlessness or syncope
- Frothy sputum
- SpO₂ below 90%
- Weight gain greater than 2 kg in 2 days

---

## Security Notes

- `android:usesCleartextTraffic="false"` is enforced — all network traffic must use HTTPS.
- The API key is embedded at build time via `BuildConfig`. For a production release, move this to a server-side proxy so the key is never shipped in the APK.
- ProGuard minification is enabled for release builds (`isMinifyEnabled = true`).
- All patient data is stored locally on-device in a Room database. No patient data is sent to the LLM; only anonymised clinical context and aggregated readings are included in prompts.
- The `emr_proposals` system ensures AI-suggested changes to medical records always pass through a clinician review step before being applied.
