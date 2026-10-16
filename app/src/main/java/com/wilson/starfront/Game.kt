package com.wilson.starfront

import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return sqrt(dx * dx + dy * dy)
}

/**
 * M1 simulation: Terran-only tower defense, one lane, 10 waves,
 * unlimited resources, Logistics cap as the pacing constraint.
 */
class Game {

    enum class Phase { BUILD, COMBAT }

    companion object {
        const val WORLD_W = 1400f
        const val WORLD_H = 2400f
        const val LOGISTICS_CAP = 40
        const val TOTAL_WAVES = 10
        const val BUILD_PHASE_TIME = 25f
        const val BETWEEN_WAVE_TIME = 18f
        const val SPAWN_INTERVAL = 0.7f
    }

    val units = mutableListOf<Unit>()
    val buildings = mutableListOf<Building>()
    val projectiles = mutableListOf<Projectile>()
    val waypoints: List<Pair<Float, Float>>

    var wave = 0
    var phase = Phase.BUILD
    var phaseTimer = BUILD_PHASE_TIME
    var gameOver = false
    var won = false
    var speed = 1f
    var paused = false
    var time = 0f

    private var nextId = 1
    private val spawnQueue: ArrayDeque<UnitKind> = ArrayDeque()
    private var spawnTimer = 0f

    val spawnX = WORLD_W / 2f
    val spawnY = 80f

    init {
        val cx = WORLD_W / 2f
        waypoints = listOf(
            Pair(cx, 420f),
            Pair(cx - 130f, 950f),
            Pair(cx + 130f, 1500f),
            Pair(cx, 1940f)
        )
        reset()
    }

    fun reset() {
        units.clear()
        buildings.clear()
        projectiles.clear()
        spawnQueue.clear()
        wave = 0
        phase = Phase.BUILD
        phaseTimer = BUILD_PHASE_TIME
        gameOver = false
        won = false
        paused = false
        speed = 1f
        nextId = 1
        spawnTimer = 0f
        val cx = WORLD_W / 2f
        val baseY = WORLD_H - 230f
        buildings.add(Building(nextId++, BuildingKind.COMMAND_CORE, Team.PLAYER, cx, baseY))
        units.add(Unit(nextId++, UnitKind.CONSTRUCTOR, Team.PLAYER, cx - 100f, baseY - 110f))
        units.add(Unit(nextId++, UnitKind.CONSTRUCTOR, Team.PLAYER, cx + 100f, baseY - 110f))
        val mx = floatArrayOf(cx - 140f, cx - 60f, cx + 60f, cx + 140f)
        repeat(4) { i ->
            units.add(Unit(nextId++, UnitKind.MILITIA, Team.PLAYER, mx[i], baseY - 220f))
        }
    }

    fun playerCore(): Building? =
        buildings.firstOrNull { it.team == Team.PLAYER && it.kind == BuildingKind.COMMAND_CORE && it.isAlive }

    fun logisticsUsed(): Int {
        var used = 0
        for (u in units) if (u.team == Team.PLAYER && u.isAlive) used += u.stats.logistics
        for (b in buildings) if (b.team == Team.PLAYER && b.isAlive) used += b.stats.logistics
        return used
    }

    fun startWave() {
        if (phase != Phase.BUILD || gameOver) return
        wave++
        phase = Phase.COMBAT
        spawnQueue.addAll(buildWave(wave))
        spawnTimer = 0.5f
    }

    private fun buildWave(n: Int): List<UnitKind> {
        val list = mutableListOf<UnitKind>()
        repeat(4 + n * 2) { list.add(UnitKind.SKITTERLING) }
        repeat(n / 2) { list.add(UnitKind.SPIKER) }
        if (n == TOTAL_WAVES) list.add(UnitKind.RAVAGER)
        return list
    }

    fun trainUnit(b: Building, kind: UnitKind): Boolean {
        if (b.kind != BuildingKind.BARRACKS || !b.complete) return false
        val st = Stats.units[kind] ?: return false
        var queued = st.logistics
        for (q in b.trainQueue) queued += (Stats.units[q]?.logistics ?: 0)
        if (logisticsUsed() + queued > LOGISTICS_CAP) return false
        b.trainQueue.add(kind)
        return true
    }

