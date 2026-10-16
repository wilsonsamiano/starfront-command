package com.wilson.starfront

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.maxOf
import kotlin.math.min

class Renderer {

    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val stroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val text = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    // Hoisted colors (hex literals use L suffix -> toInt for safety).
    private val bgColor = 0xFF0B1020L.toInt()
    private val gridColor = 0xFF161D33L.toInt()
    private val laneColor = 0xFF2E3A5CL.toInt()
    private val playerColor = 0xFF4DA3FFL.toInt()
    private val enemyColor = 0xFFFF5A5AL.toInt()
    private val bodyColor = 0xFF1A2340L.toInt()
    private val hpGood = 0xFF4DFF7FL.toInt()
    private val hpBad = 0xFFFF5A5AL.toInt()
    private val barBg = 0xFF333333L.toInt()
    private val panelColor = 0xDD1A2340L.toInt()
    private val topBarColor = 0xDD0B1020L.toInt()
    private val ghostOk = 0x664DFF7FL.toInt()
    private val ghostBad = 0x66FF5A5AL.toInt()
    private val boxColor = 0x334DA3FFL.toInt()

    fun draw(canvas: Canvas, game: Game, ui: UiState, w: Int, h: Int) {
        canvas.drawColor(bgColor)
        canvas.save()
        canvas.translate(w / 2f, h / 2f)
        canvas.scale(ui.zoom, ui.zoom)
        canvas.translate(-ui.camX, -ui.camY)
        drawWorld(canvas, game, ui)
        canvas.restore()
        drawHud(canvas, game, ui, w, h)
    }

    // ---------------- world ----------------

    private fun drawWorld(canvas: Canvas, game: Game, ui: UiState) {
        // grid
        stroke.color = gridColor
        stroke.strokeWidth = 2f
        var gx = 0f
        while (gx <= Game.WORLD_W) {
            canvas.drawLine(gx, 0f, gx, Game.WORLD_H, stroke)
            gx += 120f
        }
        var gy = 0f
        while (gy <= Game.WORLD_H) {
            canvas.drawLine(0f, gy, Game.WORLD_W, gy, stroke)
            gy += 120f
        }

        // lane path
        stroke.color = laneColor
        stroke.strokeWidth = 90f
        var px = Game.WORLD_W - 60f
        var py = Game.WORLD_H / 2f
        for (wp in game.waypoints) {
            canvas.drawLine(px, py, wp.first, wp.second, stroke)
            px = wp.first
            py = wp.second
        }
        canvas.drawLine(px, py, 230f, Game.WORLD_H / 2f, stroke)

        // enemy spawn marker
        fill.color = 0x44FF5A5AL.toInt()
        canvas.drawCircle(Game.WORLD_W - 60f, Game.WORLD_H / 2f, 95f, fill)

        for (b in game.buildings) drawBuilding(canvas, b)
        for (u in game.units) drawUnit(canvas, u)

        // projectiles
        for (p in game.projectiles) {
            if (p.dead) continue
            fill.color = p.color
            canvas.drawCircle(p.x, p.y, 6f, fill)
        }

        // selection box
        if (ui.showBox) {
            val l = min(ui.boxX0, ui.boxX1)
            val r = maxOf(ui.boxX0, ui.boxX1)
            val t = min(ui.boxY0, ui.boxY1)
            val bb = maxOf(ui.boxY0, ui.boxY1)
            fill.color = boxColor
            canvas.drawRect(l, t, r, bb, fill)
            stroke.color = Color.WHITE
            stroke.strokeWidth = 2f
            canvas.drawRect(l, t, r, bb, stroke)
        }

        // placement ghost
        val pk = ui.placementKind
        if (pk != null) {
            val st = Stats.buildings[pk] ?: return
            fill.color = if (ui.ghostOk) ghostOk else ghostBad
            canvas.drawCircle(ui.ghostX, ui.ghostY, st.radius, fill)
            stroke.color = if (ui.ghostOk) hpGood else hpBad
            stroke.strokeWidth = 3f
            canvas.drawCircle(ui.ghostX, ui.ghostY, st.radius, stroke)
        }
    }

