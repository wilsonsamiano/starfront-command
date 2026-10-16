package com.wilson.starfront

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/** StarCraft-style pixel-art sprites, embedded as base64 so the repo stays text-only.
 *  Each sprite lives in its own SpriteData file, split into <32 KB chunks:
 *  a single JVM string constant cannot exceed 64 KB. */
object Sprites {

    private val chunks: Map<String, List<String>> = mapOf(
        "marine" to SPRITE_MARINE,
        "ranger" to SPRITE_RANGER,
        "constructor" to SPRITE_CONSTRUCTOR,
        "bulwark" to SPRITE_BULWARK,
        "skitterling" to SPRITE_SKITTERLING,
        "spiker" to SPRITE_SPIKER,
        "ravager" to SPRITE_RAVAGER,
        "hq" to SPRITE_HQ,
        "turret" to SPRITE_TURRET,
        "barracks" to SPRITE_BARRACKS,
    )

    private val cache = mutableMapOf<String, Bitmap>()

    /** Decoded sprite bitmap, cached after first use. */
    fun get(name: String): Bitmap = cache.getOrPut(name) {
        val b64 = chunks[name]?.joinToString("")
            ?: throw IllegalArgumentException("unknown sprite: $name")
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("cannot decode sprite: $name")
    }
}
