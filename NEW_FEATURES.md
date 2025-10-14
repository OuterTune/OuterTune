# OuterTune New Features Documentation

## Overview

This document describes the four major new features added to OuterTune:

1. **Create Hub** - Central navigation for new features
2. **OuterConnect** - Real-time party music streaming
3. **GemQ** - AI-powered temporary queue generation
4. **GemList** - AI-powered playlist creation

## Features

### 1. Create Hub (`ConnectScreen.kt`)

A new bottom navigation item that serves as a hub for creative features:

- **Location**: Bottom navigation with "Create" label and AddCircle icon
- **Purpose**: Central access point for OuterConnect, GemQ, and GemList
- **UI**: Card-based layout with feature descriptions and navigation

### 2. OuterConnect - Real-Time Party Streaming

**Files**: `OuterConnectScreen.kt`, `PartyScreen.kt`, `PartyViewModel.kt`

**Features**:
- Create or join music parties using 6-digit codes
- Real-time synchronization of playback across all devices
- Host controls (play/pause/skip) with member participation
- Live queue management where members can add songs
- Firebase Realtime Database backend for instant updates

**Technical Details**:
- **Backend**: Firebase Realtime Database + Authentication
- **State Management**: PartyViewModel with Flow-based state
- **Real-time Updates**: Firebase ValueEventListener
- **Security**: Database rules ensure only party members can read/write

**Setup Requirements**:
1. Create Firebase project at https://console.firebase.google.com/
2. Enable Realtime Database and Authentication (Anonymous)
3. Replace `google-services.json` with your project configuration
4. Deploy database security rules from `database.rules.json`

### 3. GemQ - AI-Powered Queue Generation

**Files**: `GemQScreen.kt`, `GeminiViewModel.kt`

**Features**:
- Natural language input for describing desired music mood/style
- Configurable queue length (5-30 songs)
- AI generates temporary queue based on user description
- Integration with existing music playback system
- Fallback handling for API failures

**Technical Details**:
- **AI Backend**: Google Gemini Pro API
- **Input Processing**: Natural language to structured music recommendations
- **Response Format**: JSON with song metadata and confidence scores
- **Error Handling**: Graceful fallbacks with retry options

### 4. GemList - AI-Powered Playlist Creation

**Files**: `GemListScreen.kt`, `GeminiViewModel.kt`

**Features**:
- Create permanent playlists using AI recommendations
- Custom playlist names and descriptions
- Configurable song count (5-50 songs)
- Optional YouTube Music sync (placeholder for future implementation)
- Automatic saving to local database

**Technical Details**:
- **AI Backend**: Google Gemini Pro API
- **Database**: Integration with existing PlaylistsDao
- **Persistence**: Playlists saved locally for offline access
- **Future**: YouTube Music API integration framework prepared

## Setup Instructions

### Prerequisites

1. **Firebase Setup**:
   ```
   1. Go to https://console.firebase.google.com/
   2. Create new project or use existing
   3. Enable Authentication > Sign-in method > Anonymous
   4. Enable Realtime Database > Rules > Import from database.rules.json
   5. Download google-services.json to app/ directory
   ```

2. **Gemini AI Setup**:
   ```
   1. Visit https://aistudio.google.com/app/apikey
   2. Create API key for Gemini Pro
   3. Update ApiConfig.GEMINI_API_KEY in ApiConfig.kt
   ```

### Build Configuration

1. **Enable Firebase** (currently commented out):
   ```kotlin
   // In app/build.gradle.kts, uncomment:
   // id("com.google.gms.google-services")
   ```

2. **API Keys**:
   ```kotlin
   // Update app/src/main/java/com/dd3boh/outertune/ApiConfig.kt
   const val GEMINI_API_KEY = "your_actual_api_key_here"
   ```

## Architecture

### Navigation Flow

```
Home Screen
├── Create (New Tab)
    ├── OuterConnect
    │   ├── Create Party → Party Session
    │   └── Join Party → Party Session
    ├── GemQ → AI Queue Generation
    └── GemList → AI Playlist Creation
```