    private fun drawUnit(canvas: Canvas, u: Unit) {
        if (!u.isAlive) return
        val c = if (u.team == Team.PLAYER) playerColor else enemyColor
        fill.color = bodyColor
        canvas.drawCircle(u.x, u.y, u.radius, fill)
        stroke.color = c
        stroke.strokeWidth = 4f
        canvas.drawCircle(u.x, u.y, u.radius, stroke)
        if (u.selected) {
            stroke.color = Color.WHITE
            stroke.strokeWidth = 3f
            canvas.drawCircle(u.x, u.y, u.radius + 8f, stroke)
        }
        text.color = Color.WHITE
        text.textSize = u.radius * 1.25f
        text.textAlign = Paint.Align.CENTER
        canvas.drawText(u.kind.glyph, u.x, u.y + text.textSize * 0.35f, text)
        if (u.hp < u.maxHp) {
            val w = u.radius * 2f
            val top = u.y - u.radius - 14f
            fill.color = barBg
            canvas.drawRect(u.x - w / 2f, top, u.x + w / 2f, top + 7f, fill)
            fill.color = if (u.team == Team.PLAYER) hpGood else hpBad
            canvas.drawRect(u.x - w / 2f, top, u.x - w / 2f + w * (u.hp / u.maxHp), top + 7f, fill)
        }
    }

    private fun drawBuilding(canvas: Canvas, b: Building) {
        if (!b.isAlive) return
        val c = if (b.team == Team.PLAYER) playerColor else enemyColor
        val s = b.radius
        val r = RectF(b.x - s, b.y - s, b.x + s, b.y + s)
        fill.color = bodyColor
        canvas.drawRoundRect(r, 14f, 14f, fill)
        stroke.color = c
        stroke.strokeWidth = 5f
        canvas.drawRoundRect(r, 14f, 14f, stroke)
        if (b.selected) {
            stroke.color = Color.WHITE
            stroke.strokeWidth = 3f
            val r2 = RectF(b.x - s - 8f, b.y - s - 8f, b.x + s + 8f, b.y + s + 8f)
            canvas.drawRoundRect(r2, 18f, 18f, stroke)
        }
        text.color = Color.WHITE
        text.textSize = s * 0.85f
        text.textAlign = Paint.Align.CENTER
        if (!b.complete) {
            // construction progress overlay
            fill.color = 0x88000000L.toInt()
            canvas.drawRoundRect(r, 14f, 14f, fill)
            text.textSize = 30f
            canvas.drawText("${(b.construction * 100).toInt()}%", b.x, b.y + 10f, text)
        } else {
            canvas.drawText(b.kind.glyph, b.x, b.y + text.textSize * 0.35f, text)
            if (b.kind == BuildingKind.BARRACKS && b.trainQueue.isNotEmpty()) {
                text.textSize = 24f
                text.color = hpGood
                canvas.drawText("Q:${b.trainQueue.size}", b.x, b.y + s + 30f, text)
                text.color = Color.WHITE
            }
        }
        if (b.hp < b.maxHp) {
            val w = s * 2f
            val top = b.y - s - 14f
            fill.color = barBg
            canvas.drawRect(b.x - w / 2f, top, b.x + w / 2f, top + 8f, fill)
            fill.color = hpGood
            canvas.drawRect(b.x - w / 2f, top, b.x - w / 2f + w * (b.hp / b.maxHp), top + 8f, fill)
        }
    }

    // ---------------- HUD ----------------

