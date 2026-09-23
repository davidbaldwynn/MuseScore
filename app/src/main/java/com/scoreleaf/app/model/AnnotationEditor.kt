package com.scoreleaf.app.model

import kotlin.math.sqrt

object AnnotationEditor {
    fun add(history: AnnotationHistory, stroke: InkStroke): AnnotationHistory =
        history.copy(
            strokes = history.strokes + stroke,
            redo = emptyList(),
            redoSnapshots = emptyList()
        )

    fun undo(history: AnnotationHistory): AnnotationHistory {
        history.undoSnapshots.lastOrNull()?.let { previous ->
            return history.copy(
                strokes = previous,
                redo = emptyList(),
                undoSnapshots = history.undoSnapshots.dropLast(1),
                redoSnapshots = history.redoSnapshots + listOf(history.strokes)
            )
        }
        val removed = history.strokes.lastOrNull() ?: return history
        return history.copy(strokes = history.strokes.dropLast(1), redo = history.redo + removed)
    }

    fun redo(history: AnnotationHistory): AnnotationHistory {
        history.redoSnapshots.lastOrNull()?.let { restored ->
            return history.copy(
                strokes = restored,
                undoSnapshots = history.undoSnapshots + listOf(history.strokes),
                redoSnapshots = history.redoSnapshots.dropLast(1)
            )
        }
        val restored = history.redo.lastOrNull() ?: return history
        return history.copy(strokes = history.strokes + restored, redo = history.redo.dropLast(1))
    }

    fun redoAll(history: AnnotationHistory): AnnotationHistory = when {
        history.redoSnapshots.isNotEmpty() -> history.copy(
            strokes = history.redoSnapshots.last(),
            undoSnapshots = history.undoSnapshots + listOf(history.strokes),
            redoSnapshots = history.redoSnapshots.dropLast(1),
            redo = emptyList()
        )
        history.redo.isNotEmpty() -> history.copy(strokes = history.strokes + history.redo, redo = emptyList())
        else -> history
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
            redo = history.redo + removed,
            undoSnapshots = history.undoSnapshots,
            redoSnapshots = history.redoSnapshots
        )
    }

    fun selectInRect(history: AnnotationHistory, first: InkPoint, second: InkPoint): Set<String> {
        val minX = minOf(first.x, second.x)
        val maxX = maxOf(first.x, second.x)
        val minY = minOf(first.y, second.y)
        val maxY = maxOf(first.y, second.y)
        return history.strokes.filter { stroke ->
            stroke.points.isNotEmpty() && stroke.points.all { it.x in minX..maxX && it.y in minY..maxY }
        }.mapTo(linkedSetOf()) { it.id }
    }

    fun move(history: AnnotationHistory, ids: Set<String>, dx: Float, dy: Float): AnnotationHistory {
        if (ids.isEmpty()) return history
        val moved = history.strokes.map { stroke ->
            if (stroke.id !in ids) stroke else stroke.copy(points = stroke.points.map {
                InkPoint((it.x + dx).coerceIn(0f, 1f), (it.y + dy).coerceIn(0f, 1f))
            })
        }
        if (moved == history.strokes) return history
        return history.copy(
            strokes = moved,
            redo = emptyList(),
            undoSnapshots = history.undoSnapshots + listOf(history.strokes),
            redoSnapshots = emptyList()
        )
    }

    fun delete(history: AnnotationHistory, ids: Set<String>): AnnotationHistory {
        val removed = history.strokes.filter { it.id in ids }
        if (removed.isEmpty()) return history
        return history.copy(
            strokes = history.strokes.filterNot { it.id in ids },
            redo = removed,
            undoSnapshots = history.undoSnapshots + listOf(history.strokes),
            redoSnapshots = emptyList()
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

object AnnotationLayers {
    fun add(layers: List<AnnotationLayer>, name: String): List<AnnotationLayer> {
        val clean = name.trim()
        return if (clean.isBlank()) layers else layers + AnnotationLayer(name = clean)
    }

    fun rename(layers: List<AnnotationLayer>, id: String, name: String): List<AnnotationLayer> {
        val clean = name.trim()
        if (clean.isBlank()) return layers
        return layers.map { if (it.id == id) it.copy(name = clean) else it }
    }

    fun setVisible(layers: List<AnnotationLayer>, id: String, visible: Boolean) =
        layers.map { if (it.id == id) it.copy(visible = visible) else it }

    fun setLocked(layers: List<AnnotationLayer>, id: String, locked: Boolean) =
        layers.map { if (it.id == id) it.copy(locked = locked) else it }
}