    fun canPlace(kind: BuildingKind, x: Float, y: Float): Boolean {
        val st = Stats.buildings[kind] ?: return false
        if (logisticsUsed() + st.logistics > LOGISTICS_CAP) return false
        val cx = x.coerceIn(80f, WORLD_W - 80f)
        val cy = y.coerceIn(80f, WORLD_H - 80f)
        for (b in buildings) {
            if (!b.isAlive) continue
            if (dist(cx, cy, b.x, b.y) < b.radius + st.radius + 24f) return false
        }
        for (u in units) {
            if (u.team == Team.ENEMY && u.isAlive && dist(cx, cy, u.x, u.y) < 140f) return false
        }
        return true
    }

    fun placeBuilding(kind: BuildingKind, x: Float, y: Float): Boolean {
        if (!canPlace(kind, x, y)) return false
        val cx = x.coerceIn(80f, WORLD_W - 80f)
        val cy = y.coerceIn(80f, WORLD_H - 80f)
        buildings.add(Building(nextId++, kind, Team.PLAYER, cx, cy))
        return true
    }

    fun update(dt: Float) {
        if (gameOver || paused) return
        time += dt

        if (phase == Phase.BUILD) {
            phaseTimer -= dt
            if (phaseTimer <= 0f) startWave()
        } else if (spawnQueue.isNotEmpty()) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                spawnTimer = SPAWN_INTERVAL
                val kind = spawnQueue.removeFirst()
                val jitter = (Math.random().toFloat() * 140f) - 70f
                units.add(Unit(nextId++, kind, Team.ENEMY, spawnX + jitter, spawnY))
            }
        }

        for (u in units) updateUnit(u, dt)
        for (b in buildings) updateBuilding(b, dt)
        for (p in projectiles) updateProjectile(p, dt)
        separateUnits()

        units.removeAll { !it.isAlive }
        buildings.removeAll { !it.isAlive }
        projectiles.removeAll { it.dead || !it.target.isAlive }

        if (playerCore() == null) {
            gameOver = true
            won = false
            return
        }
        if (phase == Phase.COMBAT && spawnQueue.isEmpty() && units.none { it.team == Team.ENEMY }) {
            if (wave >= TOTAL_WAVES) {
                gameOver = true
                won = true
            } else {
                phase = Phase.BUILD
                phaseTimer = BETWEEN_WAVE_TIME
            }
        }
    }

    private fun updateUnit(u: Unit, dt: Float) {
        if (!u.isAlive) return
        val s = u.stats
        u.cooldown = max(0f, u.cooldown - dt)
        u.flash = max(0f, u.flash - dt)

        val cur = u.target
        if (cur != null && (!cur.isAlive || dist(u.x, u.y, cur.x, cur.y) > s.aggroRange + 80f)) {
            u.target = null
        }
        if (u.target == null && s.aggroRange > 0f) {
            u.target = findTarget(u)
        }

        val t = u.target
        if (t != null) {
            val d = dist(u.x, u.y, t.x, t.y)
            if (d <= s.range + t.radius) {
                u.facing = atan2(t.y - u.y, t.x - u.x)
                if (u.cooldown <= 0f) {
                    if (s.ranged) {
                        val color = if (u.team == Team.PLAYER) 0xFF7FC4FFL.toInt() else 0xFFFF7F7FL.toInt()
                        projectiles.add(Projectile(u.x, u.y, t, 750f, s.damage, u.team, color))
                    } else {
                        t.hp -= s.damage
                    }
                    u.flash = 0.12f
                    u.cooldown = s.attackPeriod
                }
            } else {
                moveToward(u, t.x, t.y, dt)
            }
            return
        }

        if (u.team == Team.ENEMY) {
            if (u.waypointIndex < waypoints.size) {
                val wp = waypoints[u.waypointIndex]
                if (moveToward(u, wp.first, wp.second, dt)) u.waypointIndex++
            } else {
                val core = playerCore()
                if (core != null) u.target = core
            }
        } else {
            if (u.attackMove && s.aggroRange > 0f) {
                val seen = findTarget(u)
                if (seen != null) {
                    u.target = seen
                    return
                }
            }
            moveToward(u, u.destX, u.destY, dt)
        }
    }

    /** Moves toward (tx, ty). Returns true when arrived. */
    private fun moveToward(u: Unit, tx: Float, ty: Float, dt: Float): Boolean {
        val dx = tx - u.x
        val dy = ty - u.y
        val d = sqrt(dx * dx + dy * dy)
        if (d < 4f) return true
        u.facing = atan2(dy, dx)
        val step = min(u.stats.speed * dt, d)
        u.x += dx / d * step
        u.y += dy / d * step
        return false
    }

    private fun findTarget(u: Unit): Damageable? {
        var best: Damageable? = null
        var bestD = u.stats.aggroRange
        for (e in units) {
            if (e.team == u.team || !e.isAlive) continue
            val d = dist(u.x, u.y, e.x, e.y)
            if (d < bestD) {
                bestD = d
                best = e
            }
        }
        for (b in buildings) {
            if (b.team == u.team || !b.isAlive || !b.complete) continue
            val d = dist(u.x, u.y, b.x, b.y)
            if (d < bestD) {
                bestD = d
                best = b
            }
        }
        return best
    }

    private fun nearestEnemy(x: Float, y: Float, range: Float): Damageable? {
        var best: Damageable? = null
        var bestD = range
        for (e in units) {
            if (e.team != Team.ENEMY || !e.isAlive) continue
            val d = dist(x, y, e.x, e.y)
            if (d < bestD) {
                bestD = d
                best = e
            }
        }
        return best
    }

    private fun updateBuilding(b: Building, dt: Float) {
        if (!b.isAlive) return
        if (!b.complete) {
            b.construction = min(1f, b.construction + dt / b.stats.buildTime)
            return
        }
        b.cooldown = max(0f, b.cooldown - dt)
        b.flash = max(0f, b.flash - dt)
        if (b.kind == BuildingKind.MG_TURRET) {
            val target = nearestEnemy(b.x, b.y, b.stats.range)
            if (target != null) {
                b.turretAngle = atan2(target.y - b.y, target.x - b.x)
                if (b.cooldown <= 0f) {
                    projectiles.add(Projectile(b.x, b.y - 10f, target, 900f, b.stats.damage, b.team, 0xFFFFD27FL.toInt()))
                    b.cooldown = b.stats.attackPeriod
                    b.flash = 0.12f
                }
            }
        }
        if (b.kind == BuildingKind.BARRACKS && b.trainQueue.isNotEmpty()) {
            val kind = b.trainQueue.first()
            val st = Stats.units[kind] ?: return
            if (logisticsUsed() + st.logistics > LOGISTICS_CAP) return // wait for space
            b.trainProgress += dt
            if (b.trainProgress >= st.buildTime) {
                b.trainProgress = 0f
                b.trainQueue.removeFirst()
                val u = Unit(nextId++, kind, Team.PLAYER, b.x, b.y + b.radius + 30f)
                u.destX = b.rallyX
                u.destY = b.rallyY
                units.add(u)
            }
        }
    }

    private fun updateProjectile(p: Projectile, dt: Float) {
        if (p.dead || !p.target.isAlive) {
            p.dead = true
            return
        }
        val dx = p.target.x - p.x
        val dy = p.target.y - p.y
        val d = sqrt(dx * dx + dy * dy)
        val step = p.speed * dt
        if (d <= step + p.target.radius) {
            p.target.hp -= p.damage
            p.dead = true
        } else {
            p.px = p.x
            p.py = p.y
            p.x += dx / d * step
            p.y += dy / d * step
        }
    }

    /** Cheap O(n^2) separation so units don't stack. Fine at M1 unit counts. */
    private fun separateUnits() {
        val n = units.size
        for (i in 0 until n) {
            val a = units[i]
            if (!a.isAlive) continue
            for (j in i + 1 until n) {
                val c = units[j]
                if (!c.isAlive) continue
                val dx = c.x - a.x
                val dy = c.y - a.y
                val minD = a.radius + c.radius
                val d2 = dx * dx + dy * dy
                if (d2 > 0.01f && d2 < minD * minD) {
                    val d = sqrt(d2)
                    val push = (minD - d) * 0.5f
                    val nx = dx / d
                    val ny = dy / d
                    a.x -= nx * push
                    a.y -= ny * push
                    c.x += nx * push
                    c.y += ny * push
                }
            }
        }
    }
}