    private fun addButton(
        canvas: Canvas,
        ui: UiState,
        id: String,
        label: String,
        rightX: Float,
        top: Float,
        bw: Float = 130f,
        bh: Float = 60f
    ): Float {
        val r = RectF(rightX - bw, top, rightX, top + bh)
        fill.color = bodyColor
        canvas.drawRoundRect(r, 12f, 12f, fill)
        stroke.color = playerColor
        stroke.strokeWidth = 3f
        canvas.drawRoundRect(r, 12f, 12f, stroke)
        text.textSize = 30f
        text.color = Color.WHITE
        text.textAlign = Paint.Align.CENTER
        canvas.drawText(label, r.centerX(), r.centerY() + 10f, text)
        ui.buttons.add(UiState.HudButton(id, label, r))
        return rightX - bw - 12f
    }

    private fun panelButton(
        canvas: Canvas,
        ui: UiState,
        id: String,
        label: String,
        centerX: Float,
        top: Float,
        bw: Float = 440f,
        bh: Float = 76f
    ): Float {
        val r = RectF(centerX - bw / 2f, top, centerX + bw / 2f, top + bh)
        fill.color = bodyColor
        canvas.drawRoundRect(r, 16f, 16f, fill)
        stroke.color = playerColor
        stroke.strokeWidth = 3f
        canvas.drawRoundRect(r, 16f, 16f, stroke)
        text.textSize = 30f
        text.color = Color.WHITE
        text.textAlign = Paint.Align.CENTER
        canvas.drawText(label, r.centerX(), r.centerY() + 10f, text)
        ui.buttons.add(UiState.HudButton(id, label, r))
        return top + bh + 18f
    }

