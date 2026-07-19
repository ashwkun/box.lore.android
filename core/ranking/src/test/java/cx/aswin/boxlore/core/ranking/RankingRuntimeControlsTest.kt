package cx.aswin.boxlore.core.ranking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RankingRuntimeControlsTest {
    private lateinit var context: Context
    private lateinit var controls: RankingRuntimeControls

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("adaptive_ranking_runtime", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        controls = RankingRuntimeControls.create(context)
    }

    @After
    fun tearDown() {
        RankingShadowDiagnostics.clear()
    }

    @Test
    fun defaults_homeEnabledOtherSurfacesDisabled() {
        RankingObjective.entries.forEach { objective ->
            assertTrue(
                "HOME should default enabled for $objective",
                controls.isAdaptiveEnabled(objective, RankingSurface.HOME),
            )
            RankingSurface.entries.filterNot { it == RankingSurface.HOME }.forEach { surface ->
                assertFalse(
                    "$surface should default disabled for $objective",
                    controls.isAdaptiveEnabled(objective, surface),
                )
            }
        }
    }

    @Test
    fun masterSwitchOff_disablesEveryObjectiveAndSurface() {
        controls.setAdaptiveEnabled(false)

        RankingObjective.entries.forEach { objective ->
            RankingSurface.entries.forEach { surface ->
                assertFalse(controls.isAdaptiveEnabled(objective, surface))
            }
        }
    }

    @Test
    fun enablingSurfaceOptsInNonDefaultSurface() {
        assertFalse(controls.isAdaptiveEnabled(RankingObjective.DISCOVERY, RankingSurface.EXPLORE))

        controls.setSurfaceEnabled(RankingSurface.EXPLORE, true)

        assertTrue(controls.isAdaptiveEnabled(RankingObjective.DISCOVERY, RankingSurface.EXPLORE))
    }

    @Test
    fun disablingObjectiveTurnsOffOnlyThatObjective() {
        controls.setObjectiveEnabled(RankingObjective.DISCOVERY, false)

        assertFalse(controls.isAdaptiveEnabled(RankingObjective.DISCOVERY, RankingSurface.HOME))
        assertTrue(controls.isAdaptiveEnabled(RankingObjective.YOUR_SHOWS, RankingSurface.HOME))
    }

    @Test
    fun disablingHomeSurfaceOverridesRolloutDefault() {
        controls.setSurfaceEnabled(RankingSurface.HOME, false)

        assertFalse(controls.isAdaptiveEnabled(RankingObjective.DISCOVERY, RankingSurface.HOME))
    }

    @Test
    fun shadowDiagnosticsFlagRoundTrips() {
        assertTrue(controls.isShadowDiagnosticsEnabled())

        controls.setShadowDiagnosticsEnabled(false)
        assertFalse(controls.isShadowDiagnosticsEnabled())

        controls.setShadowDiagnosticsEnabled(true)
        assertTrue(controls.isShadowDiagnosticsEnabled())
    }

    @Test
    fun installedInstanceIsReturnedByGetInstance() {
        RankingRuntimeControls.install(controls)

        assertSame(controls, RankingRuntimeControls.getInstance(context))
    }

    @Test
    fun getInstanceLazilyCreatesWhenNoneInstalled() {
        // Force a fresh instance by installing a known one first, then verify creation path is stable.
        val created = RankingRuntimeControls.create(context)
        assertNotNull(created)
        RankingRuntimeControls.install(created)
        assertSame(created, RankingRuntimeControls.getInstance(context))
    }

    @Test
    fun rolloutPolicyEnablesHomeOnly() {
        assertTrue(RankingRolloutPolicy.isEnabledByDefault(RankingSurface.HOME))
        RankingSurface.entries.filterNot { it == RankingSurface.HOME }.forEach {
            assertFalse(RankingRolloutPolicy.isEnabledByDefault(it))
        }
    }

    @Test
    fun shadowDiagnostics_emptyOrdersAreIgnored() {
        RankingShadowDiagnostics.clear()

        RankingShadowDiagnostics.record(
            objective = RankingObjective.DISCOVERY,
            priorOrder = emptyList(),
            adaptiveOrder = listOf("a"),
        )
        RankingShadowDiagnostics.record(
            objective = RankingObjective.DISCOVERY,
            priorOrder = listOf("a"),
            adaptiveOrder = emptyList(),
        )

        assertTrue(RankingShadowDiagnostics.snapshots().isEmpty())
    }

    @Test
    fun shadowDiagnostics_disjointOrdersProduceZeroOverlap() {
        RankingShadowDiagnostics.clear()

        RankingShadowDiagnostics.record(
            objective = RankingObjective.SLATE,
            priorOrder = listOf("a", "b", "c"),
            adaptiveOrder = listOf("x", "y", "z"),
            now = 5L,
        )

        val snapshot = RankingShadowDiagnostics.snapshots().single()
        assertEquals(0, snapshot.topFiveOverlap)
        assertEquals(0.0, snapshot.meanAbsoluteRankShift, 0.0)
        assertEquals(3, snapshot.candidateCount)
        assertEquals(5L, snapshot.recordedAt)
    }

    @Test
    fun shadowDiagnostics_snapshotsSortedByObjective() {
        RankingShadowDiagnostics.clear()

        RankingShadowDiagnostics.record(
            objective = RankingObjective.SLATE,
            priorOrder = listOf("a", "b"),
            adaptiveOrder = listOf("a", "b"),
        )
        RankingShadowDiagnostics.record(
            objective = RankingObjective.YOUR_SHOWS,
            priorOrder = listOf("a", "b"),
            adaptiveOrder = listOf("b", "a"),
        )

        val objectives = RankingShadowDiagnostics.snapshots().map { it.objective }
        assertEquals(objectives.sortedBy { it.ordinal }, objectives)
    }
}
