with open("app/src/main/java/com/quantummpv/app/preferences/GesturePreferences.kt", "r") as f:
    content = f.read()

content = content.replace("val doubleTapToSeekDuration", 'val hapticFeedbackEnabled = preferenceStore.getBoolean("haptic_feedback_enabled", true)\n  val nestedTabSwipesEnabled = preferenceStore.getBoolean("nested_tab_swipes_enabled", true)\n  val doubleTapToSeekDuration')

with open("app/src/main/java/com/quantummpv/app/preferences/GesturePreferences.kt", "w") as f:
    f.write(content)
