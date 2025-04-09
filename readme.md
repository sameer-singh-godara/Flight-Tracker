# Flight Tracker

## Overview
This is an Android application developed in Kotlin for the "Mobile Computing - Winter 2024" course, Assignment 2. The app tracks flight journeys and provides average flight time recommendations, including delays, as per the assignment requirements. This README documents the current implementation status and how I have addressed the assignment questions.

## GitHub Repository
- **Link**: [https://github.com/sameer-singh-godara/Flight-Tracker](https://github.com/sameer-singh-godara/Flight-Tracker)
- The repository is private; please ensure access is granted to the instructor via GitHub for evaluation.

## Implementation Details

### How I Solved the Assignment

#### Question 1: Tracking the Journey of a Close Friend
- **Utilizing the API and Downloading Data (5 marks)**:
  - Implemented `AviationStackApi.kt` to interface with the AviationStack API, fetching flight data using multiple API keys for redundancy. The `getFlightData` function supports queries by flight number, status, and airport codes. The user inputs the flight number, and the app fetches the corresponding data from the API.
- **Creation of the UI (5 marks)**:
  - Designed `activity_main.xml` with a `ConstraintLayout` and `LinearLayout` containing an input field for flight numbers, buttons for tracking, stats, and history, a progress bar, error text, and a scrollable details section with `flightNumberText`, `mainContentText`, and `lastUpdatedText`.
- **Parsing of JSON Files (5 marks)**:
  - Defined `FlightResponse.kt` and nested data classes (`FlightData`, `Departure`, `Arrival`, etc.) to parse JSON responses from the API into structured Kotlin objects using GsonConverterFactory.
- **Proper Output and Running Code (10 marks)**:
  - In `MainActivity.kt`, the `updateUI` function displays flight details (e.g., airline, departure/arrival times, live tracking) in `mainContentText`. The app runs with minute-by-minute refresh using a `Handler`, and the code handles API responses successfully.
- **Validation of User Input, Proper Error Messages, and Running App (5 marks)**:
  - Added input validation in `trackFlight` to check for empty flight numbers, displaying errors via `showError`. Multiple API key attempts ensure robustness, with error messages like "All API keys exhausted" shown if all fail.

#### Question 2: Recommending Average Time Taken
- **Creation of Database and Schema (10 marks)**:
  - Created `FlightStatsDatabase.kt` using Room to manage a `flight_stats` table, with `FlightStatsEntity.kt` defining columns for `id`, `route`, `durationMinutes`, `delayMinutes`, and `timestamp`. A migration from version 1 to 2 adds the `delayMinutes` column.
- **Insertion of Data into the Database and Sending Queries (10 marks)**:
  - Implemented `saveFlightStats` in `MainActivity.kt` to insert flight data into the database. `FlightStatsDao.kt` includes `insert` and `getRouteHistory` queries to store and retrieve data.
- **Identification of Cases Where Calculation is Necessary, and Computing It (10 marks)**:
  - Added `calculateFlightDuration` and `calculateRouteStats` in `MainActivity.kt` to compute flight durations and average delays based on scheduled times and delays from `FlightData`. The average duration is calculated by first taking the input flight number from the user, then the API fetches its route, and another call is made to find other flights running on that route. `updateStatsUI` displays these averages.
- **Creation of Background Jobs (10 marks)**:
  - Implemented `RouteStatsWorker.kt` using WorkManager to run a periodic job (daily) that fetches three flights per day for a route and saves them to the database. `scheduleRouteStatsJob` in `MainActivity.kt` sets up the job with constraints.
- **Correct Output (10 marks)**:
  - The `showHistory` function retrieves and displays route history with average durations, while `updateStatsUI` shows average time and delay of flights running for the particular route. The output is formatted in `mainContentText` and updates correctly.

### Files
- **Kotlin**: 
  - `MainActivity.kt`: Handles UI updates, API calls, database operations, and background job scheduling.
  - `AviationStackApi.kt`: Defines the API interface with Retrofit.
  - `FlightData.kt`: Data classes for JSON parsing.
  - `FlightTrackerApp.kt`: Initializes the Room database.
  - `FlightStatsDao.kt`: DAO for database queries.
  - `FlightStatsDatabase.kt`: Room database definition.
  - `FlightStatsEntity.kt`: Entity for the `flight_stats` table.
  - `RouteStatsWorker.kt`: Background worker for route stats collection.
- **XML**: `activity_main.xml` (UI layout).

### Future Work
- Enhance API error handling and optimize refresh intervals.
- Add unit tests for calculations and database operations.
- Improve UI responsiveness and add more detailed stats visualization.

### Submission
- **Files Uploaded**: Kotlin sources, XML layouts, and this README are committed to the GitHub repository.
- **Submission Method**: Uploaded to both Google Classroom and the private GitHub repository [https://github.com/sameer-singh-godara/Flight-Tracker](https://github.com/sameer-singh-godara/Flight-Tracker).