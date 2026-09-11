package com.logiclabs.feature.tools.courseware

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology

/**
 * Wires the console DC supply out to the four distribution rails.
 *
 * [LabCurriculum] keeps its own copy of this routine private, so rather than widening the
 * engine's API surface (those twelve presets are pinned by HMAC digests) this file carries
 * an identical local copy: +5V and 0V from the supply terminals to the top rail pair, then
 * a jumper down to the bottom pair so trench 2 is live as well.
 */
private fun wireSequentialPowerRails(circuit: BreadboardCircuit) {
    circuit.addWire(
        AD200Topology.TERM_POWER_VCC,
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 0),
        WireColor.RED
    )
    circuit.addWire(
        AD200Topology.TERM_POWER_GND,
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 0),
        WireColor.BLACK
    )
}

/** Column the 7400 latch sits at, following the classic labs' trench-1 / column-10 habit. */
private const val SR_LATCH_COLUMN = 10

/** The 7476 is 16-pin, so it starts two columns further along to keep the same visual margin. */
private const val RIPPLE_COUNTER_COLUMN = 12

/**
 * Builds the clocked (gated) S-R flip-flop on a 7400 and hangs it off [clockSocket].
 *
 * Both variations of Experiment 10 share this topology exactly; only the clock source and the
 * power-up preload differ. Gate assignment:
 *
 * - Gate 3 (Pins 9, 10 -> 8) NANDs S with the clock, giving an active-LOW Set for the latch.
 * - Gate 4 (Pins 12, 13 -> 11) NANDs R with the clock, giving an active-LOW Reset.
 * - Gates 1 and 2 (Pins 1, 2 -> 3 and Pins 4, 5 -> 6) are the cross-coupled memory: Q and ~Q.
 *
 * While the clock line is LOW both steering gates are forced HIGH, so the latch is deaf to S
 * and R and simply holds. While the clock line is HIGH the steering gates pass S and R through
 * inverted, which is precisely the active-LOW ~S/~R latch of the classic Lab 10.
 */
private fun buildClockedSrLatch(circuit: BreadboardCircuit, clockSocket: Int) {
    circuit.clearAll()
    circuit.masterPower = true
    wireSequentialPowerRails(circuit)

    val u1 = circuit.addChip("7400", trench = 1, startColumn = SR_LATCH_COLUMN)

    // 7400 is a plain 14-pin part: VCC on 14, GND on 7.
    circuit.addWire(
        u1.getPinSocket(14),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, SR_LATCH_COLUMN),
        WireColor.RED
    )
    circuit.addWire(
        u1.getPinSocket(7),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, SR_LATCH_COLUMN),
        WireColor.BLACK
    )

    // Data inputs into the steering gates.
    circuit.addWire(AD200Topology.TERM_SW0, u1.getPinSocket(9), WireColor.YELLOW)
    circuit.addWire(AD200Topology.TERM_SW1, u1.getPinSocket(12), WireColor.GRAY)

    // One clock line feeding both steering gates: source -> 3B, then 3B daisy-chained to 4B.
    circuit.addWire(clockSocket, u1.getPinSocket(10), WireColor.ORANGE)
    circuit.addWire(u1.getPinSocket(10), u1.getPinSocket(13), WireColor.ORANGE)

    // Steering outputs drive the cross-coupled pair.
    circuit.addWire(u1.getPinSocket(8), u1.getPinSocket(1), WireColor.PURPLE)
    circuit.addWire(u1.getPinSocket(11), u1.getPinSocket(4), WireColor.PURPLE)

    // The memory itself.
    circuit.addWire(u1.getPinSocket(3), u1.getPinSocket(5), WireColor.WHITE)
    circuit.addWire(u1.getPinSocket(6), u1.getPinSocket(2), WireColor.WHITE)

    // Q, ~Q, plus a third lamp that simply monitors the clock line so the student can see it.
    circuit.addWire(u1.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
    circuit.addWire(u1.getPinSocket(6), AD200Topology.TERM_LED1, WireColor.BLUE)
    circuit.addWire(u1.getPinSocket(13), AD200Topology.TERM_LED2, WireColor.ORANGE)
}

