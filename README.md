# zMap - Accessible World-Map-Explorer (Android Version)

zMap is an intuitive, accessible, and user-friendly tool designed to explore and learn about the map inclusively. The goal of the project is to ensure that everyone can access the world map in a playful and educational manner. The app is built using Kotlin, XML, and Android Studio, and incorporates various APIs to provide a comprehensive map experience. It is still in development, but the basic functionality is already available.

## Features

- **Gesture-Based Navigation**: Intuitive controls for panning, zooming, and exploring maps using touch gestures.
- **Search and Navigation**: Provide directions and information about searched locations.
- **Elevation Data**: Provide real-time altitude information for any point on the map.
- **Location Updates**: Pin point your current location to start navigation from nearby places.
- **Customizable Map Layouts**: Different viewing modes for enhanced accessibility.

## Tech Stack

- **Android (Kotlin, XMLe)**
- **Kotlin**: Primary language for Android development.

- **XML**: Used for designing UI layouts.

- **Retrofit**: For handling API calls efficiently.

- **MVVM (Model-View-ViewModel)**: Implements a structured and maintainable architecture.

- **Hilt**: Dependency injection framework for better modularization.

- **Sandwich**: Simplifies API response handling with a clean and readable approach.

- **Kotlin Flows & Coroutines**: For asynchronous programming and reactive data handling.

Installation
- **Map Rendering**: OpenStreetMap SDK
- **APIs Used**:
    - [Nominatim](https://nominatim.org/) for geolocation search
    - [Overpass API](https://overpass-api.de/) for navigation queries
    - [Open-Elevation](https://open-elevation.com/) for altitude data

## Installation

1. Clone the repository:
   ```sh
   git clone https://github.com/yourusername/zMap.git
   cd zMap
   ```
2. Open the project in Android Studio.
3. Build and run the app on an emulator or a physical device.

## How to Use

1. **Exploring the Map**: Use touch gestures to navigate and zoom in/out. Tap the Locate Me button to pinpoint your current position. Toggle between Political and Geographical map views for different perspectives.

![Feature Demo](./record/record_1.gif)


2. **Location Insights**: Tap on any point on the map to retrieve its real-time altitude information. Check the distance from your current or selected location to the nearest region border for better geographical context.

![Feature Demo](./record/record_2.gif)


3. **Search for Locations**: Enter a place name to find relevant results with easy navigation options and get directions to the selected location.

![Feature Demo](./record/record_3.gif)


## Roadmap & Future Improvements

- ✅ Implement basic gesture-based map exploration.
- ✅ Integrate search and navigation APIs.
- 🔲 Add text-to-speech support for better accessibility.
- 🔲 Optimize API calls for performance improvements.



