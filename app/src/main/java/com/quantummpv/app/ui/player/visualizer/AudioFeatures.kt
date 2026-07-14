package com.quantummpv.app.ui.player.visualizer

class AudioFeatures {
    @Volatile var energy: Float = 0f
    @Volatile var bass: Float = 0f
    @Volatile var mid: Float = 0f
    @Volatile var treble: Float = 0f
    @Volatile var beat: Float = 0f
    @Volatile var centroid: Float = 0.35f
    @Volatile var active: Boolean = false

    fun reset() {
        energy = 0f
        bass = 0f
        mid = 0f
        treble = 0f
        beat = 0f
        centroid = 0.35f
        active = false
    }
}
