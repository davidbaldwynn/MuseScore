package com.scoreleaf.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRenderCacheTest {
    @Test fun leastRecentlyUsedPageIsEvictedAtCapacity() {
        val cache = PageRenderCache<String>(capacity = 2)
        cache.put(0, "zero")
        cache.put(1, "one")
        cache[0]
        cache.put(2, "two")

        assertTrue(0 in cache)
        assertFalse(1 in cache)
        assertTrue(2 in cache)
    }

    @Test fun getOrPutDoesNotRenderCachedPagesAgain() {
        val cache = PageRenderCache<Any>(capacity = 3)
        var renders = 0
        val first = cache.getOrPut(4) { renders++; Any() }
        val second = cache.getOrPut(4) { renders++; Any() }

        assertSame(first, second)
        assertEquals(1, renders)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cacheRequiresPositiveCapacity() {
        PageRenderCache<String>(0)
    }
}
