# MPV_RX Custom Commands & User Data Integration Guide

This guide details the custom `user-data` properties, OSD messaging, panel triggers, soft-keyboard control, dynamic button script-messages, and Android system telemetry available in **MpvRx**.

Developers and power users can use these custom properties and messages within their `mpv` Lua/JS scripts to build customized UI overlays, launch native Android player panels, or coordinate custom interactions.

---

## 1. Custom Player Action Properties (`user-data/mpvrx/*`)

MpvRx listens for updates to the observed properties under `user-data/mpvrx/*`. When a script sets one of these properties, MpvRx captures the value, performs the action, and clears the property to prepare for the next command.

### 📑 Summary of Supported Actions

| Property Subpath | Expected Value Format | Action Performed |
| :--- | :--- | :--- |
| [`show_text`](#1-show_text) | `String` | Displays a premium text overlay / OSD message on the player UI. |
| [`toggle_ui`](#2-toggle_ui) | `"show"` \| `"hide"` \| `"toggle"` | Controls visibility of the player UI controls. |
| [`show_panel`](#3-show_panel) | Panel Identifier (`String`) | Opens a native MpvRx bottom sheet or settings panel. |
| [`seek_to_with_text`](#4-seek_to_with_text) | `"<seconds>\|<message>"` | Absolute seek to timestamp while showing custom overlay text. |
| [`seek_by_with_text`](#5-seek_by_with_text) | `"<seconds>\|<message>"` | Relative seek by delta (seconds) showing custom overlay text. |
| [`seek_to`](#6-seek_to) | `String` (representing Integer seconds) | Absolute seek without any text overlay. |
| [`seek_by`](#7-seek_by) | `String` (representing Integer seconds) | Relative seek without any text overlay. |
| [`software_keyboard`](#8-software_keyboard) | `"show"` \| `"hide"` \| `"toggle"` | Forces visibility state of the system software keyboard. |

---

### Detailed Properties & Examples

#### 1. `show_text`
Displays a text string directly on the player screen via MpvRx's native UI overlay.
*   **Property:** `user-data/mpvrx/show_text`
*   **Lua Example:**
    ```lua
    mp.set_property("user-data/mpvrx/show_text", "Anime4K Shaders Activated!")
    ```
*   **JS Example:**
    ```javascript
    mp.set_property("user-data/mpvrx/show_text", "Anime4K Shaders Activated!");
    ```

#### 2. `toggle_ui`
Controls the visibility of the primary video controls overlay.
*   **Property:** `user-data/mpvrx/toggle_ui`
*   **Accepted Values:**
    *   `"show"`: Forces the control overlay to appear.
    *   `"hide"`: Forces all controls, sheets, and active panels to slide out of view.
    *   `"toggle"`: Toggles the controls overlay visibility state.
*   **Lua Example:**
    ```lua
    mp.set_property("user-data/mpvrx/toggle_ui", "toggle")
    ```

#### 3. `show_panel`
Launches native player panels and bottom sheets directly from scripts.
*   **Property:** `user-data/mpvrx/show_panel`
*   **Accepted Values:**
    *   `"frame_navigation"`: Launches the high-precision frame navigation bottom sheet.
    *   `"subtitle_settings"`: Launches the subtitle style & typography settings panel.
    *   `"subtitle_delay"`: Launches the subtitle delay adjustment panel.
    *   `"audio_delay"`: Launches the audio delay adjustment panel.
    *   `"video_filters"`: Launches the color correction & sharpness filters panel.
    *   `"lua_scripts"`: Launches the Lua scripts configuration panel.
    *   `"hdr_screen_output"`: Launches the Vulkan HDR screen configuration panel.
*   **Lua Example:**
    ```lua
    -- Open Video Filters directly when an option is selected
    mp.set_property("user-data/mpvrx/show_panel", "video_filters")
    ```

#### 4. `seek_to_with_text`
Performs an absolute seek while presenting a clean, modern seek overlay with descriptive text.
*   **Property:** `user-data/mpvrx/seek_to_with_text`
*   **Value Format:** `"<seek_position_in_seconds>|<overlay_message_text>"`
*   **Lua Example:**
    ```lua
    -- Seek to exactly 5 minutes (300 seconds) and notify the user
    mp.set_property("user-data/mpvrx/seek_to_with_text", "300|Skipping Opening Theme")
    ```

#### 5. `seek_by_with_text`
Performs a relative seek by delta seconds and shows a descriptive OSD overlay.
*   **Property:** `user-data/mpvrx/seek_by_with_text`
*   **Value Format:** `"<seek_delta_in_seconds>|<overlay_message_text>"`
*   **Lua Example:**
    ```lua
    -- Seek forward by 85 seconds
    mp.set_property("user-data/mpvrx/seek_by_with_text", "85|Fast Forwarding to Recap")
    
    -- Seek backward by 10 seconds
    mp.set_property("user-data/mpvrx/seek_by_with_text", "-10|Rewinding...")
    ```

#### 6. `seek_to`
Performs a silent absolute seek to the specified timestamp.
*   **Property:** `user-data/mpvrx/seek_to`
*   **Value Format:** `"seconds"` (String)
*   **Lua Example:**
    ```lua
    mp.set_property("user-data/mpvrx/seek_to", "600")
    ```

#### 7. `seek_by`
Performs a silent relative seek by a delta.
*   **Property:** `user-data/mpvrx/seek_by`
*   **Value Format:** `"delta_seconds"` (String)
*   **Lua Example:**
    ```lua
    mp.set_property("user-data/mpvrx/seek_by", "-30")
    ```

#### 8. `software_keyboard`
Forces the Android IME software keyboard to show or hide. Useful for scripts requiring keyboard entry.
*   **Property:** `user-data/mpvrx/software_keyboard`
*   **Accepted Values:**
    *   `"show"`: Forces showing the keyboard.
    *   `"hide"`: Forces hiding the keyboard.
    *   `"toggle"`: Toggles keyboard visibility based on current focus state.
*   **Lua Example:**
    ```lua
    mp.set_property("user-data/mpvrx/software_keyboard", "show")
    ```

---

## 2. Dynamic Custom Buttons API

MpvRx allows users to configure custom buttons through the application preferences (via JSON schema slot configuration). These custom buttons are compiled dynamically into LUA/JS script files during player initialization.

### Event Routing Workflow
```
[User Taps Custom Button in UI]
             │
             ▼
[MpvRx fires Native command("script-message", "call_button_<safe_id>")]
             │
             ▼
[Registered LUA/JS script-message callback executes custom logic]
```

### Script Message Registration
When you create a custom button, MpvRx wraps your action/startup code in a clean, stateful lifecycle. The target script registers the following script-messages:
1.  **Click Action:** `call_button_<safe_id>`
2.  **Long Press Action:** `call_button_long_<safe_id>`

> [!NOTE]
> `<safe_id>` is the custom button's identifier with hyphens (`-`) automatically replaced by underscores (`_`).

#### Lua Custom Button Script Example
```lua
-- MpvRx automatically manages active instance safety via: is_active_instance()
function button_custom_toggle_deband()
    if not is_active_instance() then return end
    
    local current = mp.get_property("deband")
    if current == "yes" then
        mp.set_property("deband", "no")
        mp.set_property("user-data/mpvrx/show_text", "Debanding: Disabled")
    else
        mp.set_property("deband", "yes")
        mp.set_property("user-data/mpvrx/show_text", "Debanding: Enabled")
    end
end

-- Register to the script message called by the MpvRx button tap event
mp.register_script_message('call_button_custom_toggle_deband', button_custom_toggle_deband)
```

---

## 3. Real-Time Android Telemetry (`user-data/android/*`)

MpvRx writes system status information directly into MPV properties. Scripts can read or observe these properties to update script-rendered OSDs or trigger custom power-saving modes.

*   `user-data/android/battery-level` (Integer, `0` - `100`): Current battery percentage level.
*   `user-data/android/battery-charging` (Boolean, `true`/`false`): Whether the device is actively charging.
*   `user-data/android/battery-plugged` (Boolean, `true`/`false`): Whether the device is connected to a power outlet (AC, USB, or wireless).

#### Lua Telemetry Observer Example
```lua
function handle_battery_change(name, level)
    if level == nil then return end
    
    local level_num = tonumber(level)
    if level_num < 15 and not mp.get_property_bool("user-data/android/battery-charging") then
        -- Enable low power mode inside MPV
        mp.set_property("deband", "no")
        mp.set_property("user-data/mpvrx/show_text", "Battery Low! Disabling deband filter to save power.")
    end
end

-- Observe battery level changes
mp.observe_property("user-data/android/battery-level", "native", handle_battery_change)
```
