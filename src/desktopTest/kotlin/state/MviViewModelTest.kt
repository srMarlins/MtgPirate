package state

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.srmarlins.mtgpirate.MtgPirateDatabase
import database.CatalogStore
import database.Database
import database.DatabaseDriverFactory
import database.ImportsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import model.*
import java.util.*
import kotlin.test.*

/**
 * Tests for MVI ViewModel with database integration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {

    private lateinit var testScope: TestScope
    private lateinit var database: Database
    private lateinit var catalogStore: CatalogStore
    private lateinit var importsStore: ImportsStore
    private lateinit var platformServices: TestMviPlatformServices
    private lateinit var viewModel: MviViewModel

    @BeforeTest
    fun setup() {
        // Create test coroutine scope
        testScope = TestScope(UnconfinedTestDispatcher())

        // Create in-memory database for testing
        val driver = createInMemoryDriver()
        database = Database(object : DatabaseDriverFactory() {
            override fun createDriver() = driver
        })

        catalogStore = CatalogStore(database)
        importsStore = ImportsStore(database)
        platformServices = TestMviPlatformServices(database)

        viewModel = MviViewModel(
            scope = testScope,
            database = database,
            catalogStore = catalogStore,
            importsStore = importsStore,
            platformServices = platformServices
        )
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    private fun createInMemoryDriver(): SqlDriver {
        return JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            properties = Properties(),
            schema = MtgPirateDatabase.Schema
        )
    }

    @Test
    fun `test initial state`() = testScope.runTest {
        val state = viewModel.viewState.value

        assertNull(state.catalog)
        assertEquals("", state.deckText)
        assertTrue(state.deckEntries.isEmpty())
        assertTrue(state.matches.isEmpty())
        assertFalse(state.loadingCatalog)
    }

    @Test
    fun `test UpdateDeckText intent updates state`() = testScope.runTest {
        viewModel.processIntent(ViewIntent.UpdateDeckText("4 Lightning Bolt"))
        advanceUntilIdle()

        assertEquals("4 Lightning Bolt", viewModel.viewState.value.deckText)
    }

    @Test
    fun `test LoadCatalog intent stores catalog in database`() = testScope.runTest {
        // Setup mock catalog
        platformServices.mockCatalog = createMockCatalog()

        viewModel.processIntent(ViewIntent.LoadCatalog(force = true))
        advanceUntilIdle()

        // Verify catalog is in database and state
        val state = viewModel.viewState.value
        assertNotNull(state.catalog)
        assertEquals(2, state.catalog?.variants?.size)
        assertEquals("Lightning Bolt", state.catalog?.variants?.get(0)?.nameOriginal)
    }

    @Test
    fun `test catalog persists in database`() = testScope.runTest {
        // Insert catalog
        val catalog = createMockCatalog()
        catalogStore.replaceCatalog(catalog)
        advanceUntilIdle()

        // Verify it's accessible via flow
        val storedCatalog = database.observeCatalog().first()
        assertEquals(2, storedCatalog.variants.size)
    }

    @Test
    fun `test ParseDeck intent parses deck entries`() = testScope.runTest {
        viewModel.processIntent(ViewIntent.UpdateDeckText("4 Lightning Bolt\n1 Black Lotus"))
        viewModel.processIntent(ViewIntent.ParseDeck)
        advanceUntilIdle()

        val state = viewModel.viewState.value
        assertEquals(2, state.deckEntries.size)
        assertEquals("Lightning Bolt", state.deckEntries[0].cardName)
        assertEquals(4, state.deckEntries[0].qty)
    }

    @Test
    fun `test ParseAndMatch intent matches cards against catalog`() = testScope.runTest {
        // Setup catalog
        platformServices.mockCatalog = createMockCatalog()
        viewModel.processIntent(ViewIntent.LoadCatalog(force = true))
        advanceUntilIdle()

        // Parse and match
        viewModel.processIntent(ViewIntent.UpdateDeckText("1 Lightning Bolt"))
        viewModel.processIntent(ViewIntent.ParseAndMatch)
        advanceUntilIdle()

        val state = viewModel.viewState.value
        assertEquals(1, state.deckEntries.size)
        assertEquals(1, state.matches.size)
        assertEquals("Lightning Bolt", state.matches[0].deckEntry.cardName)
        assertTrue(state.showResultsWindow)
    }

    @Test
    fun `test ResolveCandidate intent updates match`() = testScope.runTest {
        // Setup catalog and parse deck
        platformServices.mockCatalog = createMockCatalog()
        viewModel.processIntent(ViewIntent.LoadCatalog(force = true))
        advanceUntilIdle()

        viewModel.processIntent(ViewIntent.UpdateDeckText("1 Lightning Bolt"))
        viewModel.processIntent(ViewIntent.ParseAndMatch)
        advanceUntilIdle()

        // Resolve candidate
        val variant = viewModel.viewState.value.catalog!!.variants[0]
        viewModel.processIntent(ViewIntent.ResolveCandidate(0, variant))
        advanceUntilIdle()

        val state = viewModel.viewState.value
        assertEquals(MatchStatus.MANUAL_SELECTED, state.matches[0].status)
        assertEquals(variant, state.matches[0].selectedVariant)
    }

    @Test
    fun `test ToggleIncludeSideboard updates preferences`() = testScope.runTest {
        viewModel.processIntent(ViewIntent.ToggleIncludeSideboard(false))
        advanceUntilIdle()

        val preferences = database.observePreferences().first()
        assertNotNull(preferences)
        assertFalse(preferences!!.includeSideboard)
    }

    @Test
    fun `test SaveCurrentImport stores import in database`() = testScope.runTest {
        viewModel.processIntent(ViewIntent.UpdateDeckText("4 Lightning Bolt"))
        viewModel.processIntent(ViewIntent.SaveCurrentImport("Test Import"))
        advanceUntilIdle()

        val imports = database.observeSavedImports().first()
        assertTrue(imports.isNotEmpty())
        assertEquals("Test Import", imports[0].name)
    }

    @Test
    fun `test LoadSavedImport loads import into state`() = testScope.runTest {
        // Save an import
        viewModel.processIntent(ViewIntent.UpdateDeckText("4 Lightning Bolt"))
        viewModel.processIntent(ViewIntent.SaveCurrentImport("Test Import"))
        advanceUntilIdle()

        val importId = database.observeSavedImports().first()[0].id

        // Clear deck text
        viewModel.processIntent(ViewIntent.UpdateDeckText(""))
        advanceUntilIdle()

        // Load import
        viewModel.processIntent(ViewIntent.LoadSavedImport(importId))
        advanceUntilIdle()

        assertEquals("4 Lightning Bolt", viewModel.viewState.value.deckText)
    }

    @Test
    fun `test DeleteSavedImport removes import from database`() = testScope.runTest {
        // Save an import
        viewModel.processIntent(ViewIntent.UpdateDeckText("4 Lightning Bolt"))
        viewModel.processIntent(ViewIntent.SaveCurrentImport("Test Import"))
        advanceUntilIdle()

        val importId = database.observeSavedImports().first()[0].id

        // Delete import
        viewModel.processIntent(ViewIntent.DeleteSavedImport(importId))
        advanceUntilIdle()

        val imports = database.observeSavedImports().first()
        assertTrue(imports.isEmpty())
    }

    @Test
    fun `test ToggleTheme updates state`() = testScope.runTest {
        val initialTheme = viewModel.viewState.value.isDarkTheme

        viewModel.processIntent(ViewIntent.ToggleTheme)
        advanceUntilIdle()

        assertEquals(!initialTheme, viewModel.viewState.value.isDarkTheme)
    }

    @Test
    fun `test CompleteWizardStep adds step to completed set`() = testScope.runTest {
        viewModel.processIntent(ViewIntent.CompleteWizardStep(1))
        advanceUntilIdle()

        assertTrue(viewModel.viewState.value.wizardCompletedSteps.contains(1))

        viewModel.processIntent(ViewIntent.CompleteWizardStep(2))
        advanceUntilIdle()

        val steps = viewModel.viewState.value.wizardCompletedSteps
        assertTrue(steps.contains(1))
        assertTrue(steps.contains(2))
    }

    @Test
    fun `test OpenResolve and CloseResolve update state`() = testScope.runTest {
        viewModel.processIntent(ViewIntent.OpenResolve(5))
        advanceUntilIdle()

        assertEquals(5, viewModel.viewState.value.showCandidatesFor)

        viewModel.processIntent(ViewIntent.CloseResolve)
        advanceUntilIdle()

        assertNull(viewModel.viewState.value.showCandidatesFor)
    }

    @Test
    fun `test window visibility intents`() = testScope.runTest {
        viewModel.processIntent(ViewIntent.SetShowPreferences(true))
        advanceUntilIdle()
        assertTrue(viewModel.viewState.value.showPreferences)

        viewModel.processIntent(ViewIntent.SetShowCatalogWindow(true))
        advanceUntilIdle()
        assertTrue(viewModel.viewState.value.showCatalogWindow)

        viewModel.processIntent(ViewIntent.SetShowMatchesWindow(true))
        advanceUntilIdle()
        assertTrue(viewModel.viewState.value.showMatchesWindow)

        viewModel.processIntent(ViewIntent.SetShowSavedImportsWindow(true))
        advanceUntilIdle()
        assertTrue(viewModel.viewState.value.showSavedImportsWindow)
    }

    @Test
    fun `test SavePreferences updates database`() = testScope.runTest {
        viewModel.processIntent(
            ViewIntent.SavePreferences(
                variantPriority = listOf("Foil", "Regular"),
                setPriority = listOf("SET1", "SET2"),
                fuzzyEnabled = false
            )
        )
        advanceUntilIdle()

        val preferences = database.observePreferences().first()
        assertNotNull(preferences)
        assertEquals(listOf("Foil", "Regular"), preferences!!.variantPriority)
        assertEquals(listOf("SET1", "SET2"), preferences.setPriority)
        assertFalse(preferences.fuzzyEnabled)
    }

    @Test
    fun `test UpdateVariantPriority updates preferences`() = testScope.runTest {
        viewModel.processIntent(ViewIntent.UpdateVariantPriority(listOf("Holo", "Foil", "Regular")))
        advanceUntilIdle()

        val preferences = database.observePreferences().first()
        assertNotNull(preferences)
        assertEquals(listOf("Holo", "Foil", "Regular"), preferences!!.variantPriority)
    }

    @Test
    fun `test database flows trigger state updates`() = testScope.runTest {
        // Directly insert into database
        val catalog = createMockCatalog()
        catalogStore.replaceCatalog(catalog)
        advanceUntilIdle()

        // State should automatically update via flow
        val state = viewModel.viewState.value
        assertNotNull(state.catalog)
        assertEquals(2, state.catalog?.variants?.size)
    }

    private fun createMockCatalog(): Catalog {
        return Catalog(
            variants = listOf(
                CardVariant(
                    nameOriginal = "Lightning Bolt",
                    nameNormalized = "lightning bolt",
                    setCode = "LEA",
                    sku = "SKU001",
                    variantType = "Regular",
                    priceInCents = 100,
                    collectorNumber = "1",
                    imageUrl = null
                ),
                CardVariant(
                    nameOriginal = "Black Lotus",
                    nameNormalized = "black lotus",
                    setCode = "LEA",
                    sku = "SKU002",
                    variantType = "Regular",
                    priceInCents = 100000,
                    collectorNumber = "2",
                    imageUrl = null
                )
            )
        )
    }
}

/**
 * Test implementation of MviPlatformServices.
 */
class TestMviPlatformServices(private val database: Database) : MviPlatformServices {
    var mockCatalog: Catalog? = null
    private val logs = mutableListOf<LogEntry>()

    override suspend fun fetchCatalogFromRemote(log: (String) -> Unit): Catalog? {
        log("Fetching mock catalog")
        return mockCatalog
    }

    override suspend fun updatePreferences(update: (Preferences) -> Preferences) {
        val currentPrefs = database.observePreferences().first() ?: Preferences()
        val newPrefs = update(currentPrefs)
        database.insertPreferences(newPrefs)
    }

    override suspend fun addLog(log: LogEntry) {
        logs.add(log)
        database.insertLog(log)
    }

    override suspend fun exportCsv(matches: List<DeckEntryMatch>, onComplete: (String) -> Unit) {
        onComplete("/tmp/test-export.csv")
    }

    override suspend fun copyToClipboard(text: String) {
        // Mock implementation for tests
    }

    fun getLogs(): List<LogEntry> = logs
}
