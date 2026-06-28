package com.filtertube.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelFilteringTest {
    @Test
    fun filtersChannelsByGenderWhenSelected() {
        val channels = listOf(
            Channel("1", "A", "music", ""),
            Channel("2", "B", "music", "female"),
            Channel("3", "C", "music", "male"),
        )

        val visible = channels.forLevel(2, "female")

        assertEquals(2, visible.size)
        assertEquals(listOf("A", "B"), visible.map { it.name })
    }
}
