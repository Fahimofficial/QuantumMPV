#!/usr/bin/env python3
"""Regression checks for selected, architecture-adapted mpvRx 2.7.x ports."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]

cmake = (root / "app/src/main/cpp/CMakeLists.txt").read_text()
assert '"-Wl,-z,max-page-size=16384"' in cmake
assert "$<$<CONFIG:Release>:-Wl,-z,max-page-size=16384>" not in cmake

servers = (root / "app/src/main/java/com/quantummpv/app/ui/preferences/MediaServersPreferencesScreen.kt").read_text()
assert "if (existingId == null)" in servers
assert "appearancePreferences.showJellyfinTab.set(true)" in servers

network_screen = (root / "app/src/main/java/com/quantummpv/app/ui/browser/networkstreaming/NetworkBrowserScreen.kt").read_text()
network_adapter = (root / "app/src/main/java/com/quantummpv/app/ui/browser/networkstreaming/NetworkPlaylistVideo.kt").read_text()
assert "AddToPlaylistDialog(" in network_screen
assert "onLongClick = {" in network_screen
assert "isPlayableNetworkMedia(includeAudio)" in network_screen
assert "NetworkPlaybackUri.create(connectionId, path)" in network_adapter

mini_player = (root / "app/src/main/java/com/quantummpv/app/ui/browser/components/MiniPlayer.kt").read_text()
assert "progressColor.copy(alpha = 0.22f)" in mini_player
assert "progressColor.copy(alpha = 0.85f)" in mini_player
assert "val edgeWidth = 2.dp.toPx().coerceAtMost(playedWidth)" in mini_player

subtitle_preferences = (root / "app/src/main/java/com/quantummpv/app/preferences/SubtitlesPreferences.kt").read_text()
subtitle_positioning = (root / "app/src/main/java/com/quantummpv/app/ui/player/SubtitlePositioning.kt").read_text()
subtitle_panel = (root / "app/src/main/java/com/quantummpv/app/ui/player/controls/components/panels/SubtitleSettingsMiscellaneousCard.kt").read_text()
assert 'secondary_sub_scale' in subtitle_preferences and 'secondary_sub_pos", -1' in subtitle_preferences
assert "preferredSecondaryPosition >= MIN_SUBTITLE_POSITION" in subtitle_positioning
assert "resolveSecondarySubtitlePosition" in subtitle_panel
assert "player_sheets_sub_force_rtl_title" in subtitle_panel

seekbar = (root / "app/src/main/java/com/quantummpv/app/ui/player/controls/components/Seekbar.kt").read_text()
assert "DeviceFormFactor.isTelevision(LocalContext.current)" in seekbar
assert "Key.DirectionLeft, Key.DirectionRight" in seekbar
assert "animatedPosition.snapTo(finalPosition)" in seekbar
assert "onValueChangeFinished(finalPosition)" in seekbar
assert ".focusProperties { canFocus = false }" in seekbar

print("selected upstream 2.7.x release-port regression checks passed")