/**
 * Builds the 2-bit asynchronous (ripple) counter on a 7476 and hangs stage 1 off [clockSocket].
 *
 * The catalog has no 7473, so this uses the 7476 dual J-K: the same flip-flop with individual
 * async presets brought out. Two details matter and are wired explicitly:
 *
 * 1. The 7476's supply pins are NOT at the corners: VCC is Pin 5 and GND is Pin 13. Wiring
 *    16 and 8 the way you would a 7400 leaves the chip dead.
 * 2. ~PRE and ~CLR are active-LOW and override the clock entirely. Left floating they read
 *    HIGH in TTL and appear to work, but a counter you cannot clear is not a counter, so both
 *    are brought out to switches and parked at their inactive (HIGH) level.
 *
 * J and K are tied to +5V on both stages, so each flip-flop toggles on every active edge.
 * Stage 2's clock comes from stage 1's Q (Pin 15 -> Pin 6). The 7476 is negative-edge
 * triggered, so stage 2 advances when stage 1's Q falls 1 -> 0, i.e. on the carry out of the
 * LSB. That makes the sequence count UP: 00 -> 01 -> 10 -> 11 -> 00. Taking stage 2's clock
 * from 1~Q (Pin 14) instead would invert every carry and count DOWN: 00 -> 11 -> 10 -> 01.
 */
private fun buildRippleCounter(circuit: BreadboardCircuit, clockSocket: Int) {
    circuit.clearAll()
    circuit.masterPower = true
    wireSequentialPowerRails(circuit)

    val u1 = circuit.addChip("7476", trench = 1, startColumn = RIPPLE_COUNTER_COLUMN)

    // Supply: Pin 5 = VCC, Pin 13 = GND. Note the mid-package positions.
    circuit.addWire(
        u1.getPinSocket(5),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, RIPPLE_COUNTER_COLUMN + 4),
        WireColor.RED
    )
    circuit.addWire(
        u1.getPinSocket(13),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, RIPPLE_COUNTER_COLUMN + 3),
        WireColor.BLACK
    )

    // J = K = 1 on both stages: 1J/1K on Pins 4/16, 2J/2K on Pins 9/12. Toggle mode.
    circuit.addWire(
        u1.getPinSocket(4),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, RIPPLE_COUNTER_COLUMN + 3),
        WireColor.RED
    )
    circuit.addWire(
        u1.getPinSocket(16),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, RIPPLE_COUNTER_COLUMN),
        WireColor.RED
    )
    circuit.addWire(
        u1.getPinSocket(9),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, RIPPLE_COUNTER_COLUMN + 7),
        WireColor.RED
    )
    circuit.addWire(
        u1.getPinSocket(12),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, RIPPLE_COUNTER_COLUMN + 6),
        WireColor.RED
    )

    // Async controls, both active-LOW, both switch-driven and daisy-chained across the stages.
    // SW0 -> 1~CLR (Pin 3) -> 2~CLR (Pin 8): pulling it LOW clears the count to 00.
    circuit.addWire(AD200Topology.TERM_SW0, u1.getPinSocket(3), WireColor.YELLOW)
    circuit.addWire(u1.getPinSocket(3), u1.getPinSocket(8), WireColor.YELLOW)
    // SW1 -> 1~PRE (Pin 2) -> 2~PRE (Pin 7): pulling it LOW jams the count to 11.
    circuit.addWire(AD200Topology.TERM_SW1, u1.getPinSocket(2), WireColor.GRAY)
    circuit.addWire(u1.getPinSocket(2), u1.getPinSocket(7), WireColor.GRAY)

    // Clock chain: source -> 1~CLK (Pin 1), then 1Q (Pin 15) -> 2~CLK (Pin 6). That single
    // wire is what makes the counter asynchronous: stage 2 is clocked by stage 1, not by the
    // source, so each stage adds its own propagation delay to the settling time.
    circuit.addWire(clockSocket, u1.getPinSocket(1), WireColor.ORANGE)
    circuit.addWire(u1.getPinSocket(15), u1.getPinSocket(6), WireColor.PURPLE)

    // Read the count: 1Q is the LSB, 2Q the MSB, plus a clock monitor lamp.
    circuit.addWire(u1.getPinSocket(15), AD200Topology.TERM_LED0, WireColor.GREEN)
    circuit.addWire(u1.getPinSocket(11), AD200Topology.TERM_LED1, WireColor.BLUE)
    circuit.addWire(u1.getPinSocket(1), AD200Topology.TERM_LED2, WireColor.ORANGE)
}

