package com.wilson.starfront

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.net.Uri
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.sqrt

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    val game = Game()
    val ui = UiState()
    private val renderer = Renderer()
    private var thread: GameThread? = null

    // ---- touch tracking (written on UI thread, read on game thread) ----
    @Volatile private var fingerDown = false
    @Volatile private var downX = 0f
    @Volatile private var downY = 0f
    @Volatile private var downTime = 0L
    @Volatile private var moved = false
    @Volatile private var longPressFired = false
    @Volatile private var pinchMode = false
    private var pinchDist = 0f
    private var pinchMidX = 0f
    private var pinchMidY = 0f

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ---------------- surface lifecycle ----------------

    override fun surfaceCreated(h: SurfaceHolder) {
        startThread()
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, height: Int) {
        fitCamera(w, height)
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        stopThread()
    }

    private fun fitCamera(w: Int, h: Int) {
        if (w == 0 || h == 0) return
        // portrait: fit the world width, start the camera on the player's base
        ui.zoom = (w / Game.WORLD_W * 0.96f).coerceIn(0.3f, 2.2f)
        ui.camX = Game.WORLD_W / 2f
        ui.camY = Game.WORLD_H - 550f
        clampCamera()
    }

    private fun startThread() {
        if (thread == null) {
            val t = GameThread()
            thread = t
            t.start()
        }
    }

    private fun stopThread() {
        val t = thread ?: return
        t.running = false
        try {
            t.join()
        } catch (e: InterruptedException) {
            // shutting down anyway
        }
        thread = null
    }

    fun pauseGame() {
        synchronized(game) { game.paused = true }
    }

    fun resumeGame() {
        synchronized(game) { game.paused = false }
    }

    private inner class GameThread : Thread() {
        @Volatile var running = true

        override fun run() {
            var last = System.nanoTime()
            var acc = 0.0
            val step = 1.0 / 60.0
            while (running) {
                val now = System.nanoTime()
                var frame = (now - last) / 1e9
                last = now
                if (frame > 0.25) frame = 0.25

                checkLongPress()

                acc += frame
                var iter = 0
                while (acc >= step && iter < 4) {
                    synchronized(game) {
                        game.update((step * game.speed).toFloat())
                        if (ui.messageTimer > 0f) ui.messageTimer -= step.toFloat()
                    }
                    acc -= step
                    iter++
                }
                if (iter == 4) acc = 0.0

                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        synchronized(game) {
                            renderer.draw(canvas, game, ui, width, height)
                        }
                    }
                } catch (e: Exception) {
                    // surface not ready; skip frame
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (e: Exception) {
                            // surface gone; ignore
                        }
                    }
                }
            }
        }
    }

    // ---------------- coordinates ----------------

    private fun screenToWorldX(sx: Float): Float = (sx - width / 2f) / ui.zoom + ui.camX
    private fun screenToWorldY(sy: Float): Float = (sy - height / 2f) / ui.zoom + ui.camY

    private fun clampCamera() {
        val hw = width / 2f / ui.zoom
        val hh = height / 2f / ui.zoom
        ui.camX = if (hw * 2f >= Game.WORLD_W) Game.WORLD_W / 2f
        else ui.camX.coerceIn(hw, Game.WORLD_W - hw)
        ui.camY = if (hh * 2f >= Game.WORLD_H) Game.WORLD_H / 2f
        else ui.camY.coerceIn(hh, Game.WORLD_H - hh)
    }

    // ---------------- touch ----------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fingerDown = true
                moved = false
                longPressFired = false
                pinchMode = false
                downX = e.x
                downY = e.y
                downTime = System.currentTimeMillis()
                ui.showBox = false
                updateGhost(e.x, e.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount == 2) {
                    pinchMode = true
                    fingerDown = false
                    ui.showBox = false
                    pinchDist = pinchDistance(e)
                    pinchMidX = (e.getX(0) + e.getX(1)) / 2f
                    pinchMidY = (e.getY(0) + e.getY(1)) / 2f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinchMode && e.pointerCount >= 2) {
                    val nd = pinchDistance(e)
                    val midX = (e.getX(0) + e.getX(1)) / 2f
                    val midY = (e.getY(0) + e.getY(1)) / 2f
                    ui.camX -= (midX - pinchMidX) / ui.zoom
                    ui.camY -= (midY - pinchMidY) / ui.zoom
                    if (pinchDist > 0f && nd > 0f) {
                        ui.zoom = (ui.zoom * nd / pinchDist).coerceIn(0.3f, 2.2f)
                    }
                    pinchDist = nd
                    pinchMidX = midX
                    pinchMidY = midY
                    clampCamera()
                } else if (fingerDown && !pinchMode) {
                    val dx = e.x - downX
                    val dy = e.y - downY
                    if (!moved && sqrt(dx * dx + dy * dy) > 28f) {
                        moved = true
                    }
                    if (moved && ui.placementKind == null) {
                        ui.showBox = true
                        ui.boxX0 = screenToWorldX(downX)
                        ui.boxY0 = screenToWorldY(downY)
                        ui.boxX1 = screenToWorldX(e.x)
                        ui.boxY1 = screenToWorldY(e.y)
                    }
                    updateGhost(e.x, e.y)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // pinch over; swallow the tap that would follow
                pinchMode = false
                fingerDown = false
                longPressFired = true
                ui.showBox = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!pinchMode && fingerDown) {
                    if (moved && ui.showBox) {
                        finishBoxSelect()
                    } else if (!longPressFired) {
                        handleTap(e.x, e.y)
                    }
                }
                fingerDown = false
                pinchMode = false
                ui.showBox = false
            }
        }
        return true
    }

    private fun pinchDistance(e: MotionEvent): Float {
        val dx = e.getX(0) - e.getX(1)
        val dy = e.getY(0) - e.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    private fun updateGhost(sx: Float, sy: Float) {
        val pk = ui.placementKind
        if (pk != null && fingerDown && !pinchMode) {
            ui.ghostX = screenToWorldX(sx)
            ui.ghostY = screenToWorldY(sy)
            synchronized(game) {
                ui.ghostOk = game.canPlace(pk, ui.ghostX, ui.ghostY)
            }
        }
    }

    /** Fired from the game thread while the finger is held down. */
    private fun checkLongPress() {
        if (fingerDown && !moved && !pinchMode && !longPressFired &&
            ui.placementKind == null &&
            System.currentTimeMillis() - downTime > 550L
        ) {
            longPressFired = true
            val wx = screenToWorldX(downX)
            val wy = screenToWorldY(downY)
            synchronized(game) {
                issueOrder(wx, wy, attackMove = true)
                showMessage("Attack-move")
            }
        }
    }

    // ---------------- tap handling ----------------

    private fun handleTap(sx: Float, sy: Float) {
        // Whole body runs under the game lock: ui.buttons / ui.minimapRect are
        // rebuilt by the renderer on the game thread.
        synchronized(game) {
            for (b in ui.buttons) {
                if (b.rect.contains(sx, sy)) {
                    onButton(b.id)
                    return
                }
            }
            // info panel open: taps outside its buttons just close it
            if (ui.showInfo) {
                ui.showInfo = false
                return
            }
            if (game.gameOver) {
                game.reset()
                clearSelection()
                refreshActions()
                return
            }
            if (ui.minimapRect.contains(sx, sy)) {
                ui.camX = (sx - ui.minimapRect.left) / ui.minimapRect.width() * Game.WORLD_W
                ui.camY = (sy - ui.minimapRect.top) / ui.minimapRect.height() * Game.WORLD_H
                clampCamera()
                return
            }

            val wx = screenToWorldX(sx)
            val wy = screenToWorldY(sy)
            val pk = ui.placementKind
            if (pk != null) {
                if (game.placeBuilding(pk, wx, wy)) {
                    ui.placementKind = null
                    refreshActions()
                    showMessage("${pk.displayName} placed")
                } else {
                    showMessage("Can't build here")
                }
                return
            }

            // nearest player building?
            var bestB: Building? = null
            var bestBd = Float.MAX_VALUE
            for (b in game.buildings) {
                if (!b.isAlive || b.team != Team.PLAYER) continue
                val d = dist(wx, wy, b.x, b.y) - b.radius
                if (d < 26f && d < bestBd) {
                    bestBd = d
                    bestB = b
                }
            }
            // nearest player unit?
            var bestU: Unit? = null
            var bestUd = Float.MAX_VALUE
            for (u in game.units) {
                if (!u.isAlive || u.team != Team.PLAYER) continue
                val d = dist(wx, wy, u.x, u.y) - u.radius
                if (d < 30f && d < bestUd) {
                    bestUd = d
                    bestU = u
                }
            }

            // prefer whichever is closer (units slightly favored on ties)
            val pickUnit = bestU != null && (bestB == null || bestUd <= bestBd + 10f)
            if (pickUnit) {
                selectUnits(listOf(bestU!!))
                return
            }
            if (bestB != null) {
                selectBuilding(bestB)
                return
            }

            // empty ground
            val barracks = ui.selectedBuilding
            if (barracks != null && barracks.kind == BuildingKind.BARRACKS) {
                barracks.rallyX = wx
                barracks.rallyY = wy
                showMessage("Rally point set")
                return
            }
            if (ui.selectedUnits.isNotEmpty()) {
                issueOrder(wx, wy, attackMove = false)
            } else {
                clearSelection()
                refreshActions()
            }
        }
    }

    private fun finishBoxSelect() {
        val l = minOf(ui.boxX0, ui.boxX1)
        val r = maxOf(ui.boxX0, ui.boxX1)
        val t = minOf(ui.boxY0, ui.boxY1)
        val bb = maxOf(ui.boxY0, ui.boxY1)
        synchronized(game) {
            val found = game.units.filter { u ->
                u.isAlive && u.team == Team.PLAYER && u.x in l..r && u.y in t..bb
            }
            if (found.isNotEmpty()) selectUnits(found) else {
                clearSelection()
                refreshActions()
            }
        }
    }

    // ---------------- selection & orders ----------------

    private fun clearSelection() {
        for (u in ui.selectedUnits) u.selected = false
        ui.selectedUnits.clear()
        ui.selectedBuilding?.selected = false
        ui.selectedBuilding = null
    }

    private fun selectUnits(list: List<Unit>) {
        clearSelection()
        for (u in list) {
            u.selected = true
            ui.selectedUnits.add(u)
        }
        refreshActions()
    }

    private fun selectBuilding(b: Building) {
        clearSelection()
        b.selected = true
        ui.selectedBuilding = b
        refreshActions()
    }

    private fun issueOrder(wx: Float, wy: Float, attackMove: Boolean) {
        for (u in ui.selectedUnits) {
            if (!u.isAlive) continue
            u.destX = wx
            u.destY = wy
            u.attackMove = attackMove
            u.target = null
            u.waypointIndex = 0
        }
    }

    private fun refreshActions() {
        ui.actions.clear()
        val b = ui.selectedBuilding
        if (b != null && ui.selectedUnits.isEmpty()) {
            if (b.kind == BuildingKind.BARRACKS && b.complete) {
                ui.actions.add(UiState.HudButton("train:MILITIA", "Militia (1)"))
                ui.actions.add(UiState.HudButton("train:RANGER", "Ranger (1)"))
                ui.actions.add(UiState.HudButton("train:BULWARK", "Bulwark (3)"))
            }
        } else if (ui.selectedUnits.size == 1 && ui.selectedUnits[0].kind == UnitKind.CONSTRUCTOR) {
            ui.actions.add(UiState.HudButton("build:MG_TURRET", "MG Turret (4)"))
            ui.actions.add(UiState.HudButton("build:BARRACKS", "Barracks"))
        }
        if (ui.placementKind != null) {
            ui.actions.add(UiState.HudButton("cancelPlace", "Cancel"))
        }
    }

    private fun onButton(id: String) {
        synchronized(game) {
            when {
                id == "pause" -> game.paused = !game.paused
                id == "speed" -> game.speed = if (game.speed < 1.5f) 2f else 1f
                id == "info" -> ui.showInfo = true
                id == "closeInfo" -> ui.showInfo = false
                id == "bmc" -> {
                    ui.showInfo = false
                    openLink(AppLinks.BMC_URL)
                }
                id == "checkUpdate" -> manualCheck()
                id == "downloadUpdate" -> {
                    val update = ui.pendingUpdate
                    if (update != null) {
                        (context as MainActivity).startUpdateDownload(update.apkUrl)
                        ui.updateStatus = "Downloading update ${update.tag}…"
                    }
                }
                id == "startWave" -> {
                    game.startWave()
                    showMessage("Wave ${game.wave} incoming!")
                }
                id == "cancelPlace" -> {
                    ui.placementKind = null
                    refreshActions()
                }
                id.startsWith("build:") -> {
                    val kind = BuildingKind.valueOf(id.removePrefix("build:"))
                    ui.placementKind = if (ui.placementKind == kind) null else kind
                    if (ui.placementKind != null) showMessage("Tap map to place ${kind.displayName}")
                    refreshActions()
                }
                id.startsWith("train:") -> {
                    val kind = UnitKind.valueOf(id.removePrefix("train:"))
                    val barracks = ui.selectedBuilding
                    if (barracks != null && game.trainUnit(barracks, kind)) {
                        showMessage("Training ${kind.displayName}")
                    } else {
                        showMessage("Logistics full!")
                    }
                }
            }
        }
    }

    private fun showMessage(msg: String) {
        ui.message = msg
        ui.messageTimer = 2.2f
    }

    /** Called (on the UI thread) when the launch check finds a newer release. */
    fun showUpdateAvailable(info: UpdateInfo) {
        synchronized(game) {
            ui.pendingUpdate = info
            ui.updateStatus = "Update ${info.tag} available — tap Download"
            ui.showInfo = true
        }
    }

    private fun manualCheck() {
        ui.updateStatus = "Checking for updates…"
        ui.pendingUpdate = null
        UpdateManager.checkForUpdate { info ->
            post {
                synchronized(game) {
                    if (info != null) {
                        ui.pendingUpdate = info
                        ui.updateStatus = "Update ${info.tag} available"
                    } else {
                        ui.updateStatus = "You're up to date"
                    }
                }
            }
        }
    }

    private fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            showMessage("Couldn't open link")
        }
    }
}
