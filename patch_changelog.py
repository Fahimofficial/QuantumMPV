with open("CHANGELOG.md", "r") as f:
    content = f.read()

new_changelog = """# Changelog

These notes are written in plain English and focus on changes that matter in everyday use.

## 1.2.0-preview.2

This preview brings the highly anticipated missing features from mpvRx 2.6.0 into QuantumMPV, resolving all compatibility barriers.

### What's New

- **Audiobooks & Servers**: Fully ported the Audiobookshelf integration and Navidrome/Subsonic server playback support into the new QuantumMPV media pipeline.
- **Themes & Wallpapers**: Ported the custom theme customizer and wallpaper selection screens.
- **Advanced Gestures**: Restored and integrated the configurable swipe action zones and nested tab gesture support.
- **Core Reliability**: Resolved 100+ architectural merge conflicts and compile errors introduced by the deep codebase rename and refactor, aligning the upstream features with the modern SQLite-based FTS library approach.

"""

content = content.replace("# Changelog\n\nThese notes are written in plain English and focus on changes that matter in everyday use.\n", new_changelog)

with open("CHANGELOG.md", "w") as f:
    f.write(content)
