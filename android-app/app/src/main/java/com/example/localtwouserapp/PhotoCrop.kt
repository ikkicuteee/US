package com.example.localtwouserapp

data class CropBox(val left: Int, val top: Int, val size: Int)
object PhotoCrop {
    fun bounds(width: Int, height: Int, x: Float, y: Float, zoom: Float): CropBox {
        require(width > 0 && height > 0 && x.isFinite() && y.isFinite() && zoom.isFinite())
        val size = (minOf(width, height) / zoom.coerceIn(1f, 3f)).toInt().coerceAtLeast(1)
        return CropBox(((width - size) * x.coerceIn(0f, 1f)).toInt(), ((height - size) * y.coerceIn(0f, 1f)).toInt(), size)
    }
}