### Data Flow

```
UI Layer (Compose Screens)
    ↕
ViewModel Layer (State Management)
    ↕
Repository Layer
    ├── Firebase (Party Data)
    ├── Gemini AI (Music Generation)
    └── Local Database (Playlists)
```

### State Management

- **PartyViewModel**: Manages real-time party state and Firebase sync
- **GeminiViewModel**: Handles AI interactions and response processing
- **Reactive UI**: StateFlow-based updates with Compose integration

## Database Schema

### Firebase Realtime Database

```json
{
  "parties": {
    "ABC123": {
      "code": "ABC123",
      "name": "Road Trip Jams",
      "hostId": "user123",
      "members": [...],
      "queue": [...],
      "currentTrack": {...},
      "isPlaying": true,
      "position": 45000,
      "createdAt": 1640995200000,
      "lastUpdated": 1640995260000
    }
  }
}
```

### Local Database (SQLite)

Extends existing playlist system with AI-generated metadata.

## Security

### Firebase Rules

- **Read Access**: Only party members can read party data
- **Write Access**: Host controls playback, all members can add to queue
- **Authentication**: Anonymous authentication required
- **Validation**: Strict data validation rules prevent malicious updates

### API Security

- **Gemini AI**: API key should be secured (consider server-side proxy for production)
- **Rate Limiting**: Implement client-side request throttling
- **Error Handling**: Sanitize error messages to prevent information leakage

## Testing

### Unit Tests

- PartyViewModel state management
- GeminiViewModel AI response parsing
- Database operations and syncing

### Integration Tests

- Firebase real-time synchronization
- Gemini AI API integration
- Navigation flow between screens

### Manual Testing

1. **OuterConnect**:
   - Create party and verify code generation
   - Join party with code and test synchronization
   - Test host controls and member queue additions

2. **GemQ**:
   - Test various prompt inputs and song counts
   - Verify AI response parsing and error handling
   - Test queue integration with music player

3. **GemList**:
   - Create playlists with different descriptions
   - Verify database persistence
   - Test error scenarios and recovery

## Future Enhancements

### Short Term
1. **YouTube Music Integration**: Sync GemList playlists with YouTube Music
2. **Enhanced AI Prompts**: Support for more sophisticated music queries
3. **Party Chat**: Real-time messaging within party sessions
4. **Music Discovery**: AI-powered music recommendations based on listening history

### Long Term
1. **Social Features**: Friend lists, party history, shared playlists
2. **Advanced AI**: Mood detection, context-aware recommendations
3. **Multi-Platform**: Web and desktop clients for party participation
4. **Analytics**: Music taste insights and listening statistics

## Troubleshooting

### Common Issues

1. **Firebase Connection**:
   ```
   - Verify google-services.json is in app/ directory
   - Check Firebase project configuration
   - Ensure Realtime Database rules are deployed
   ```

2. **Gemini AI Errors**:
   ```
   - Verify API key is correct and active
   - Check quota limits and billing status
   - Test with simple prompts first
   ```

3. **Build Issues**:
   ```
   - Clean and rebuild project
   - Verify all dependencies are properly synced
   - Check for version conflicts in libs.versions.toml
   ```

### Debug Mode

Enable detailed logging by setting log levels in respective ViewModels:

```kotlin
// In PartyViewModel and GeminiViewModel
private val TAG = "ViewModel"
Log.d(TAG, "Debug message")
```

## Contributing

When contributing to these features:

1. **Code Style**: Follow existing Kotlin and Compose patterns
2. **Testing**: Add unit tests for new functionality
3. **Documentation**: Update this README for significant changes
4. **Security**: Review Firebase rules and API key handling
5. **Performance**: Monitor real-time sync performance and AI response times

## Support

For issues or questions:

1. Check existing GitHub issues
2. Review Firebase and Gemini AI documentation
3. Test with mock data to isolate problems
4. Submit detailed bug reports with logs and reproduction steps