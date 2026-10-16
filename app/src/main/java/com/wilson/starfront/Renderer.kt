package com.wilson.starfront

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.maxOf
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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

    private val bgColor = 0xFF0B1020L.toInt()
    private val playerColor = 0xFF4DA3FFL.toInt()
    private val enemyColor = 0xFFFF5A5AL.toInt()
    private val hpGood = 0xFF4DFF7FL.toInt()
    private val hpBad = 0xFFFF5A5AL.toInt()
    private val barBg = 0xFF333333L.toInt()
    private val bodyColor = 0xFF1A2340L.toInt()
    private val panelColor = 0xDD1A2340L.toInt()
    private val topBarColor = 0xDD0B1020L.toInt()
    private val ghostOk = 0x664DFF7FL.toInt()
    private val ghostBad = 0x66FF5A5AL.toInt()
    private val boxColor = 0x334DA3FFL.toInt()
    private val dimText = 0xFF9FB3D9L.toInt()
    private val warnText = 0xFFFFD27FL.toInt()

    // Pre-rendered static terrain (drawn once, blitted per frame).
    private var bg: Bitmap? = null
    private val bgDst = RectF()

    // Sprite art (StarCraft-style pixel art, loaded from drawable-nodpi).
    private val spritePaint = Paint().apply { isAntiAlias = true; isFilterBitmap = false }
    private data class Sprite(val bmp: Bitmap, val drawW: Float, val drawH: Float)
    private var spriteCache: Map<String, Sprite>? = null

    private fun sprites(): Map<String, Sprite> {
        spriteCache?.let { return it }
        fun load(name: String, drawW: Float): Sprite {
            val bmp = Sprites.get(name)
            return Sprite(bmp, drawW, drawW * bmp.height / bmp.width)
        }
        val m = mapOf(
            "marine" to load("marine", 46f),
            "ranger" to load("ranger", 46f),
            "constructor" to load("constructor", 46f),
            "bulwark" to load("bulwark", 84f),
            "skitterling" to load("skitterling", 40f),
            "spiker" to load("spiker", 58f),
            "ravager" to load("ravager", 120f),
            "hq" to load("hq", 130f),
            "turret" to load("turret", 64f),
            "barracks" to load("barracks", 120f)
        )
        spriteCache = m
        return m
    }

    private fun unitSpriteName(kind: UnitKind): String = when (kind) {
        UnitKind.MILITIA -> "marine"
        UnitKind.RANGER -> "ranger"
        UnitKind.BULWARK -> "bulwark"
        UnitKind.CONSTRUCTOR -> "constructor"
        UnitKind.SKITTERLING -> "skitterling"
        UnitKind.SPIKER -> "spiker"
        UnitKind.RAVAGER -> "ravager"
    }

    private fun centered(sp: Sprite, x: Float, y: Float) =
        RectF(x - sp.drawW / 2f, y - sp.drawH / 2f, x + sp.drawW / 2f, y + sp.drawH / 2f)

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

    // ---------------- static terrain ----------------

    private fun ensureBackground() {
        if (bg != null) return
        val w = 700
        val h = (700f * Game.WORLD_H / Game.WORLD_W).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = w / Game.WORLD_W
        val rnd = Random(1337)

        c.drawColor(0xFF0D1326L.toInt())
        repeat(650) {
            fill.color = if (rnd.nextBoolean()) 0xFF121B32L.toInt() else 0xFF0A0F22L.toInt()
            fill.alpha = 50
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, (6f + rnd.nextFloat() * 30f) * s, fill)
        }
        fill.alpha = 255
        stroke.color = 0xFF182034L.toInt()
        stroke.strokeWidth = 1.5f
        var gx = 0f
        while (gx <= w) {
            c.drawLine(gx, 0f, gx, h.toFloat(), stroke)
            gx += 120f * s
        }
        var gy = 0f
        while (gy <= h) {
            c.drawLine(0f, gy, w.toFloat(), gy, stroke)
            gy += 120f * s
        }

        // lane path in bitmap space
        val cx = Game.WORLD_W / 2f
        val pts = listOf(
            Pair(cx * s, 80f * s),
            Pair(cx * s, 420f * s),
            Pair((cx - 130f) * s, 950f * s),
            Pair((cx + 130f) * s, 1500f * s),
            Pair(cx * s, 1940f * s),
            Pair(cx * s, (Game.WORLD_H - 230f) * s)
        )
        val lp = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        lp.color = 0xFF1E263EL.toInt()
        lp.strokeWidth = 108f * s
        strokePath(c, pts, lp)
        lp.color = 0xFF3B3349L.toInt()
        lp.strokeWidth = 90f * s
        strokePath(c, pts, lp)
        // dirt speckles along the lane
        repeat(260) {
            val seg = rnd.nextInt(pts.size - 1)
            val (x1, y1) = pts[seg]
            val (x2, y2) = pts[seg + 1]
            val t = rnd.nextFloat()
            val nx = y1 - y2
            val ny = x2 - x1
            val nl = sqrt(nx * nx + ny * ny).takeIf { it > 0.01f } ?: 1f
            val off = (rnd.nextFloat() - 0.5f) * 70f * s
            fill.color = if (rnd.nextBoolean()) 0xFF2E2840L.toInt() else 0xFF463E55L.toInt()
            fill.alpha = 90
            c.drawCircle(
                x1 + (x2 - x1) * t + nx / nl * off,
                y1 + (y2 - y1) * t + ny / nl * off,
                (2f + rnd.nextFloat() * 4f) * s, fill
            )
        }
        fill.alpha = 255
        bg = bmp
    }

    private fun strokePath(c: Canvas, pts: List<Pair<Float, Float>>, paint: Paint) {
        for (i in 0 until pts.size - 1) {
            c.drawLine(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second, paint)
        }
    }

    // ---------------- world ----------------

    private fun drawWorld(canvas: Canvas, game: Game, ui: UiState) {
        ensureBackground()
        bgDst.set(0f, 0f, Game.WORLD_W, Game.WORLD_H)
        canvas.drawBitmap(bg, null, bgDst, null)

        // spawn pulse
        val pulse = 0.5f + 0.5f * sin(game.time * 3f)
        fill.color = enemyColor
        fill.alpha = (36 + 44 * pulse).toInt()
        canvas.drawCircle(game.spawnX, game.spawnY, 95f, fill)
        fill.alpha = 255

        for (b in game.buildings) drawBuilding(canvas, b, game)
        for (u in game.units) drawUnit(canvas, u, game)

        // projectiles with motion trails
        for (p in game.projectiles) {
            if (p.dead) continue
            stroke.color = p.color
            stroke.strokeWidth = 5f
            canvas.drawLine(p.px, p.py, p.x, p.y, stroke)
            fill.color = 0xFFFFFFFFL.toInt()
            canvas.drawCircle(p.x, p.y, 4f, fill)
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

    // ---------------- unit sprites (facing +x) ----------------

    private fun drawUnit(canvas: Canvas, u: Unit, game: Game) {
        if (!u.isAlive) return
        val teamC = if (u.team == Team.PLAYER) playerColor else enemyColor

        fill.color = 0x55000000L.toInt()
        canvas.drawOval(
            u.x - u.radius, u.y - u.radius * 0.6f + 6f,
            u.x + u.radius, u.y + u.radius * 0.6f + 6f, fill
        )

        val sp = sprites()[unitSpriteName(u.kind)] ?: return
        canvas.save()
        canvas.translate(u.x, u.y)
        canvas.rotate(Math.toDegrees(u.facing.toDouble()).toFloat())
        canvas.drawBitmap(
            sp.bmp, null,
            RectF(-sp.drawW / 2f, -sp.drawH / 2f, sp.drawW / 2f, sp.drawH / 2f),
            spritePaint
        )
        canvas.restore()

        if (u.flash > 0f) {
            val a = (u.flash / 0.12f).coerceIn(0f, 1f)
            val tipR = sp.drawW / 2f + 8f
            fill.color = 0xFFFFE08AL.toInt()
            fill.alpha = (230 * a).toInt()
            canvas.drawCircle(u.x + cos(u.facing) * tipR, u.y + sin(u.facing) * tipR, 5f + 9f * a, fill)
            fill.alpha = 255
        }

        stroke.color = teamC
        stroke.strokeWidth = 3f
        canvas.drawCircle(u.x, u.y, u.radius + 4f, stroke)
        if (u.selected) {
            stroke.color = Color.WHITE
            stroke.strokeWidth = 2.5f
            canvas.drawCircle(u.x, u.y, u.radius + 9f, stroke)
        }
        if (u.hp < u.maxHp) {
            val w = u.radius * 2f
            val top = u.y - u.radius - 13f
            fill.color = barBg
            canvas.drawRect(u.x - w / 2f, top, u.x + w / 2f, top + 6f, fill)
            fill.color = if (u.team == Team.PLAYER) hpGood else hpBad
            canvas.drawRect(u.x - w / 2f, top, u.x - w / 2f + w * (u.hp / u.maxHp), top + 6f, fill)
        }
    }

    // ---------------- buildings ----------------

    private fun drawBuilding(canvas: Canvas, b: Building, game: Game) {
        if (!b.isAlive) return
        val s = b.radius
        fill.color = 0x55000000L.toInt()
        canvas.drawRoundRect(b.x - s, b.y - s + 8f, b.x + s, b.y + s + 8f, 14f, 14f, fill)
        when (b.kind) {
            BuildingKind.COMMAND_CORE -> {
                val sp = sprites()["hq"]!!
                canvas.drawBitmap(sp.bmp, null, centered(sp, b.x, b.y), spritePaint)
            }
            BuildingKind.BARRACKS -> {
                val sp = sprites()["barracks"]!!
                canvas.drawBitmap(sp.bmp, null, centered(sp, b.x, b.y), spritePaint)
            }
            BuildingKind.MG_TURRET -> {
                val sp = sprites()["turret"]!!
                canvas.save()
                canvas.translate(b.x, b.y)
                canvas.rotate(Math.toDegrees(b.turretAngle.toDouble()).toFloat())
                canvas.drawBitmap(
                    sp.bmp, null,
                    RectF(-sp.drawW / 2f, -sp.drawH / 2f, sp.drawW / 2f, sp.drawH / 2f),
                    spritePaint
                )
                canvas.restore()
                if (b.flash > 0f) {
                    val a = (b.flash / 0.12f).coerceIn(0f, 1f)
                    fill.color = 0xFFFFE08AL.toInt()
                    fill.alpha = (230 * a).toInt()
                    canvas.drawCircle(
                        b.x + cos(b.turretAngle) * (sp.drawW / 2f + 8f),
                        b.y + sin(b.turretAngle) * (sp.drawW / 2f + 8f),
                        5f + 9f * a, fill
                    )
                    fill.alpha = 255
                }
            }
        }
        if (b.selected) {
            stroke.color = Color.WHITE
            stroke.strokeWidth = 3f
            canvas.drawRoundRect(b.x - s - 8f, b.y - s - 8f, b.x + s + 8f, b.y + s + 8f, 18f, 18f, stroke)
        }
        if (!b.complete) {
            fill.color = 0x88000000L.toInt()
            canvas.drawRoundRect(b.x - s, b.y - s, b.x + s, b.y + s, 14f, 14f, fill)
            text.color = Color.WHITE
            text.textSize = 30f
            text.textAlign = Paint.Align.CENTER
            canvas.drawText("${(b.construction * 100).toInt()}%", b.x, b.y + 10f, text)
        } else if (b.kind == BuildingKind.BARRACKS && b.trainQueue.isNotEmpty()) {
            text.textSize = 24f
            text.color = hpGood
            text.textAlign = Paint.Align.CENTER
            canvas.drawText("Q:${b.trainQueue.size}", b.x, b.y + s + 30f, text)
            text.color = Color.WHITE
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

        // top bar: two lines on the left so nothing collides with the buttons
        fill.color = topBarColor
        canvas.drawRect(0f, 0f, w.toFloat(), 84f, fill)

        text.textAlign = Paint.Align.LEFT
        text.textSize = 30f
        text.color = Color.WHITE
        val waveText = if (game.phase == Game.Phase.BUILD)
            "WAVE ${game.wave + 1}/${Game.TOTAL_WAVES} IN ${maxOf(0, game.phaseTimer.toInt())}s"
        else
            "WAVE ${game.wave}/${Game.TOTAL_WAVES}"
        canvas.drawText(waveText, 20f, 40f, text)
        text.textSize = 25f
        text.color = dimText
        canvas.drawText("LOG ${game.logisticsUsed()}/${Game.LOGISTICS_CAP}   RES \u221E", 20f, 70f, text)

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
            text.textAlign = Paint.Align.CENTER
            canvas.drawText("START WAVE", r.centerX(), r.centerY() + 11f, text)
            ui.buttons.add(UiState.HudButton("startWave", "START WAVE", r))
        }

        // contextual action bar (button width shrinks to fit narrow screens)
        if (ui.actions.isNotEmpty()) {
            val bh = 84f
            val gap = 16f
            val n = ui.actions.size
            val bw = min(((w - 32f) - (n - 1) * gap) / n, 250f)
            val totalW = n * bw + (n - 1) * gap
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
            text.color = warnText
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
            text.color = dimText
            canvas.drawText(
                "Beta ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                w / 2f, r.top + 132f, text
            )
            if (ui.updateStatus.isNotEmpty()) {
                text.textSize = 28f
                text.color = warnText
                canvas.drawText(ui.updateStatus, w / 2f, r.top + 178f, text)
            }

            var by = r.top + 214f
            by = panelButton(canvas, ui, "bmc", "\u2615 Buy me a coffee", w / 2f, by)
            val updateId = if (ui.pendingUpdate != null) "downloadUpdate" else "checkUpdate"
            val updateLabel =
                if (ui.pendingUpdate != null) "Download update ${ui.pendingUpdate!!.tag}" else "Check for updates"
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
