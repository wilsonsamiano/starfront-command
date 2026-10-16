package com.wilson.starfront

enum class Team { PLAYER, ENEMY }

/** Anything that can take damage and be targeted. */
interface Damageable {
    var hp: Float
    val maxHp: Float
    var x: Float
    var y: Float
    val radius: Float
    val team: Team
    val isAlive: Boolean
        get() = hp > 0f
}

enum class UnitKind(val displayName: String, val glyph: String) {
    CONSTRUCTOR("Constructor", "C"),
    MILITIA("Militia", "M"),
    RANGER("Ranger", "R"),
    BULWARK("Bulwark", "B"),
    SKITTERLING("Skitterling", "s"),
    SPIKER("Spiker", "p"),
    RAVAGER("Ravager", "V")
}

data class UnitStats(
    val maxHp: Float,
    val damage: Float,
    val range: Float,
    val speed: Float,        // world units per second
    val attackPeriod: Float, // seconds between attacks
    val logistics: Int,
    val buildTime: Float,    // seconds to train
    val radius: Float,
    val ranged: Boolean,
    val aggroRange: Float    // 0 = never auto-acquires (Constructor)
)

enum class BuildingKind(val displayName: String, val glyph: String) {
    COMMAND_CORE("Command Core", "HQ"),
    MG_TURRET("MG Turret", "T"),
    BARRACKS("Barracks", "B")
}

data class BuildingStats(
    val maxHp: Float,
    val damage: Float,
    val range: Float,
    val attackPeriod: Float,
    val radius: Float,
    val buildTime: Float, // seconds to construct
    val logistics: Int
)

object Stats {
    val units: Map<UnitKind, UnitStats> = mapOf(
        UnitKind.CONSTRUCTOR to UnitStats(60f, 5f, 40f, 170f, 1.0f, 1, 4f, 16f, false, 0f),
        UnitKind.MILITIA to UnitStats(110f, 12f, 48f, 180f, 0.9f, 1, 6f, 15f, false, 280f),
        UnitKind.RANGER to UnitStats(85f, 15f, 230f, 175f, 1.1f, 1, 8f, 15f, true, 320f),
        UnitKind.BULWARK to UnitStats(340f, 48f, 270f, 85f, 2.4f, 3, 16f, 24f, true, 340f),
        UnitKind.SKITTERLING to UnitStats(50f, 8f, 38f, 210f, 0.8f, 0, 0f, 13f, false, 300f),
        UnitKind.SPIKER to UnitStats(95f, 13f, 210f, 160f, 1.2f, 0, 0f, 15f, true, 320f),
        UnitKind.RAVAGER to UnitStats(1600f, 65f, 95f, 115f, 1.6f, 0, 0f, 34f, false, 360f)
    )

    val buildings: Map<BuildingKind, BuildingStats> = mapOf(
        BuildingKind.COMMAND_CORE to BuildingStats(1600f, 0f, 0f, 0f, 48f, 0f, 0),
        BuildingKind.MG_TURRET to BuildingStats(420f, 17f, 270f, 0.65f, 22f, 10f, 4),
        BuildingKind.BARRACKS to BuildingStats(650f, 0f, 0f, 0f, 42f, 15f, 0)
    )
}

class Unit(
    val id: Int,
    val kind: UnitKind,
    override val team: Team,
    x0: Float,
    y0: Float
) : Damageable {
    val stats: UnitStats = Stats.units[kind] ?: throw IllegalStateException("no stats for $kind")
    override var hp: Float = stats.maxHp
    override val maxHp: Float = stats.maxHp
    override var x: Float = x0
    override var y: Float = y0
    override val radius: Float = stats.radius

    var destX: Float = x0
    var destY: Float = y0
    var attackMove: Boolean = false
    var cooldown: Float = 0f
    var target: Damageable? = null
    var waypointIndex: Int = 0 // enemies only: progress along the lane
    var selected: Boolean = false
    var facing: Float = 0f // radians, 0 = +x
    var flash: Float = 0f // muzzle-flash timer
}

class Building(
    val id: Int,
    val kind: BuildingKind,
    override val team: Team,
    x0: Float,
    y0: Float
) : Damageable {
    val stats: BuildingStats = Stats.buildings[kind] ?: throw IllegalStateException("no stats for $kind")
    override var hp: Float = stats.maxHp
    override val maxHp: Float = stats.maxHp
    override var x: Float = x0
    override var y: Float = y0
    override val radius: Float = stats.radius

    var construction: Float = if (kind == BuildingKind.COMMAND_CORE) 1f else 0f
    val complete: Boolean
        get() = construction >= 1f
    var cooldown: Float = 0f
    var rallyX: Float = x0
    var rallyY: Float = y0 + 90f
    val trainQueue: ArrayDeque<UnitKind> = ArrayDeque()
    var trainProgress: Float = 0f
    var selected: Boolean = false
    var turretAngle: Float = 0f // radians, 0 = +x
    var flash: Float = 0f // muzzle-flash timer
}

class Projectile(
    var x: Float,
    var y: Float,
    val target: Damageable,
    val speed: Float,
    val damage: Float,
    val team: Team,
    val color: Int
) {
    var dead: Boolean = false
    var px: Float = x
    var py: Float = y
}
