package com.gamevault.unified.data.repository

import com.gamevault.unified.data.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

/** Install sizes are only in the full payload, so refreshes must not erase them. */
class GameRepositoryMergeTest {

    private fun game(id: String, title: String = id) = Game(id = id, title = title)

    @Test
    fun `fresh games keep their order when nothing is known`() {
        val fresh = listOf(game("a"), game("b"))
        assertEquals(fresh, GameRepository.withKnownSizes(fresh, emptyMap()))
    }

    @Test
    fun `known sizes are merged back by id`() {
        val fresh = listOf(game("a"), game("b"))
        val merged = GameRepository.withKnownSizes(fresh, mapOf("b" to 2L * 1024 * 1024 * 1024))

        assertEquals(null, merged[0].installSize)
        assertEquals(2L * 1024 * 1024 * 1024, merged[1].installSize)
    }

    @Test
    fun `a game the snapshot never saw stays sizeless`() {
        val merged = GameRepository.withKnownSizes(listOf(game("new")), mapOf("old" to 1024L))
        assertEquals(null, merged.single().installSize)
    }
}
