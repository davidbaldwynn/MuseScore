package com.scoreleaf.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test fun androidAndLegacyComposeColorsResolveToArgb() {
        assertEquals(0xffff0000.toInt(), annotationArgb(-65536L))
        assertEquals(0xffff0000.toInt(), annotationArgb(0xffff000000000000UL.toLong()))
    }

    @Test fun lassoSelectsContainedElementsAndMovesThemWithoutLosingMetadata() {
        val shape = InkStroke(
            color = 0xff123456,
            width = 5f,
            points = listOf(InkPoint(.2f, .2f), InkPoint(.4f, .4f)),
            tool = AnnotationTool.RECTANGLE,
            layerId = "analysis",
            text = "phrase",
            pressures = listOf(.4f, .8f)
        )
        val outside = first.copy(points = listOf(InkPoint(.7f, .7f), InkPoint(.9f, .9f)))
        val history = AnnotationHistory(listOf(shape, outside))

        val selected = AnnotationEditor.selectInRect(history, InkPoint(.1f, .1f), InkPoint(.5f, .5f))
        assertEquals(setOf(shape.id), selected)
        val moved = AnnotationEditor.move(history, selected, .1f, -.1f)

        assertEquals(listOf(InkPoint(.3f, .1f), InkPoint(.5f, .3f)), moved.strokes.first().points)
        assertEquals(shape.copy(points = listOf(InkPoint(.3f, .1f), InkPoint(.5f, .3f))), moved.strokes.first())
        assertTrue(moved.redo.isEmpty())
    }

    @Test fun lassoDeleteIsUndoableAsOneOperation() {
        val selected = setOf(first.id, second.id)
        val deleted = AnnotationEditor.delete(AnnotationHistory(listOf(first, second)), selected)
        assertTrue(deleted.strokes.isEmpty())
        assertEquals(listOf(first, second), deleted.redo)
        assertEquals(listOf(first, second), AnnotationEditor.redoAll(deleted).strokes)
    }

    @Test fun namedLayersCanBeCreatedRenamedHiddenAndLocked() {
        val initial = listOf(AnnotationLayer.DEFAULT)
        val added = AnnotationLayers.add(initial, "Teacher notes")
        assertEquals(2, added.size)
        val id = added.last().id
        val renamed = AnnotationLayers.rename(added, id, "Bowings")
        val hidden = AnnotationLayers.setVisible(renamed, id, false)
        val locked = AnnotationLayers.setLocked(hidden, id, true)

        assertEquals("Bowings", locked.last().name)
        assertFalse(locked.last().visible)
        assertTrue(locked.last().locked)
        assertEquals(initial, AnnotationLayers.add(initial, "   "))
    }
}
