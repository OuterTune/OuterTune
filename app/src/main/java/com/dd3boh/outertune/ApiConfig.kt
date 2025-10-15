package com.dd3boh.outertune

/**
 * Configuration for OuterTune API keys and settings.
 * 
 * IMPORTANT: Replace the placeholder values with your actual API keys before building.
 * 
 * To get your API keys:
 * 1. Gemini AI: Visit https://aistudio.google.com/app/apikey
 * 2. Firebase: Create a project at https://console.firebase.google.com/
 */
object ApiConfig {
    
    // Gemini API key is now user-configurable via Settings (DataStore)
    // Navigate to Settings > Experimental > Gemini API to set your key.
    
    /**
     * Firebase configuration
     * These will be automatically populated from google-services.json
     */
    object Firebase {
        // Firebase project settings are configured via google-services.json
        // Enable Authentication (Anonymous) and Realtime Database in Firebase Console
    }
}