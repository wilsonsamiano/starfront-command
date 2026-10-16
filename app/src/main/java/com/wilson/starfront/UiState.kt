package com.wilson.starfront

import android.graphics.RectF

/** All transient UI state owned by GameView and read by Renderer. */
class UiState {
    data class HudButton(val id: String, val label: String, val rect: RectF = RectF())

    var camX: Float = Game.WORLD_W / 2f
    var camY: Float = Game.WORLD_H / 2f
    var zoom: Float = 0.55f

    val selectedUnits = mutableListOf<Unit>()
    var selectedBuilding: Building? = null

    /** Building type the player is currently placing, or null. */
    var placementKind: BuildingKind? = null
    var ghostX: Float = 0f
    var ghostY: Float = 0f
    var ghostOk: Boolean = false

    var showBox: Boolean = false
    var boxX0: Float = 0f
    var boxY0: Float = 0f
    var boxX1: Float = 0f
    var boxY1: Float = 0f

    /** Contextual action-bar buttons (rebuilt on selection change). */
    val actions = mutableListOf<HudButton>()

    /** Every tappable HUD button this frame (rebuilt by Renderer each draw). */
    val buttons = mutableListOf<HudButton>()

    val minimapRect: RectF = RectF()

    var message: String = ""
    var messageTimer: Float = 0f
}
