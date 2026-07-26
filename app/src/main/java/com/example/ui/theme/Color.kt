package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Premium Light Theme Colors (Islamic Cream & Emerald/Gold Aesthetic)
val ImmersiveBackground = Color(0xFFFAF9F6)       // Luxurious Warm Alabaster/Cream Canvas
val ImmersiveHeaderBlue = Color(0xFFFFFFFF)       // Pure White for Cards and Top Bar
val ImmersiveNavBlue = Color(0xFFF1F0EA)          // Elegant Light Warm Sand for Bottom Navigation
val ImmersiveCardGradientStart = Color(0xFFFFFDF6) // Golden Warm White
val ImmersiveCardGradientEnd = Color(0xFFFAF5E3)   // Soft Sunset Cream

// High Contrast Accent Colors for Light Theme
val PremiumGold = Color(0xFF9E7815)        // Deeper Premium Gold for outstanding readability on light bg
val LightGold = Color(0xFFC59B27)          // Warm Gold
val DarkGold = Color(0xFF6A4E03)           // Burnished Deep Gold

// Backgrounds and Surfaces
val CreamBackground = ImmersiveBackground
val SlateDarkBackground = ImmersiveBackground

// Text colors for high contrast
val TextPrimary = Color(0xFF0F172A)        // Deep Slate (Dark Charcoal) for titles & headings
val TextSecondary = Color(0xFF475569)      // Medium Slate for subtitles & captions
val TextMuted = Color(0xFF94A3B8)          // Light Slate for placeholders & inactive states

// Helper mappings to keep old imports working
val RoyalBlueLight = Color(0xFF0F172A)
val RoyalBlueDark = Color(0xFF1E293B)
val RoyalBluePrimary = Color(0xFF334155)

val SecondaryBlue = Color(0xFF475569)
val AccentGold = Color(0xFF9E7815)

val GroupColorsList = listOf(
    "#E11D48", // Red Rose
    "#B58920", // Rich Gold (increased contrast for light theme)
    "#16A34A", // Forest Green
    "#D97706", // Deep Amber
    "#7C3AED", // Royal Purple
    "#0891B2", // Cyan Teal
    "#DB2777"  // Deep Pink
)
