package com.scoreleaf.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationEditorTest {
    private val first = InkStroke(
        color = 0xff000000,
        width = 3f,
        points = listOf(InkPoint(.1f, .1f), InkPoint(.4f, .1f)),
        tool = AnnotationTool.PEN
    )
    private val second = InkStroke(
        color = 0x66ffff00,
        width = 18f,
        points = listOf(InkPoint(.1f, .7f), InkPoint(.5f, .7f)),
        tool = AnnotationTool.HIGHLIGHTER,
        layerId = "markup"
    )

    @Test fun undoAndRedoAreLossless() {
        val original = AnnotationHistory(listOf(first, second))
        val undone = AnnotationEditor.undo(original)
        assertEquals(listOf(first), undone.strokes)
        assertEquals(listOf(second), undone.redo)
        assertEquals(original, AnnotationEditor.redo(undone))
    }

    @Test fun addingAfterUndoClearsRedoHistory() {
        val undone = AnnotationEditor.undo(AnnotationHistory(listOf(first, second)))
        val replacement = first.copy(points = listOf(InkPoint(.2f, .2f), InkPoint(.8f, .2f)))
        val changed = AnnotationEditor.add(undone, replacement)

        assertEquals(listOf(first, replacement), changed.strokes)
        assertTrue(changed.redo.isEmpty())
    }

    @Test fun eraserRemovesOnlyNearestStrokeInsideRadius() {
        val history = AnnotationHistory(listOf(first, second))
        val erased = AnnotationEditor.eraseNearest(history, InkPoint(.25f, .12f), .05f)
        assertEquals(listOf(second), erased.strokes)
        assertEquals(listOf(first), erased.redo)

        assertEquals(history, AnnotationEditor.eraseNearest(history, InkPoint(.9f, .4f), .03f))
    }

    @Test fun annotationMetadataDefaultsSupportLegacyInk() {
        val legacy = InkStroke(0xff000000, 3f, listOf(InkPoint(0f, 0f), InkPoint(1f, 1f)))
        assertEquals(AnnotationTool.PEN, legacy.tool)
        assertEquals("default", legacy.layerId)
    }
}
