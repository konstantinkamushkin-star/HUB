package com.divehub.app.ui.achievements

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.StickyNote2
// Extensions from material-icons-extended live in this package; each must be in scope for
// [Icons.Filled.…] to resolve.
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * [com.divehub.app.services.AchievementDefinition.iconName] matches iOS SF Symbol names; iOS resolves them in
 * `DiveHub/Views/Common/DiveHubLogoMark.swift` (`DiveHubSystemIcon`). On Android, each name maps to the
 * closest [Icons.Filled] glyph.
 */
fun achievementImageVectorForSfName(sf: String): ImageVector = when (sf) {
    "drop.fill" -> Icons.Filled.ScubaDiving
    "5.circle.fill" -> Icons.Filled.Star
    "10.circle.fill" -> Icons.Filled.Star
    "25.square.fill" -> Icons.Filled.EmojiEvents
    "50.circle.fill" -> Icons.Filled.EmojiEvents
    "100.circle.fill" -> Icons.Filled.EmojiEvents
    "book.closed.fill" -> Icons.Filled.Book
    "arrow.down.to.line" -> Icons.Filled.South
    "arrow.down" -> Icons.Filled.ArrowDownward
    "arrow.down.circle" -> Icons.Filled.ExpandMore
    "arrow.down.circle.fill" -> Icons.Filled.ExpandMore
    "diamond.fill" -> Icons.Filled.Diamond
    "triangle.fill" -> Icons.Filled.ChangeHistory
    "timer" -> Icons.Filled.Schedule
    "hourglass" -> Icons.Filled.HourglassTop
    "lungs.fill" -> Icons.Filled.Air
    "clock.badge" -> Icons.Filled.Schedule
    "clock.arrow.circlepath" -> Icons.Filled.Cached
    "clock.badge.checkmark" -> Icons.Filled.CheckCircle
    "mappin.and.ellipse" -> Icons.Filled.LocationOn
    "map" -> Icons.Filled.Map
    "globe" -> Icons.Filled.Public
    "globe.europe.africa.fill" -> Icons.Filled.Public
    "building.2" -> Icons.Filled.Business
    "building.2.crop.circle" -> Icons.Filled.Business
    "house.and.flag" -> Icons.Filled.Home
    "photo" -> Icons.Filled.Image
    "photo.on.rectangle" -> Icons.Filled.PhotoLibrary
    "camera.fill" -> Icons.Filled.PhotoLibrary
    "video.fill" -> Icons.Filled.Videocam
    "square.and.arrow.up" -> Icons.Filled.Upload
    "antenna.radiowaves.left.and.right" -> Icons.Filled.Podcasts
    "fish" -> Icons.Filled.SetMeal
    "leaf" -> Icons.Filled.Grass
    "book" -> Icons.Filled.Book
    "aqi.medium" -> Icons.Filled.Science
    "eye" -> Icons.Filled.Visibility
    "snowflake" -> Icons.Filled.AcUnit
    "thermometer.snowflake" -> Icons.Filled.AcUnit
    "sun.max.fill" -> Icons.Filled.WbSunny
    "thermometer.low" -> Icons.Filled.Opacity
    "at" -> Icons.Filled.AlternateEmail
    "checkmark.seal" -> Icons.Filled.Verified
    "text.alignleft" -> Icons.AutoMirrored.Filled.FormatAlignLeft
    "calendar" -> Icons.Filled.CalendarMonth
    "calendar.badge.clock" -> Icons.Filled.Today
    "calendar.badge.exclamationmark" -> Icons.AutoMirrored.Filled.EventNote
    "arrow.triangle.2.circlepath" -> Icons.Filled.Recycling
    "moon.stars.fill" -> Icons.Filled.DarkMode
    "sailboat" -> Icons.Filled.Sailing
    "wind" -> Icons.Filled.Air
    "person.2.fill" -> Icons.Filled.Groups
    "hare.fill" -> Icons.Filled.Speed
    "doc.richtext" -> Icons.Filled.Description
    "note.text" -> Icons.AutoMirrored.Filled.StickyNote2
    "hand.wave" -> Icons.Filled.Handshake
    "person.3.fill" -> Icons.Filled.Groups
    "figure.pool.swim" -> Icons.Filled.Pool
    else -> Icons.Filled.Star
}

/** iOS [DiveHubSystemIcon]: brand mark for `water.waves` and `divehub.logo` only. */
fun achievementUsesBrandMark(sf: String): Boolean =
    sf == "water.waves" || sf == "divehub.logo"
