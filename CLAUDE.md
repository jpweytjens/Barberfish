# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Karoo Extension template project for creating Android apps that integrate with Hammerhead Karoo cycling computers. The project uses Kotlin with Jetpack Compose and follows Android app development patterns.

## Key Architecture

- **Extension Entry Point**: `TemplateExtension.kt` - Extends `KarooExtension` to implement cycling computer integration
- **UI Layer**: Jetpack Compose with Material3 theming in `MainScreen.kt`
- **Configuration**: Extension metadata in `extension_info.xml` defines extension properties like ID, display name, and capabilities
- **Package Structure**: Uses `io.hammerhead.karooexttemplate` namespace (template values to be replaced)

## Build Commands

```bash
# Build the project
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug build to connected device
./gradlew installDebug

# Clean build artifacts
./gradlew clean
```

## Dependencies

- **Karoo Extension SDK**: `io.hammerhead:karoo-ext:1.1.3` - Core extension functionality
- **Jetpack Compose**: Full UI toolkit with Material3 theming
- **AndroidX**: Core Android libraries including lifecycle and activity components

## Template Customization

When creating a new extension from this template:

1. Update namespace in `build.gradle.kts` and package declarations
2. Replace template values in `strings.xml` (app_name, extension_name)
3. Update extension ID and metadata in `extension_info.xml`
4. Implement extension logic in `TemplateExtension.kt`
5. Customize UI in `MainScreen.kt` and related screens

## Key Files

- `TemplateExtension.kt` - Main extension implementation
- `MainActivity.kt` - Android activity entry point
- `MainScreen.kt` - Primary UI screen
- `extension_info.xml` - Extension metadata configuration
- `strings.xml` - Localized strings and extension display names

## Barberfish Extension Implementation Plan

### Project Goal
Create "Barberfish" - a Hammerhead Karoo extension for randonneuring cyclists, named after the fish that lives in symbiosis with Hammerhead sharks.

### Features to Implement

#### 1. Average Speed Datafields
- **Excluding Paused Time**: Color-coded (red/green) based on configurable limits
- **Including Paused Time**: Color-coded (red/green) based on configurable limits
- **Default Limits**: Paris Club Audax minimum and maximum speeds
- **Configuration**: User-configurable speed thresholds

#### 2. Time-based Datafields
- **ETA (Estimated Time of Arrival)**: Custom ddHddMdds formatting
- **Paused Time**: Custom ddHddMdds formatting
- **Time Format**: ddHddMdds where d = digit (e.g., 12H34M56S)

#### 3. Customizable Column Layout
- **Columns**: 1-4 configurable columns
- **Content**: Any default Karoo datafields with customization options
- **UI**: Match Hammerhead Karoo styling standards

#### 4. Configuration System
- Speed limit thresholds for color coding
- Column layout customization
- Datafield selection and formatting options

### Karoo Extension Architecture (Research Complete)

#### Core Architecture
- **Extension Base Class**: Extensions extend `KarooExtension` with name and version
- **Process Isolation**: Extensions run in separate processes for stability
- **System Service**: Use `KarooSystemService` to connect/disconnect and interact with Karoo
- **Data Types**: Extensions define custom data types with unique IDs, display names, and icons

#### Key Components
1. **KarooExtension**: Main extension class with lifecycle methods
2. **KarooSystemService**: System interaction service for ride data access
3. **Data Types**: Custom datafield definitions with visual representations
4. **RemoteViews**: Used for cross-process UI rendering

#### Datafield Implementation Patterns
- **Color Coding**: Use conditional logic to change colors based on values (red/yellow/green)
- **Custom Calculations**: Access ride data for speed, time, distance calculations
- **Glance Framework**: Used for UI rendering with composables
- **Data Alignment**: Support for left, center, right positioning

#### Configuration & Manifest
- Register extension in `AndroidManifest.xml` with intent filters
- Define extension metadata in XML files
- Configure extension capabilities and permissions

#### Sample Implementation Patterns
- Color-coded speed indicators (red < 1, yellow < 5, green >= 5)
- Custom FIT file data writing
- Bluetooth device integration
- HTTP request capabilities
- Custom audio feedback (beep patterns)

### Implementation Phases

#### Phase 1: Foundation (High Priority)
- [x] Research Hammerhead Karoo extension architecture and available APIs
- [x] Update project configuration and branding from template to Barberfish
- [ ] Create core extension structure and base datafield classes

#### Phase 2: Core Datafields (Medium Priority)
- [ ] Implement average speed datafield (excluding paused time) with color coding
- [ ] Implement average speed datafield (including paused time) with color coding
- [ ] Create ddHddMdds time formatting utility
- [ ] Implement ETA datafield with custom formatting
- [ ] Implement paused time datafield with custom formatting
- [ ] Create configuration system for speed limits and color thresholds

#### Phase 3: Advanced Features (Lower Priority)
- [ ] Implement customizable column layout datafield (1-4 columns)
- [ ] Add configuration UI for column layout customization

#### Phase 4: Documentation & Testing
- [ ] Create comprehensive README.md with ETA calculation documentation
- [ ] Test all datafields on Karoo device and optimize performance

### Technical Standards
- Industry standard Kotlin code
- Hammerhead Karoo conventions and standards
- Match default Karoo UI styling
- Use official karoo-ext library (https://github.com/hammerheadnav/karoo-ext)
- Documentation: https://hammerheadnav.github.io/karoo-ext/index.html