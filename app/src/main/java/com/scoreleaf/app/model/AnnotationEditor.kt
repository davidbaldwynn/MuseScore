package com.scoreleaf.app.model

import kotlin.math.sqrt

object AnnotationEditor {
    fun add(history: AnnotationHistory, stroke: InkStroke): AnnotationHistory =
        history.copy(strokes = history.strokes + stroke, redo = emptyList())

    fun undo(history: AnnotationHistory): AnnotationHistory {
        val removed = history.strokes.lastOrNull() ?: return history
        return AnnotationHistory(history.strokes.dropLast(1), history.redo + removed)
    }

    fun redo(history: AnnotationHistory): AnnotationHistory {
        val restored = history.redo.lastOrNull() ?: return history
        return AnnotationHistory(history.strokes + restored, history.redo.dropLast(1))
    }

    fun eraseNearest(
        history: AnnotationHistory,
        point: InkPoint,
        radius: Float
    ): AnnotationHistory {
        require(radius >= 0f) { "Eraser radius must not be negative" }
        val match = history.strokes.mapIndexed { index, stroke ->
            index to distance(stroke.points, point)
        }.filter { it.second <= radius }.minByOrNull { it.second }?.first ?: return history
        val removed = history.strokes[match]
        return AnnotationHistory(
            strokes = history.strokes.filterIndexed { index, _ -> index != match },
            redo = history.redo + removed
        )
    }

    private fun distance(points: List<InkPoint>, target: InkPoint): Float {
        if (points.isEmpty()) return Float.POSITIVE_INFINITY
        if (points.size == 1) return pointDistance(points.first(), target)
        return points.zipWithNext().minOf { (start, end) ->
            segmentDistance(start, end, target)
        }
    }

    private fun segmentDistance(start: InkPoint, end: InkPoint, point: InkPoint): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return pointDistance(start, point)
        val projection = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared)
            .coerceIn(0f, 1f)
        return pointDistance(InkPoint(start.x + projection * dx, start.y + projection * dy), point)
    }

    private fun pointDistance(a: InkPoint, b: InkPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
