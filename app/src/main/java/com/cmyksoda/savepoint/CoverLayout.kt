package com.cmyksoda.savepoint

data class CoverSize(val width: Float, val height: Float)

fun coverAspectRatio(system: String, imageWidth: Int? = null, imageHeight: Int? = null): Float {
    if (imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0)
        return imageWidth.toFloat() / imageHeight
    return when (system) {
        "Game Boy",
        "Game Boy Color",
        "Game Boy Advance",
        "Nintendo DS",
        "Nintendo 3DS" -> 1f
        "SNES",
        "Nintendo 64" -> 1.4f
        "Nintendo Switch",
        "Nintendo Switch 2" -> 100f / 163f
        else -> 2f / 3f
    }
}

fun fitCover(aspectRatio: Float, bounds: CoverSize): CoverSize {
    require(aspectRatio.isFinite() && aspectRatio > 0)
    require(bounds.width.isFinite() && bounds.width > 0)
    require(bounds.height.isFinite() && bounds.height > 0)
    val width = minOf(bounds.width, bounds.height * aspectRatio)
    return CoverSize(width, width / aspectRatio)
}
