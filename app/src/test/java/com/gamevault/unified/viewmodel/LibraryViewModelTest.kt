package com.gamevault.unified.viewmodel

import com.gamevault.unified.data.local.CachedLibrary
import com.gamevault.unified.data.local.ServerStorage
import com.gamevault.unified.data.local.UiPrefs
import com.gamevault.unified.data.local.UiViewPrefs
import com.gamevault.unified.data.model.Game
import com.gamevault.unified.data.model.ServerConfig
import com.gamevault.unified.data.model.ServerProfile
import com.gamevault.unified.data.repository.GameRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ViewModel behaviour that does not need a real PC: the cover circuit
 * breaker, filter handling, paging and profile switching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var repository: GameRepository
    private lateinit var storage: ServerStorage
    private lateinit var uiPrefs: UiPrefs

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        storage = mockk(relaxed = true)
        uiPrefs = mockk(relaxed = true)

        coEvery { uiPrefs.snapshot() } returns UiViewPrefs()
        coEvery { repository.artlessIds() } returns emptySet()
        coEvery { repository.cached() } returns null
        coEvery { storage.snapshot() } returns null
        coEvery { storage.snapshotProfile() } returns null
        coEvery { storage.allProfiles() } returns emptyList()
        every { repository.formatTime(any()) } returns "now"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): LibraryViewModel =
        LibraryViewModel(repository, storage, uiPrefs, null)

    @Test
    fun `a 404 marks the game as artless`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.noteCoverResult("g1", success = false, httpCode = 404)
        advanceUntilIdle()

        assertTrue("g1" in vm.ui.value.artlessIds)
        coVerify { repository.markArtless(setOf("g1")) }
    }

    @Test
    fun `a 404 for a known artless game is not stored twice`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.noteCoverResult("g1", success = false, httpCode = 404)
        advanceUntilIdle()
        vm.noteCoverResult("g1", success = false, httpCode = 404)
        advanceUntilIdle()

        assertEquals(1, vm.ui.value.artlessIds.size)
    }

    @Test
    fun `a 401 does not trip the breaker`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        repeat(30) { vm.noteCoverResult("g$it", success = false, httpCode = 401) }
        advanceUntilIdle()

        assertTrue(vm.ui.value.coverFailures.isEmpty())
        assertEquals(0L, vm.ui.value.imagesPausedUntil)
    }

    @Test
    fun `a successful cover clears the failure streak`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        repeat(5) { vm.noteCoverResult("g$it", success = false, httpCode = null) }
        advanceUntilIdle()
        assertEquals(5, vm.ui.value.coverFailures.size)

        vm.noteCoverResult("g0", success = true, httpCode = null)
        advanceUntilIdle()
        assertTrue(vm.ui.value.coverFailures.isEmpty())
    }

    @Test
    fun `sustained failures pause the image loading`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        repeat(24) { vm.noteCoverResult("g$it", success = false, httpCode = null) }
        // No advanceUntilIdle() here: the breaker auto-resumes after 5 s and
        // the virtual clock would jump straight past it.
        assertTrue("the breaker should have tripped", vm.ui.value.imagesPausedUntil > 0)
        assertTrue(vm.ui.value.coverFailures.isEmpty())
    }

    @Test
    fun `a few failures do not pause the image loading`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        repeat(10) { vm.noteCoverResult("g$it", success = false, httpCode = null) }
        advanceUntilIdle()

        assertEquals(0L, vm.ui.value.imagesPausedUntil)
        assertEquals(10, vm.ui.value.coverFailures.size)
    }

    @Test
    fun `clear filters keeps the query and the sort`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateFilters(
            GameFilters(
                query = "hades",
                sort = SortMode.PLAYTIME,
                selectedSources = setOf("Steam"),
                installedOnly = true
            )
        )
        vm.clearFilters()

        val f = vm.ui.value.filters
        assertEquals("hades", f.query)
        assertEquals(SortMode.PLAYTIME, f.sort)
        assertTrue(f.selectedSources.isEmpty())
        assertFalse(f.installedOnly)
    }

    @Test
    fun `the page is clamped to the available range`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setPageSize(2)
        vm.setPage(99)
        assertEquals(vm.ui.value.totalPages - 1, vm.ui.value.page)
        assertTrue(vm.ui.value.page >= 0)
    }

    @Test
    fun `switching a profile loads that profile's library`() = runTest(dispatcher) {
        val first = ServerProfile(id = "p1", name = "First", baseUrl = "http://first:19821", token = "t1")
        val second = ServerProfile(id = "p2", name = "Second", baseUrl = "http://second:19821", token = "t2")
        val firstLibrary = CachedLibrary(
            savedAt = 1_700_000_000_000L,
            games = listOf(Game(id = "g1", title = "From the first PC")),
            sources = listOf("Steam")
        )
        val secondLibrary = CachedLibrary(
            savedAt = 1_700_000_100_000L,
            games = listOf(Game(id = "g2", title = "From the second PC")),
            sources = listOf("GOG")
        )

        coEvery { storage.snapshotProfile() } returns first
        coEvery { storage.allProfiles() } returns listOf(first, second)
        coEvery { storage.snapshot() } returns ServerConfig("http://first:19821", "t1")
        coEvery { repository.cached() } returns firstLibrary
        coEvery { repository.refreshAndCache() } returns firstLibrary

        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("From the first PC"), vm.ui.value.games.map { it.title })

        // The other PC becomes active: its own snapshot has to be loaded.
        coEvery { storage.snapshotProfile() } returns second
        coEvery { storage.snapshot() } returns ServerConfig("http://second:19821", "t2")
        coEvery { repository.cached() } returns secondLibrary
        coEvery { repository.refreshAndCache() } returns secondLibrary

        vm.switchProfile("p2")
        advanceUntilIdle()

        assertEquals("p2", vm.ui.value.activeProfileId)
        assertEquals(listOf("From the second PC"), vm.ui.value.games.map { it.title })
        coVerify { storage.activate("p2") }
    }

    @Test
    fun `a failing connection reports a friendly error`() = runTest(dispatcher) {
        coEvery { repository.testConnection(any()) } throws
            IllegalStateException("Failed to connect to /192.168.1.50:19821")

        val vm = viewModel()
        advanceUntilIdle()

        vm.saveServer("http://192.168.1.50:19821", "token")
        advanceUntilIdle()

        val error = vm.ui.value.error
        assertNotNull(error)
        assertTrue(error!!.contains("Connection refused"))
        assertNull(vm.ui.value.connectionTest)
    }

    @Test
    fun `a successful connection saves the profile`() = runTest(dispatcher) {
        val profile = ServerProfile(id = "p1", name = "", baseUrl = "http://pc:19821", token = "tok")
        coEvery { repository.testConnection(any()) } returns "1.6.0"
        coEvery { storage.save(any(), any()) } returns profile
        coEvery { storage.allProfiles() } returns listOf(profile)
        coEvery { storage.snapshotProfile() } returns profile
        coEvery { storage.snapshot() } returns ServerConfig("http://pc:19821", "tok")

        val vm = viewModel()
        advanceUntilIdle()

        vm.saveServer("http://pc:19821", "tok")
        advanceUntilIdle()

        assertEquals("p1", vm.ui.value.activeProfileId)
        assertEquals("Connected (Playnite 1.6.0)", vm.ui.value.connectionTest)
        coVerify { storage.save(any(), any()) }
    }
}
