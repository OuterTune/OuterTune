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
    
    /**
     * Gemini AI API Key
     * Get your key from: https://aistudio.google.com/app/apikey
     */
    const val GEMINI_API_KEY = "AIzaSyDmJU1uoF-4ZH2_AsK_6UfkwBYioh5gJog"
    
    /**
     * Firebase configuration
     * These will be automatically populated from google-services.json
     */
    object Firebase {
        // Firebase project settings are configured via google-services.json
        // Enable Authentication (Anonymous) and Realtime Database in Firebase Console
    }
}