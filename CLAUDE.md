# CLAUDE.md

This file provides guidance to Claude Code when working with this Karoo Extension repository.

## Project Overview

**Barberfish** - A Hammerhead Karoo extension for randonneuring cyclists, providing time and speed data fields with customizable thresholds and formatting.

## Architecture & Key Files

### Core Extension Files
- `BarberfishExtension.kt` - Main extension class extending `KarooExtension`
- `extension_info.xml` - Extension metadata (ID, name, data types)
- `AndroidManifest.xml` - Extension service registration

### Data Field Implementation Pattern
Each data field requires:
1. **DataType class** (e.g., `AverageSpeedDataType.kt`) - Extends `DataTypeImpl`
   - `startStream()` - Provides data values
   - `startView()` - Optional custom UI (use `UpdateNumericConfig` for standard display)
2. **Entry in extension_info.xml** - Defines display name, icon, type ID
3. **Registration in BarberfishExtension.types** - Makes data type available

### UI Files (Optional)
- `MainActivity.kt` - Companion app (testing/configuration)
- `MainScreen.kt` - Jetpack Compose UI

## Technology Stack

- **Language**: Kotlin
- **SDK**: `io.hammerhead:karoo-ext:1.1.5`
- **UI**: Jetpack Compose + Material3 (for companion app)
- **Build**: Gradle with Android plugin

## Key APIs & Patterns

### Extension Lifecycle
```kotlin
class BarberfishExtension : KarooExtension("barberfish", "1.0") {
    override val types = listOf(/* your data types */)
    
    override fun onCreate() {
        karooSystem.connect { /* handle connection */ }
    }
}
```

### Data Field Implementation
```kotlin
class MyDataType(extension: String) : DataTypeImpl(extension, "my-type-id") {
    override fun startStream(emitter: Emitter<StreamState>) {
        // Get data from karooSystem.streamDataFlow(DataType.Type.X)
        // Transform and emit via emitter.onNext()
    }
    
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // For numeric display: emitter.onNext(UpdateNumericConfig(...))
        // For custom UI: Use RemoteViews/Glance
    }
}
```

### Accessing Karoo Data
```kotlin
karooSystem.streamDataFlow(DataType.Type.SPEED)        // Current speed
karooSystem.streamDataFlow(DataType.Type.DISTANCE)     // Total distance  
karooSystem.streamDataFlow(DataType.Type.ELAPSED_TIME) // Ride time
karooSystem.consumerFlow<RideState>()                  // Ride state (recording/paused)
```

## Build Commands

```bash
./gradlew assembleDebug    # Build debug APK
./gradlew installDebug     # Install to connected device
./gradlew clean           # Clean build artifacts
```

## Documentation & Resources

- **API Docs**: https://hammerheadnav.github.io/karoo-ext/index.html
- **Sample Code**: Official karoo-ext repo sample app
- **Template**: https://github.com/hammerheadnav/karoo-ext-template
- **Community**: https://support.hammerhead.io/hc/en-us/community/topics/31298804001435

## Implementation Plan

### Core Data Fields to Implement

1. **Average Speed (Excluding Paused)**
   - Data: `DISTANCE / (ELAPSED_TIME - PAUSED_TIME)`
   - Colors: Red/green based on configurable min/max speeds
   - Default: Paris Club Audax limits

2. **Average Speed (Including Paused)**  
   - Data: `DISTANCE / TOTAL_TIME`
   - Colors: Red/green based on configurable thresholds

3. **ETA (Estimated Time of Arrival)**
   - Calculation: Based on route distance and current average speed
   - Format: Custom ddHddMdds (e.g., 12H34M56S)

4. **Paused Time**
   - Data: Track cumulative pause duration  
   - Format: Custom ddHddMdds

### Implementation Priority
1. Foundation: Extension structure + basic numeric data fields
2. Color coding: Threshold-based red/green display  
3. Custom formatting: ddHddMdds time format
4. Configuration: User-settable speed thresholds

## Best Practices

- **Data Streaming**: Use `combine()` to merge multiple data sources
- **Color Coding**: Use `UpdateNumericConfig(textColor = color)` 
- **Performance**: Minimize calculations in `startStream()`
- **Lifecycle**: Always cancel jobs in `emitter.setCancellable {}`
- **UI Consistency**: Match Karoo's default styling
- **Error Handling**: Gracefully handle missing/invalid data