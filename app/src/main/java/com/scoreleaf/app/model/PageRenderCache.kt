package com.scoreleaf.app.model

class PageRenderCache<T>(private val capacity: Int) {
    init {
        require(capacity > 0) { "Cache capacity must be positive" }
    }

    private val values = object : LinkedHashMap<Int, T>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, T>?): Boolean {
            return size > capacity
        }
    }

    @Synchronized operator fun get(page: Int): T? = values[page]

    @Synchronized fun put(page: Int, value: T) {
        values[page] = value
    }

    @Synchronized fun getOrPut(page: Int, render: () -> T): T {
        return values[page] ?: render().also { values[page] = it }
    }

    @Synchronized operator fun contains(page: Int): Boolean = values.containsKey(page)

    @Synchronized fun clear() = values.clear()
}
