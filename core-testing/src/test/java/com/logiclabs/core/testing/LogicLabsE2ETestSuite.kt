package com.logiclabs.core.testing

import com.logiclabs.core.testing.tier1_coverage.BasicGatesFeatureTest
import com.logiclabs.core.testing.tier1_coverage.BreadboardDsuFeatureTest
import com.logiclabs.core.testing.tier1_coverage.ComplexChipsFeatureTest
import com.logiclabs.core.testing.tier1_coverage.TrainerConsoleFeatureTest
import com.logiclabs.core.testing.tier1_coverage.VirtualInstrumentsFeatureTest
import com.logiclabs.core.testing.tier2_boundary.BoundaryBusContentionTest
import com.logiclabs.core.testing.tier2_boundary.BoundaryClockFrequenciesTest
import com.logiclabs.core.testing.tier2_boundary.BoundaryEmptyNetlistTest
import com.logiclabs.core.testing.tier2_boundary.BoundaryFloatingAndReverseTest
import com.logiclabs.core.testing.tier2_boundary.BoundaryOscillationClampingTest
import com.logiclabs.core.testing.tier3_combination.ClockToRippleToSevenSegTest
import com.logiclabs.core.testing.tier3_combination.PulserToFlipFlopScopeTest
import com.logiclabs.core.testing.tier3_combination.SwitchesToGatesToLedsTest
import com.logiclabs.core.testing.tier4_courseware.Lab1BasicGatesExperimentTest
import com.logiclabs.core.testing.tier4_courseware.Lab2UniversalNandExperimentTest
import com.logiclabs.core.testing.tier4_courseware.Lab3BinaryArithmeticExperimentTest
import com.logiclabs.core.testing.tier4_courseware.Lab4MultiplexersDecodersExperimentTest
import com.logiclabs.core.testing.tier4_courseware.Lab5SequentialLogicExperimentTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Master E2E Test Suite for Logic Labs.
 * Executes all 18 test fixtures across Tier 1, Tier 2, Tier 3, and Tier 4.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Tier 1: Feature Coverage (>=5 tests per feature)
    BasicGatesFeatureTest::class,
    ComplexChipsFeatureTest::class,
    BreadboardDsuFeatureTest::class,
    TrainerConsoleFeatureTest::class,
    VirtualInstrumentsFeatureTest::class,

    // Tier 2: Boundary & Corner Cases (>=5 tests per feature)
    BoundaryEmptyNetlistTest::class,
    BoundaryClockFrequenciesTest::class,
    BoundaryOscillationClampingTest::class,
    BoundaryFloatingAndReverseTest::class,
    BoundaryBusContentionTest::class,

    // Tier 3: Cross-Feature Combinations (Pairwise)
    SwitchesToGatesToLedsTest::class,
    ClockToRippleToSevenSegTest::class,
    PulserToFlipFlopScopeTest::class,

    // Tier 4: Real-World Application Scenarios (Classic 5-Lab University Experiments)
    Lab1BasicGatesExperimentTest::class,
    Lab2UniversalNandExperimentTest::class,
    Lab3BinaryArithmeticExperimentTest::class,
    Lab4MultiplexersDecodersExperimentTest::class,
    Lab5SequentialLogicExperimentTest::class
)
class LogicLabsE2ETestSuite
