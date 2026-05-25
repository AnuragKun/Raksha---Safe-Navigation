package com.arlabs.raksha.features.onboarding.Components

import androidx.annotation.DrawableRes
import com.arlabs.raksha.R

enum class PageType {
    FEATURE_TOUR,   // Standard image + text page
    SETUP_CONTACTS, // Prompt to add emergency contacts
    SETUP_PERMISSIONS // Request location & notification permissions
}

data class Page (
    val title: String,
    val description: String,
    @DrawableRes val imageRes: Int,
    val emoji: String = "",
    val type: PageType = PageType.FEATURE_TOUR
)

val pages = listOf(
    Page(imageRes = R.drawable.ic_raksha_logo,
        title = "Navigate Smarter",
        description = "Don't just find the fastest route, find the safest one. We guide you through paths vetted for safety by our community."
    ),
    Page(imageRes = R.drawable.ic_raksha_logo,
        title = "Power in Unity",
        description = "Join a network of verified users making travel safer for everyone. Report unsafe spots and help build a trusted map."
    ),
    Page(imageRes = R.drawable.ic_raksha_logo,
        title = "Automatic Guardian",
        description = "Set a safety timer for your journey. If you don't check in on time, we automatically alert your emergency contacts for you."
    ),
    Page(imageRes = R.drawable.ic_raksha_logo,
        title = "Add Emergency Contacts",
        description = "These are the people who'll be notified when you trigger SOS or your safety timer expires. You can add more later in your Profile.",
        emoji = "👥",
        type = PageType.SETUP_CONTACTS
    ),
    Page(imageRes = R.drawable.ic_raksha_logo,
        title = "Enable Permissions",
        description = "Raksha needs these permissions to keep you safe: precise location for routing, notifications for alerts, SMS to contact help, and contacts to pick your emergency people.",
        emoji = "🔐",
        type = PageType.SETUP_PERMISSIONS
    )
)