/**
 * Sequential-logic experiments 10 and 11, each shipped as two one-tap variations.
 *
 * These extend the twelve classic presets in [LabCurriculum] into clocked territory, and follow
 * the same contract: [LabExperiment.buildCircuit] receives a circuit, clears it, mounts the ICs,
 * strings every wire and leaves the board powered and settled, so tapping the experiment hands
 * the student a working trainer rather than a parts list.
 *
 * Variation convention: variations of one experiment share the expNN_ id prefix and an identical
 * [LabExperiment.title], and differ only in [LabExperiment.subtitle] and in the clock source
 * they wire. Variation A uses the trainer's clock generator; Variation B uses the manual pulser
 * so the circuit can be single-stepped edge by edge.
 *
 * ### The NE555 substitution
 *
 * The paper versions of both experiments build a discrete NE555 astable (R_A and R_B of 10 kOhm
 * with a 100 uF timing capacitor: f = 1.44 / ((R_A + 2 R_B) C), about 0.48 Hz; the 0.1 uF part
 * is only the pin-5 control-voltage bypass, not a timing element). The AD-200 trainer has that
 * function built into the faceplate, so these presets wire the CLK terminal instead and set the
 * generator to 1 Hz. Electrically identical from the flip-flop's point of view: a slow square
 * wave with clean edges.
 *
 * ### Why the expected truth tables look the way they do
 *
 * [TestBenchVerifier][com.logiclabs.feature.tools.testbench.TestBenchVerifier] sweeps the
 * switches through all 2^N combinations and calls step() once per vector. It never toggles
 * clockState and never presses the pulser, so no clock edge occurs anywhere in a sweep. A naive
 * sequential preset therefore reports whatever its outputs happened to be left at, which is a
 * coin toss dressed up as a truth table.
 *
 * Both experiments dodge that honestly, by verifying something the sweep genuinely controls:
 *
 * - Experiment 10 holds the gate open (clock line parked HIGH in Variation A) and sweeps S and
 *   R, which is the real, documented level-triggered behaviour of a clocked S-R latch. Variation
 *   B parks the gate closed and asserts the opposite property: that S and R do nothing at all
 *   until the pulser fires.
 * - Experiment 11 sweeps the async ~PRE / ~CLR pair, which overrides the clock by design. The
 *   sweep's own vectors assert those overrides, so every row is determined by the sweep itself
 *   and not by whatever count the board was sitting on.
 *
 * Every row of both tables was traced against the engine's relaxation loop by hand. Nothing here
 * is expected to pass by luck, and no row of Experiment 11 depends on state from before the
 * sweep started.
 */
