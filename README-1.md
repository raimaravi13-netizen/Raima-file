# AI-Powered Disaster Management System

A single-file Java desktop application built with **Java 17 and Swing**. It provides location-based weather monitoring, disaster-risk assessment, safety guidance, emergency contacts, a disaster simulation, and nearby emergency-service routing.

## Features

- **Welcome and login:** Application introduction and a simple username/mobile-number login.
- **Current location:** Requests browser geolocation permission and sends the coordinates to the Java application.
- **Live weather and forecast:** Retrieves current weather and forecast information for the detected coordinates.
- **Disaster scan and risk assessment:** Uses the retrieved wind and rainfall readings to identify cyclone/flood conditions and calculate a basic risk level.
- **Safety playbook:** Displays before-, during-, and after-disaster precautions.
- **AI assistant:** Uses built-in safety responses, can optionally call the Anthropic API when an API key is provided, and can fall back to Wikipedia summaries.
- **Emergency contacts:** Displays emergency numbers and provides call/dialer, QR, and SOS-message options.
- **Digital twin and simulation:** Provides an animated sample-city disaster simulation and evacuation controls.
- **Emergency services and smart routing:** Finds nearby hospitals, police stations, fire stations, shelters, and rescue services using OpenStreetMap data. It can request driving routes through OSRM and provides an approximate fallback if routing is unavailable.

## Technology Stack

- Java 17
- Java Swing and AWT
- Java `HttpClient`
- Java built-in lightweight HTTP server (`com.sun.net.httpserver`)
- Browser Geolocation API
- OpenStreetMap Nominatim (reverse geocoding)
- Open-Meteo (weather and forecast)
- OpenStreetMap Overpass API (nearby emergency services)
- OSRM public routing service (driving routes)
- Anthropic API (optional AI assistant integration)
- Wikipedia API (assistant fallback)

## Project Structure

This project is currently implemented as a **single Java source file**:

```text
Disaster-Management-System/
├── DisasterManagementSystem.java
└── README.md
```

The uploaded source file is named `DOC-20261112-WA0003.txt`. Save or rename it as `DisasterManagementSystem.java` before compiling, because the public Java class is named `DisasterManagementSystem`.

## Requirements

- JDK 17 or later
- Internet connection for live geocoding, weather, nearby-service lookup, and online routing
- A modern browser such as Chrome or Edge for location permission
- Location permission enabled in the browser when prompted

No third-party Java libraries are required by the current source code.

## How to Run

Open Command Prompt or a terminal in the folder containing `DisasterManagementSystem.java`.

### 1. Check Java

```bash
java -version
javac -version
```

Make sure the installed JDK is version 17 or later.

### 2. Compile

```bash
javac --add-modules jdk.httpserver DisasterManagementSystem.java
```

### 3. Run

```bash
java --add-modules jdk.httpserver DisasterManagementSystem
```

The Swing application window should open.

## Using the Application

1. Select **Get Started**.
2. Enter a username and a 10-digit mobile number.
3. Open the Location & Weather page.
4. Select **GPS / Current Location**.
5. Allow location access in the browser.
6. Return to the application and wait for the location and weather information to load.
7. Run the AI disaster scan to view the application's rule-based status and risk assessment.
8. Explore the safety playbook, assistant, emergency contacts, digital twin, and emergency-response pages.
9. On the emergency-response page, load nearby services, select a destination, and request a route.

The application starts a local HTTP service on `http://localhost:8765` to receive browser location data. If that port is already in use, close the other running instance and start the application again.

## Optional Anthropic API Key

The AI assistant can use an Anthropic API key. You can enter a key through the assistant page, or set the `ANTHROPIC_API_KEY` environment variable before launching the application.

Example for Windows Command Prompt:

```cmd
set ANTHROPIC_API_KEY=your_api_key_here
java --add-modules jdk.httpserver DisasterManagementSystem
```

Keep API keys private. Do not commit real API keys, passwords, or personal information to GitHub. If a key was entered inside the running application, it is held in application memory for that run.

If the Anthropic API is not configured or cannot be reached, the assistant attempts a Wikipedia summary and then uses built-in safety responses.

## Database and MySQL Status

**The current uploaded source code does not contain MySQL or JDBC integration.**

The source file has no MySQL Connector/J dependency, JDBC imports, database connection code, SQL statements, or database table operations. It also does not contain a Maven `pom.xml` or Maven configuration.

Therefore, this version runs without MySQL and does not store application data in a MySQL database. Do not describe MySQL/JDBC as an implemented feature unless database integration is added to the project.

## Important Notes and Limitations

- The disaster classification is a basic rule-based demonstration using weather values; it is not an official disaster-warning service or a validated predictive AI model.
- The digital-twin city is a sample simulation, not a live digital replica of a real city.
- Nearby service information depends on public OpenStreetMap/Overpass data. Availability and completeness can vary.
- The built-in offline emergency-service backup contains approximate sample locations around Tiruchirappalli only. It should not be treated as verified emergency-service data.
- OSRM routing depends on public routing-server availability. The fallback is a straight-line distance estimate, not a road route.
- The application does not receive live road-closure information. Always verify travel conditions and follow official emergency instructions.
- External APIs require an internet connection and may have their own availability limits and usage policies.
- In a real emergency in India, contact the appropriate official emergency service (112 for the national emergency number).

## Future Improvements

- Add MySQL database storage using JDBC and MySQL Connector/J if persistent user, scan, or emergency records are required.
- Add validated disaster datasets and a properly trained prediction model.
- Add secure API-key management rather than entering keys directly into the desktop application.
- Add verified emergency-service data and official road-closure information.

## License

Add a license file if you intend to publish this project for reuse. Until then, no reuse license is specified.