    private fun drawHud(canvas: Canvas, game: Game, ui: UiState, w: Int, h: Int) {
        ui.buttons.clear()

        // top bar
        fill.color = topBarColor
        canvas.drawRect(0f, 0f, w.toFloat(), 84f, fill)

        text.textAlign = Paint.Align.LEFT
        text.textSize = 32f
        text.color = Color.WHITE
        val waveText = if (game.phase == Game.Phase.BUILD)
            "WAVE ${game.wave + 1}/${Game.TOTAL_WAVES} IN ${maxOf(0, game.phaseTimer.toInt())}s"
        else
            "WAVE ${game.wave}/${Game.TOTAL_WAVES}"
        canvas.drawText(waveText, 20f, 54f, text)

        text.textAlign = Paint.Align.CENTER
        canvas.drawText("LOG ${game.logisticsUsed()}/${Game.LOGISTICS_CAP}  RES \u221E", w / 2f, 54f, text)

        var bx = w.toFloat() - 20f
        bx = addButton(canvas, ui, "pause", if (game.paused) "\u25B6" else "I I", bx, 12f)
        bx = addButton(canvas, ui, "speed", "${game.speed.toInt()}x", bx, 12f)
        addButton(canvas, ui, "info", "i", bx, 12f)

        if (game.phase == Game.Phase.BUILD && !game.gameOver) {
            val bw = 300f
            val bh = 64f
            val r = RectF(w / 2f - bw / 2f, 100f, w / 2f + bw / 2f, 100f + bh)
            fill.color = 0xFF2E7D32L.toInt()
            canvas.drawRoundRect(r, 14f, 14f, fill)
            text.textSize = 32f
            text.color = Color.WHITE
            canvas.drawText("START WAVE", r.centerX(), r.centerY() + 11f, text)
            ui.buttons.add(UiState.HudButton("startWave", "START WAVE", r))
        }

        // contextual action bar
        if (ui.actions.isNotEmpty()) {
            val bw = 250f
            val bh = 84f
            val gap = 16f
            val totalW = ui.actions.size * bw + (ui.actions.size - 1) * gap
            var ax = (w - totalW) / 2f
            val ay = h - bh - 24f
            for (a in ui.actions) {
                a.rect.set(ax, ay, ax + bw, ay + bh)
                fill.color = panelColor
                canvas.drawRoundRect(a.rect, 16f, 16f, fill)
                stroke.color = playerColor
                stroke.strokeWidth = 3f
                canvas.drawRoundRect(a.rect, 16f, 16f, stroke)
                text.textSize = 28f
                text.color = Color.WHITE
                text.textAlign = Paint.Align.CENTER
                canvas.drawText(a.label, a.rect.centerX(), a.rect.centerY() + 10f, text)
                ui.buttons.add(a)
                ax += bw + gap
            }
        }

        // minimap
        val mw = 220f
        val mh = 220f * Game.WORLD_H / Game.WORLD_W
        val mx = w - mw - 16f
        val my = 96f
        ui.minimapRect.set(mx, my, mx + mw, my + mh)
        fill.color = 0xAA0B1020L.toInt()
        canvas.drawRect(ui.minimapRect, fill)
        val sx = mw / Game.WORLD_W
        val sy = mh / Game.WORLD_H
        for (b in game.buildings) {
            if (!b.isAlive) continue
            fill.color = if (b.team == Team.PLAYER) playerColor else enemyColor
            canvas.drawCircle(mx + b.x * sx, my + b.y * sy, 5f, fill)
        }
        for (u in game.units) {
            if (!u.isAlive) continue
            fill.color = if (u.team == Team.PLAYER) playerColor else enemyColor
            canvas.drawCircle(mx + u.x * sx, my + u.y * sy, 3f, fill)
        }
        stroke.color = Color.WHITE
        stroke.strokeWidth = 2f
        val hw = (w / 2f) / ui.zoom
        val hh = (h / 2f) / ui.zoom
        canvas.drawRect(
            mx + (ui.camX - hw) * sx, my + (ui.camY - hh) * sy,
            mx + (ui.camX + hw) * sx, my + (ui.camY + hh) * sy, stroke
        )

        // toast message
        if (ui.messageTimer > 0f && ui.message.isNotEmpty()) {
            text.textSize = 32f
            text.color = 0xFFFFD27FL.toInt()
            text.textAlign = Paint.Align.CENTER
            canvas.drawText(ui.message, w / 2f, 190f, text)
            text.color = Color.WHITE
        }

        // info panel (version, support, updates)
        if (ui.showInfo) {
            fill.color = 0xAA000000L.toInt()
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
            val pw = 660f
            val ph = 500f
            val r = RectF(w / 2f - pw / 2f, h / 2f - ph / 2f, w / 2f + pw / 2f, h / 2f + ph / 2f)
            fill.color = 0xFF141B33L.toInt()
            canvas.drawRoundRect(r, 24f, 24f, fill)
            stroke.color = playerColor
            stroke.strokeWidth = 4f
            canvas.drawRoundRect(r, 24f, 24f, stroke)

            text.textAlign = Paint.Align.CENTER
            text.color = Color.WHITE
            text.textSize = 44f
            canvas.drawText("Starfront Command", w / 2f, r.top + 82f, text)
            text.textSize = 29f
            text.color = 0xFF9FB3D9L.toInt()
            canvas.drawText(
                "Beta ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                w / 2f, r.top + 132f, text
            )
            if (ui.updateStatus.isNotEmpty()) {
                text.textSize = 28f
                text.color = 0xFFFFD27FL.toInt()
                canvas.drawText(ui.updateStatus, w / 2f, r.top + 178f, text)
            }

            var by = r.top + 214f
            by = panelButton(canvas, ui, "bmc", "\u2615 Buy me a coffee", w / 2f, by)
            val updateId = if (ui.pendingUpdate != null) "downloadUpdate" else "checkUpdate"
            val updateLabel = if (ui.pendingUpdate != null) "Download update ${ui.pendingUpdate!!.tag}" else "Check for updates"
            by = panelButton(canvas, ui, updateId, updateLabel, w / 2f, by)
            panelButton(canvas, ui, "closeInfo", "Close", w / 2f, by)
        }

        // game over overlay
        if (game.gameOver) {
            fill.color = 0xAA000000L.toInt()
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 76f
            text.color = if (game.won) hpGood else hpBad
            canvas.drawText(if (game.won) "VICTORY" else "DEFEAT", w / 2f, h / 2f - 20f, text)
            text.textSize = 34f
            text.color = Color.WHITE
            canvas.drawText("Tap anywhere to play again", w / 2f, h / 2f + 60f, text)
        }
    }
}