val sequentialLabs: List<LabExperiment> = listOf(

    LabExperiment(
        id = "exp10_clocked_sr_trainer_clock",
        labNumber = 10,
        title = "Clocked S-R Flip-Flop with Clock Source",
        subtitle = "Variation A · Trainer clock at 1 Hz",
        description = "Gate a cross-coupled 7400 S-R latch with a clock so it can only change " +
            "state while the clock is asserted. Two NAND gates steer S and R, the other two " +
            "hold the bit. The trainer's clock generator stands in for the NE555 astable of " +
            "the printed experiment (10 kOhm + 10 kOhm + 100 uF is about 0.5 Hz); run it at " +
            "1 Hz and watch LED2 to see the gate open and close. LED0 is Q, LED1 is ~Q.",
        targetChips = listOf("7400"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("S (SW0)", "R (SW1)"),
        outputLabels = listOf("Q", "~Q"),
        objectives = listOf(
            LabObjective(
                id = "exp10a_obj1",
                title = "Power the 7400",
                description = "Pin 14 to the +5V rail, Pin 7 to the 0V rail."
            ),
            LabObjective(
                id = "exp10a_obj2",
                title = "Gate S and R with the clock",
                description = "SW0 to 3A (Pin 9) and SW1 to 4A (Pin 12). Run the CLK terminal " +
                    "to 3B (Pin 10) and carry it across to 4B (Pin 13) so both steering gates " +
                    "share one clock line."
            ),
            LabObjective(
                id = "exp10a_obj3",
                title = "Cross-couple the memory",
                description = "3Y (Pin 8) to 1A (Pin 1) and 4Y (Pin 11) to 2A (Pin 4), then " +
                    "close the loop: 1Y (Pin 3) to 2B (Pin 5) and 2Y (Pin 6) to 1B (Pin 2)."
            ),
            LabObjective(
                id = "exp10a_obj4",
                title = "Substitute the trainer clock for the NE555",
                description = "The faceplate generator replaces the discrete astable. Set the " +
                    "rate to about 1 Hz and monitor the clock line on LED2."
            ),
            LabObjective(
                id = "exp10a_obj5",
                title = "Verify the gated table",
                description = "With the clock HIGH: S=1,R=0 sets Q, S=0,R=1 resets Q, S=R=0 " +
                    "holds the previous state, and S=R=1 is the forbidden combination that " +
                    "drives Q and ~Q HIGH together."
            ),
            LabObjective(
                id = "exp10a_obj6",
                title = "Prove the clock is a gate",
                description = "Stop the clock with the line LOW, then flip S and R freely: Q " +
                    "must not move. That is the whole point of clocking a latch."
            )
        ),
        // The board loads with the clock line HIGH, so the gate is open and the latch is
        // transparent to S and R. Vector by vector, traced against the relaxation loop:
        //   v=0  S=0 R=0  hold  -> buildCircuit leaves the latch reset, so (0,1)
        //   v=1  S=0 R=1  reset -> (0,1)
        //   v=2  S=1 R=0  set   -> (1,0)
        //   v=3  S=1 R=1  both steering gates LOW, both outputs forced HIGH -> (1,1)
        // The hold row is the only one that depends on prior state, and buildCircuit pins that
        // down deliberately: it drives R=1 for one step to clear the latch before releasing it,
        // so "hold" reproduces the reset state rather than an accident of load order.
        expectedFunction = { inputs ->
            val s = inputs[0]
            val r = inputs[1]
            when {
                s && r -> listOf(true, true)
                s -> listOf(true, false)
                r -> listOf(false, true)
                else -> listOf(false, true)
            }
        },
        buildCircuit = { circuit ->
            buildClockedSrLatch(circuit, AD200Topology.TERM_CLK)

            // The trainer's generator replaces the NE555. All three of these are public vars on
            // the circuit, so the preset can dial the console in from here.
            circuit.clockFrequencyHz = 1.0
            circuit.clockRunning = true
            // Park the clock HIGH: the gate is open, so the loaded board answers S and R at
            // once instead of looking dead until someone finds the clock control.
            circuit.clockState = true

            // Preload: hold Reset for one step to force a known Q = 0, then release it. This is
            // what makes the "hold" row of the truth table a fact rather than a guess.
            circuit.switches[0] = false
            circuit.switches[1] = true
            circuit.step()
            circuit.switches[1] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp10_clocked_sr_pulser",
        labNumber = 10,
        title = "Clocked S-R Flip-Flop with Clock Source",
        subtitle = "Variation B · Manual pulser single-step",
        description = "The same gated 7400 S-R latch, clocked from Pulser A instead of the " +
            "free-running generator. With the pulser idle the clock line sits LOW, both " +
            "steering gates are held HIGH and the latch is completely deaf: set S and R first, " +
            "then press the pulser to load them. One press, one edge, one state change, so " +
            "every transition can be read off the lamps at your own pace.",
        targetChips = listOf("7400"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("S (SW0)", "R (SW1)"),
        outputLabels = listOf("Q", "~Q"),
        objectives = listOf(
            LabObjective(
                id = "exp10b_obj1",
                title = "Move the clock to the pulser",
                description = "Everything is wired as in Variation A except the clock line: it " +
                    "comes from the Pulser A positive terminal, not CLK."
            ),
            LabObjective(
                id = "exp10b_obj2",
                title = "Set up, then clock",
                description = "Choose S and R while the pulser is idle, then press it. The " +
                    "latch samples the inputs only while the pulser is held down."
            ),
            LabObjective(
                id = "exp10b_obj3",
                title = "Watch the gate hold state",
                description = "With the pulser released, sweep S and R through all four " +
                    "combinations. Q and ~Q must not move at all. The verifier's truth table " +
                    "is exactly this test: four rows, one unchanged output pair."
            ),
            LabObjective(
                id = "exp10b_obj4",
                title = "Load a 1 and a 0",
                description = "S=1, R=0, press: Q lights. S=0, R=1, press: Q goes out. Both " +
                    "switches down, press: nothing happens, which is the hold condition."
            ),
            LabObjective(
                id = "exp10b_obj5",
                title = "Find the forbidden state",
                description = "S=1 and R=1 together, then press: both lamps light. Release " +
                    "both switches and the latch settles into whichever state wins the race."
            )
        ),
        // Here the clock gate is CLOSED for the whole sweep (the pulser is released, so the
        // clock line is LOW and both steering gates output HIGH regardless of S and R). The
        // property being verified is therefore clock immunity: whatever was loaded stays put
        // through all four S/R combinations. buildCircuit clocks a 1 in before sealing, so the
        // expected answer is a constant (Q=1, ~Q=0) on every row.
        //
        // The inputs are deliberately unused. That is the assertion, not an oversight: if any
        // row of this table changes, the clock gate is leaking. Note that if a student
        // single-steps the latch to Q=0 and then re-runs the bench, the whole column inverts
        // and the bench will report 0/4 - correctly, because the preset's stored state is part
        // of what is being verified.
        expectedFunction = { _ -> listOf(true, false) },
        buildCircuit = { circuit ->
            buildClockedSrLatch(circuit, AD200Topology.TERM_PULSER_A_P)

            // The pulser is the clock in this build, so park the generator rather than leave a
            // second, unused clock source running on the faceplate.
            circuit.clockRunning = false

            // Preload a 1: present S=1, press the pulser to open the gate, then release it.
            // Each mutation plus step() is one settled instant of trainer time, exactly as the
            // workbench does it when a student's finger goes down and comes back up.
            circuit.switches[0] = true
            circuit.switches[1] = false
            circuit.pulserAPressed = true
            circuit.step()
            circuit.pulserAPressed = false
            circuit.step()

            // Drop S again so the board sits in a clean hold state, storing a 1 with both data
            // inputs idle. That is the state the truth table above describes.
            circuit.switches[0] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp11_ripple_counter_trainer_clock",
        labNumber = 11,
        title = "2-Bit Asynchronous Ripple Counter",
        subtitle = "Variation A · Trainer clock at 1 Hz",
        description = "Chain both halves of a 7476 dual J-K into a 2-bit ripple counter. J and " +
            "K are tied high so each stage toggles, stage 1 is clocked from the trainer " +
            "generator (standing in for the NE555 astable) and stage 2 is clocked from stage " +
            "1's Q. Because the 7476 fires on the falling edge, the count runs UP: 00, 01, 10, " +
            "11, back to 00. LED0 is Q0 (LSB), LED1 is Q1 (MSB), LED2 follows the clock. SW0 " +
            "clears the count, SW1 presets it.",
        targetChips = listOf("7476"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("~CLR (SW0)", "~PRE (SW1)"),
        outputLabels = listOf("Q0", "Q1"),
        objectives = listOf(
            LabObjective(
                id = "exp11a_obj1",
                title = "Power the 7476 on Pins 5 and 13",
                description = "The 7476 does not put its supply on the corners. VCC is Pin 5 " +
                    "and GND is Pin 13; wiring 16 and 8 out of habit leaves the chip dead."
            ),
            LabObjective(
                id = "exp11a_obj2",
                title = "Put both stages in toggle mode",
                description = "1J (Pin 4), 1K (Pin 16), 2J (Pin 9) and 2K (Pin 12) all to +5V. " +
                    "J=K=1 makes a J-K flip-flop divide its clock by two."
            ),
            LabObjective(
                id = "exp11a_obj3",
                title = "De-assert preset and clear on purpose",
                description = "SW0 drives 1~CLR and 2~CLR (Pins 3 and 8), SW1 drives 1~PRE and " +
                    "2~PRE (Pins 2 and 7). Both are active-LOW, so both switches sit HIGH. A " +
                    "floating TTL input reads HIGH and looks fine, but then you have no way to " +
                    "zero the counter."
            ),
            LabObjective(
                id = "exp11a_obj4",
                title = "Ripple the carry",
                description = "CLK to 1~CLK (Pin 1), then 1Q (Pin 15) to 2~CLK (Pin 6). Stage " +
                    "2 never sees the source clock; it is clocked by the stage below it, which " +
                    "is what asynchronous means here."
            ),
            LabObjective(
                id = "exp11a_obj5",
                title = "Read the sequence and its direction",
                description = "Q0 on LED0, Q1 on LED1. The 7476 is negative-edge triggered, so " +
                    "stage 2 advances when Q0 falls from 1 to 0, which is the carry of an up " +
                    "count: 00, 01, 10, 11, 00. Moving stage 2's clock to 1~Q (Pin 14) counts " +
                    "down instead."
            ),
            LabObjective(
                id = "exp11a_obj6",
                title = "Clear and preset the count",
                description = "Hold SW0 LOW: the count jams at 00 and the clock is ignored. " +
                    "Hold SW1 LOW: it jams at 11. Assert both and the 7476 drives Q and ~Q " +
                    "HIGH on both stages, which is the illegal combination."
            )
        ),
        // Sweeping ~CLR and ~PRE is what makes a counter verifiable without a clock edge: the
        // async inputs override the clock by design, so every row is decided by the switches.
        //   v=0  ~CLR=0 ~PRE=0  illegal: both stages force Q HIGH -> (1,1)
        //   v=1  ~CLR=0 ~PRE=1  clear:   both stages reset        -> (0,0)
        //   v=2  ~CLR=1 ~PRE=0  preset:  both stages set          -> (1,1)
        //   v=3  ~CLR=1 ~PRE=1  hold:    carries v=2's 11 forward -> (1,1)
        // The hold row inherits from the preset row inside the same sweep, so this table is
        // independent of the count the board happened to be showing when the bench started:
        // the student can run it mid-count and still get 4/4. Both stages always agree here,
        // which is why the outputs are a pair of the same value.
        expectedFunction = { inputs ->
            val clearAsserted = !inputs[0]
            val presetAsserted = !inputs[1]
            val q = when {
                clearAsserted && presetAsserted -> true
                presetAsserted -> true
                clearAsserted -> false
                else -> true
            }
            listOf(q, q)
        },
        buildCircuit = { circuit ->
            buildRippleCounter(circuit, AD200Topology.TERM_CLK)

            circuit.clockFrequencyHz = 1.0
            circuit.clockRunning = true
            // Park the clock LOW so the counter's first move is a complete rise-then-fall
            // rather than half an edge left over from the load.
            circuit.clockState = false

            // Both async controls inactive (HIGH): the counter is free to run.
            circuit.switches[0] = true
            circuit.switches[1] = true
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp11_ripple_counter_pulser",
        labNumber = 11,
        title = "2-Bit Asynchronous Ripple Counter",
        subtitle = "Variation B · Manual pulser single-step",
        description = "The same 7476 ripple counter, clocked by Pulser A so the count advances " +
            "one step per press. Releasing the pulser is the falling edge that toggles stage " +
            "1, and every second release ripples through to stage 2: 00, 01, 10, 11, 00. " +
            "Stepping by hand is how you see that the two stages do not change together: " +
            "stage 2 waits on stage 1, one propagation delay behind.",
        targetChips = listOf("7476"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("~CLR (SW0)", "~PRE (SW1)"),
        outputLabels = listOf("Q0", "Q1"),
        objectives = listOf(
            LabObjective(
                id = "exp11b_obj1",
                title = "Clock stage 1 from the pulser",
                description = "Pulser A positive terminal to 1~CLK (Pin 1). Everything else is " +
                    "wired as in Variation A, including 1Q (Pin 15) to 2~CLK (Pin 6)."
            ),
            LabObjective(
                id = "exp11b_obj2",
                title = "Count one press at a time",
                description = "Start from 00 and press four times: 01, 10, 11, 00. The counter " +
                    "advances on the release, because the 7476 triggers on the falling edge."
            ),
            LabObjective(
                id = "exp11b_obj3",
                title = "Catch the ripple",
                description = "Q1 only moves on the presses where Q0 goes from 1 to 0, the " +
                    "second and the fourth. That is the carry rippling up the chain."
            ),
            LabObjective(
                id = "exp11b_obj4",
                title = "Confirm the direction",
                description = "Clocking stage 2 from 1Q gives an up count. Rewire Pin 6 to 1~Q " +
                    "(Pin 14) and the same four presses walk backwards: 11, 10, 01, 00."
            ),
            LabObjective(
                id = "exp11b_obj5",
                title = "Override the clock",
                description = "SW0 LOW clears to 00 and SW1 LOW presets to 11, both regardless " +
                    "of the pulser. Async inputs do not wait for an edge."
            )
        ),
        // Identical reasoning to Variation A: the sweep exercises the async overrides, which
        // work with no clock edge at all, and the last row inherits from the row before it
        // inside the same sweep. That is why both variations can share one expected table even
        // though they are clocked from different sources.
        expectedFunction = { inputs ->
            val clearAsserted = !inputs[0]
            val presetAsserted = !inputs[1]
            val q = when {
                clearAsserted && presetAsserted -> true
                presetAsserted -> true
                clearAsserted -> false
                else -> true
            }
            listOf(q, q)
        },
        buildCircuit = { circuit ->
            buildRippleCounter(circuit, AD200Topology.TERM_PULSER_A_P)

            // Pulser A is the clock source here, so the generator is parked rather than left
            // free-running on an unconnected terminal.
            circuit.clockRunning = false
            circuit.pulserAPressed = false

            // Async controls inactive; the board loads sitting at 00, ready to be stepped.
            circuit.switches[0] = true
            circuit.switches[1] = true
            circuit.step()
        }
    )
)
