package com.example

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import kotlin.system.measureNanoTime
import kotlin.math.*
import kotlin.random.Random

// ----------------------------------------------------
// SYNTHETIC SOUND ENGINE FOR ZERO-LATENCY EFFECTS
// ----------------------------------------------------
object SoundSynth {
    fun playPop() {
        playTone(550, 80, 0.35f)
    }

    fun playClearLine() {
        playSweep(420, 1100, 180, 0.45f)
    }

    fun playError() {
        playSweep(180, 120, 150, 0.45f)
    }

    fun playCombo(streak: Int) {
        val base = 480 + (streak * 90)
        playSweep(base, base * 2, 220, 0.5f)
    }

    fun playGameOver() {
        playSweep(380, 140, 450, 0.4f)
    }

    fun playWin() {
        Thread {
            try {
                playToneDirect(440, 100, 0.4f)
                Thread.sleep(110)
                playToneDirect(554, 100, 0.4f)
                Thread.sleep(110)
                playToneDirect(659, 100, 0.4f)
                Thread.sleep(110)
                playToneDirect(880, 250, 0.4f)
            } catch (ignored: Exception) {}
        }.start()
    }

    private fun playTone(frequency: Int, durationMs: Int, volume: Float) {
        Thread { playToneDirect(frequency, durationMs, volume) }.start()
    }

    private fun playToneDirect(frequency: Int, durationMs: Int, volume: Float) {
        try {
            val sampleRate = 22050
            val numSamples = durationMs * sampleRate / 1000
            val samples = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                samples[i] = (sin(2.0 * Math.PI * frequency * t) * Short.MAX_VALUE * volume).toInt().toShort()
            }
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                numSamples * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(samples, 0, numSamples)
            audioTrack.play()
            Thread.sleep(durationMs + 30L)
            audioTrack.release()
        } catch (ignored: Exception) {}
    }

    private fun playSweep(startFreq: Int, endFreq: Int, durationMs: Int, volume: Float) {
        Thread {
            try {
                val sampleRate = 22050
                val numSamples = durationMs * sampleRate / 1000
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    val freq = startFreq + (endFreq - startFreq) * progress
                    val t = i.toDouble() / sampleRate
                    samples[i] = (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * volume).toInt().toShort()
                }
                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    numSamples * 2,
                    AudioTrack.MODE_STATIC
                )
                audioTrack.write(samples, 0, numSamples)
                audioTrack.play()
                Thread.sleep(durationMs + 30L)
                audioTrack.release()
            } catch (ignored: Exception) {}
        }.start()
    }
}

// ----------------------------------------------------
// PUZZLE SHAPES & TEMPLATE DEFINITIONS
// ----------------------------------------------------
data class PuzzleShape(
    val id: Int,
    val name: String,
    val coords: List<Pair<Int, Int>>, // offset cells (row, col)
    val color: Color
) {
    // Rotates the shape by 90 degrees clockwise
    fun rotated90(): PuzzleShape {
        val newCoords = coords.map { (r, c) -> Pair(c, -r) }
        // Offset coords to align top-left coordinate to (0,0)
        val minR = newCoords.minOf { it.first }
        val minC = newCoords.minOf { it.second }
        val alignedCoords = newCoords.map { (r, c) -> Pair(r - minR, c - minC) }
        return copy(coords = alignedCoords)
    }

    fun toIntArray(): IntArray {
        val arr = IntArray(coords.size * 2)
        var idx = 0
        for ((r, c) in coords) {
            arr[idx++] = r
            arr[idx++] = c
        }
        return arr
    }
}

val SHAPE_TEMPLATES = listOf(
    // 1. Single dot
    PuzzleShape(1, "Oak Peg", listOf(0 to 0), Color(0xFFE28743)),
    // 2. 1x2 splitters
    PuzzleShape(2, "Maple Splitter 2v", listOf(0 to 0, 1 to 0), Color(0xFFCD6133)),
    PuzzleShape(3, "Maple Splitter 2h", listOf(0 to 0, 0 to 1), Color(0xFFCD6133)),
    // 3. 1x3 bars
    PuzzleShape(4, "Pine Beam 3v", listOf(0 to 0, 1 to 0, 2 to 0), Color(0xFFB33939)),
    PuzzleShape(5, "Pine Beam 3h", listOf(0 to 0, 0 to 1, 0 to 2), Color(0xFFB33939)),
    // 4. 1x4 heavy beams
    PuzzleShape(6, "Cedar Trunk 4v", listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), Color(0xFF227093)),
    PuzzleShape(7, "Cedar Trunk 4h", listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3), Color(0xFF227093)),
    // 5. Square chunks
    PuzzleShape(8, "Crate 2x2", listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), Color(0xFF218C74)),
    PuzzleShape(9, "Cabinet 3x3", listOf(
        0 to 0, 0 to 1, 0 to 2,
        1 to 0, 1 to 1, 1 to 2,
        2 to 0, 2 to 1, 2 to 2
    ), Color(0xFF845EC2)),
    // 6. Corner L-shapes (small and large)
    PuzzleShape(10, "Corner joint", listOf(0 to 0, 1 to 0, 1 to 1), Color(0xFFFFB142)),
    PuzzleShape(11, "Rev Corner joint", listOf(0 to 0, 0 to 1, 1 to 0), Color(0xFFFFB142)),
    PuzzleShape(12, "Large L", listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2), Color(0xFFFF5252)),
    PuzzleShape(13, "Large L Rev", listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 2 to 0), Color(0xFFFF5252)),
    // 7. S, Z, T pieces
    PuzzleShape(14, "T-Plank", listOf(0 to 0, 0 to 1, 0 to 2, 1 to 1), Color(0xFF474787)),
    PuzzleShape(15, "Z-Board", listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2), Color(0xFF03A9F4)),
    PuzzleShape(16, "S-Board", listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1), Color(0xFF8BC34A))
)

// ----------------------------------------------------
// ADVENTURE LEVELS CONFIGURATION
// ----------------------------------------------------
data class PuzzleLevel(
    val id: Int,
    val name: String,
    val description: String,
    val targetScore: Int,
    val targetClearedLines: Int,
    val initialLockedBoard: List<Pair<Int, Int>> // Pre-placed blocked cells (row, col)
)

val ADVENTURE_LEVELS = listOf(
    PuzzleLevel(
        id = 1,
        name = "Maple Grove",
        description = "Reach 120 points and clear 1 line!",
        targetScore = 120,
        targetClearedLines = 1,
        initialLockedBoard = listOf(
            0 to 0, 0 to 7, 7 to 0, 7 to 7
        )
    ),
    PuzzleLevel(
        id = 2,
        name = "Cedar Archway",
        description = "Harvest 200 points and clear 2 lines!",
        targetScore = 200,
        targetClearedLines = 2,
        initialLockedBoard = listOf(
            3 to 3, 3 to 4, 4 to 3, 4 to 4,
            2 to 2, 2 to 5, 5 to 2, 5 to 5
        )
    ),
    PuzzleLevel(
        id = 3,
        name = "Ironwood Gorge",
        description = "Break the symmetry! Score 300 points and wipe 3 lines!",
        targetScore = 300,
        targetClearedLines = 3,
        initialLockedBoard = listOf(
            0 to 3, 1 to 3, 2 to 3, 5 to 4, 6 to 4, 7 to 4
        )
    ),
    PuzzleLevel(
        id = 4,
        name = "Pyramid Tomb",
        description = "Ancient brick cages. Extract 400 points and clear 4 rows!",
        targetScore = 400,
        targetClearedLines = 4,
        initialLockedBoard = listOf(
            1 to 1, 1 to 2, 1 to 5, 1 to 6,
            2 to 1, 2 to 6,
            5 to 1, 5 to 6,
            6 to 1, 6 to 2, 6 to 5, 6 to 6
        )
    ),
    PuzzleLevel(
        id = 5,
        name = "The Great Ring",
        description = "Locked circular blockades. Harvest 500 points & 5 clears!",
        targetScore = 500,
        targetClearedLines = 5,
        initialLockedBoard = listOf(
            0 to 2, 0 to 5,
            2 to 0, 2 to 7,
            5 to 0, 5 to 7,
            7 to 2, 7 to 5
        )
    )
)

// Define old voxel model structures to keep 3D engine compilable inside
data class VoxelBlockType(
    val id: Byte,
    val name: String,
    val topColor: Color,
    val sideColor: Color,
    val frontColor: Color,
    val isNeon: Boolean = false,
    val description: String = ""
)

val VOXEL_TYPES = mapOf(
    1.toByte() to VoxelBlockType(1, "Spring Grass", Color(0xFF4CAF50), Color(0xFF388E3C), Color(0xFF2E7D32), description = "Classic soil topped with lush vegetation"),
    2.toByte() to VoxelBlockType(2, "Rich Soil", Color(0xFF8D6E63), Color(0xFF795548), Color(0xFF5D4037), description = "Dark fertile earth for structural bases"),
    3.toByte() to VoxelBlockType(3, "Slate Stone", Color(0xFFB0BEC5), Color(0xFF90A4AE), Color(0xFF78909C), description = "Solid volcanic stone extracted from caves"),
    4.toByte() to VoxelBlockType(4, "Corewood", Color(0xFFA1887F), Color(0xFF5D4037), Color(0xFF4E342E), description = "Resilient natural timber for scaffolds"),
    5.toByte() to VoxelBlockType(5, "Oak Leaves", Color(0xFF81C784), Color(0xFF4CAF50), Color(0xFF388E3C), description = "Drawn from virtual highlands canopy"),
    6.toByte() to VoxelBlockType(6, "Clay Brick", Color(0xFFE57373), Color(0xFFD32F2F), Color(0xFFC62828), description = "Hard-pressed standard architectural modules"),
    7.toByte() to VoxelBlockType(7, "Nugget Gold", Color(0xFFFFEE58), Color(0xFFFBC02D), Color(0xFFF9A825), description = "Extremely dense precious element"),
    8.toByte() to VoxelBlockType(8, "Core Orange", Color(0xFFFFE0B2), Color(0xFFFF8F00), Color(0xFFE65100), isNeon = true, description = "Magma block, illuminating"),
    9.toByte() to VoxelBlockType(9, "Laser Cyan", Color(0xFFE0F7FA), Color(0xFF00ACC1), Color(0xFF006064), isNeon = true, description = "Photon static block with custom hum"),
    10.toByte() to VoxelBlockType(10, "Tint Glass", Color(0x7CE0F2F1), Color(0x6600E5FF), Color(0x5500B8D4), description = "Permits deep views")
)

// ----------------------------------------------------
// MASTER ACTIVITY ENTRY
// ----------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF130905) // Deep mahogany/wooden dark theme background
                ) {
                    BlockGameScreen()
                }
            }
        }
    }
}

@Composable
fun BlockGameScreen() {
    // Top-Level Navigation Router
    // Modes: "wood_puzzle" (default) or "voxel_3d"
    var appMode by remember { mutableStateOf("wood_puzzle") }

    // Inside Wood Puzzle, split between "classic" endless and "adventure" level mode
    var woodModeSubTab by remember { mutableStateOf("classic") } // "classic" or "adventure"

    // Context & SharedPreferences reference for loading scores
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wood_block_puzzle_prefs", Context.MODE_PRIVATE) }

    // Init Engine
    LaunchedEffect(Unit) {
        NativeVoxelEngine.initPuzzleGame()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF180E09) // Luxury cherry-cedar dark background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // HIGH QUALITY TOP HEAD SWITCHER
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF281810),
                    border = BorderStroke(1.5.dp, Color(0xFF4A2F21))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { appMode = "wood_puzzle" },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("tab_wood_puzzle"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (appMode == "wood_puzzle") Color(0xFF8B5A2B) else Color.Transparent,
                                contentColor = if (appMode == "wood_puzzle") Color.White else Color(0x99FFFFFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wood Puzzle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { appMode = "voxel_3d" },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("tab_voxel_3d"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (appMode == "voxel_3d") Color(0xFF8B5A2B) else Color.Transparent,
                                contentColor = if (appMode == "voxel_3d") Color.White else Color(0x99FFFFFF)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("3D Voxel Core", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (appMode == "wood_puzzle") {
                    // Sub-navigation switcher for Classic vs Adventure level mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E110A),
                            border = BorderStroke(1.dp, Color(0xFF3B2014))
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (woodModeSubTab == "classic") Color(0xFFCD853F) else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { woodModeSubTab = "classic" }
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .testTag("subtab_classic")
                                ) {
                                    Text(
                                        "Classic Mode",
                                        color = if (woodModeSubTab == "classic") Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (woodModeSubTab == "adventure") Color(0xFFCD853F) else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { woodModeSubTab = "adventure" }
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .testTag("subtab_adventure")
                                ) {
                                    Text(
                                        "Adventure Levels",
                                        color = if (woodModeSubTab == "adventure") Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Wood Game core rendering
                    WoodPuzzleEngineView(
                        woodSubTab = woodModeSubTab,
                        context = context,
                        prefs = prefs
                    )
                } else {
                    // Originally built Voxel Creative Builder screen segment
                    VoxelCreativeBuilderView()
                }
            }
        }
    }
}

// ----------------------------------------------------
// MODERN CHERRY-MATE WOOD PUZZLE VIEW segment
// ----------------------------------------------------
@Composable
fun WoodPuzzleEngineView(
    woodSubTab: String,
    context: Context,
    prefs: android.content.SharedPreferences
) {
    // Score states
    var score by rememberSaveable { mutableStateOf(0) }
    var highScore by remember { mutableStateOf(prefs.getInt("classic_high_score", 0)) }

    // Adventure level parameters
    var activeLevelId by rememberSaveable { mutableStateOf(1) }
    val level = remember(activeLevelId) { ADVENTURE_LEVELS.firstOrNull { it.id == activeLevelId } ?: ADVENTURE_LEVELS.first() }
    var levelClearedLinesCount by remember { mutableStateOf(0) }
    var reachedLevelWinModal by remember { mutableStateOf(false) }

    // Combo streak
    var comboCount by remember { mutableStateOf(0) }
    var pointsStreakGlow by remember { mutableStateOf(0) }

    // Board Refresh Signal
    var triggerBoardRefresh by remember { mutableStateOf(0) }

    // Selected piece from bottom tray
    var selectedShapeIdx by remember { mutableStateOf<Int?>(null) }

    // Check if hammer power-up is active (destruction tool)
    var isHammerPowerUpActive by remember { mutableStateOf(false) }
    var remainsHammersCount by remember { mutableStateOf(3) }

    // Trays parameters (3 random items at the bottom)
    val trayItems = remember { mutableStateListOf<PuzzleShape?>() }

    // Undo States (store last move data)
    var lastBoardSnapshot by remember { mutableStateOf<Array<IntArray>?>(null) }
    var lastTraySnapshot by remember { mutableStateOf<List<PuzzleShape?>>(listOf()) }
    var lastScoreSnapshot by remember { mutableStateOf(0) }
    var lastComboSnapshot by remember { mutableStateOf(0) }
    var lastClearedLinesSnapshot by remember { mutableStateOf(0) }
    var canUndo by remember { mutableStateOf(false) }

    // Game Over indicator
    var isGameOver by remember { mutableStateOf(false) }

    // Generate random items to populate the empty bottom tray
    fun replenishTray() {
        trayItems.clear()
        // Randomize 3 templates
        for (i in 0 until 3) {
            val randTemplate = SHAPE_TEMPLATES[Random.nextInt(SHAPE_TEMPLATES.size)]
            trayItems.add(randTemplate)
        }
    }

    // Capture Undo Frame Snapshot
    fun saveBoardStateForUndo() {
        val snapshot = Array(8) { row ->
            IntArray(8) { col ->
                NativeVoxelEngine.getPuzzleBoardCell(row, col)
            }
        }
        lastBoardSnapshot = snapshot
        lastTraySnapshot = trayItems.toList()
        lastScoreSnapshot = score
        lastComboSnapshot = comboCount
        lastClearedLinesSnapshot = levelClearedLinesCount
        canUndo = true
    }

    // Trigger Game Restart Function
    fun restartPuzzleGame(isAdventure: Boolean) {
        NativeVoxelEngine.initPuzzleGame()
        score = 0
        comboCount = 0
        levelClearedLinesCount = 0
        selectedShapeIdx = null
        isHammerPowerUpActive = false
        isGameOver = false
        reachedLevelWinModal = false
        canUndo = false

        replenishTray()

        if (isAdventure) {
            // Fill wood blocks based on locked level map coordinates
            for ((lockedR, lockedC) in level.initialLockedBoard) {
                // Set Cell value to 99 representing a stone-grey locked locked boulder cell block
                NativeVoxelEngine.setPuzzleBoardCell(lockedR, lockedC, 99)
            }
        }

        triggerBoardRefresh++
        SoundSynth.playPop()
    }

    // Check if game over occurred (none of the remaining tray shapes can fit anywhere on current grid)
    fun verifyGameOverState() {
        var anyFreePositionsAvailable = false
        val activeTrayShapes = trayItems.filterNotNull()

        if (activeTrayShapes.isEmpty()) {
            return // tray is empty, will be replenished which can't immediately lose
        }

        for (shape in activeTrayShapes) {
            if (NativeVoxelEngine.checkShapeFitsAnywhere(shape.toIntArray())) {
                anyFreePositionsAvailable = true
                break
            }
        }

        if (!anyFreePositionsAvailable) {
            isGameOver = true
            SoundSynth.playGameOver()
        }
    }

    // Initialize current game setup on level/sub-tab modification
    LaunchedEffect(woodSubTab, activeLevelId) {
        restartPuzzleGame(woodSubTab == "adventure")
    }

    // Core Placement Handler
    fun placeSelectedItemOnGrid(targetRow: Int, targetCol: Int): Boolean {
        val shapeIdx = selectedShapeIdx ?: return false
        val shape = trayItems.getOrNull(shapeIdx) ?: return false

        saveBoardStateForUndo()

        val coordsArray = shape.toIntArray()
        val success = NativeVoxelEngine.placeShape(coordsArray, targetRow, targetCol, shape.id)
        if (success) {
            // Award placement points
            val placementPoints = shape.coords.size
            score += placementPoints
            if (woodSubTab == "classic" && score > highScore) {
                highScore = score
                prefs.edit().putInt("classic_high_score", highScore).apply()
            }

            // Remove shape from tray
            trayItems[shapeIdx] = null
            selectedShapeIdx = null

            // Animate satisfying sound
            SoundSynth.playPop()

            // Resolve completed horizontal/vertical grid lines clearances
            val lineClearResultMask = NativeVoxelEngine.clearLines()
            val linesClearedInThisTurn = (lineClearResultMask shr 16) and 0xFF

            if (linesClearedInThisTurn > 0) {
                comboCount++
                val baseLineClearBonus = linesClearedInThisTurn * 15
                val comboBonusFactor = comboCount * 12
                val finalClearBonus = baseLineClearBonus + comboBonusFactor
                score += finalClearBonus
                levelClearedLinesCount += linesClearedInThisTurn

                // Update classic highscore
                if (woodSubTab == "classic" && score > highScore) {
                    highScore = score
                    prefs.edit().putInt("classic_high_score", highScore).apply()
                }

                pointsStreakGlow = finalClearBonus
                SoundSynth.playClearLine()

                // Chain bonus sweep sound
                if (comboCount > 1) {
                    SoundSynth.playCombo(comboCount)
                }
            } else {
                comboCount = 0
                pointsStreakGlow = 0
            }

            // Replenish empty tray if all 3 suggestions are placed
            if (trayItems.all { it == null }) {
                replenishTray()
            }

            // Verify adventure goal win thresholds
            if (woodSubTab == "adventure") {
                if (score >= level.targetScore && levelClearedLinesCount >= level.targetClearedLines) {
                    reachedLevelWinModal = true
                    SoundSynth.playWin()
                }
            }

            // Check game over
            verifyGameOverState()

            triggerBoardRefresh++
            return true
        } else {
            SoundSynth.playError()
        }
        return false
    }

    // Perform Sledgehammer Action
    fun executeHammerSledge(targetRow: Int, targetCol: Int) {
        if (!isHammerPowerUpActive || remainsHammersCount <= 0) return

        val cellVal = NativeVoxelEngine.getPuzzleBoardCell(targetRow, targetCol)
        if (cellVal > 0) {
            saveBoardStateForUndo()
            NativeVoxelEngine.setPuzzleBoardCell(targetRow, targetCol, 0)
            remainsHammersCount--
            isHammerPowerUpActive = false
            SoundSynth.playClearLine()
            triggerBoardRefresh++

            // Re-verify if this hammer clearance saved the user from Game Over
            if (isGameOver) {
                var stillLoser = true
                val items = trayItems.filterNotNull()
                for (sh in items) {
                    if (NativeVoxelEngine.checkShapeFitsAnywhere(sh.toIntArray())) {
                        stillLoser = false
                        break
                    }
                }
                if (!stillLoser) {
                    isGameOver = false
                }
            }
        }
    }

    // Trigger Undo Action
    fun performUndo() {
        val prevBoard = lastBoardSnapshot ?: return
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                NativeVoxelEngine.setPuzzleBoardCell(r, c, prevBoard[r][c])
            }
        }
        // Restoring snapshots
        trayItems.clear()
        trayItems.addAll(lastTraySnapshot)
        score = lastScoreSnapshot
        comboCount = lastComboSnapshot
        levelClearedLinesCount = lastClearedLinesSnapshot
        canUndo = false
        isGameOver = false
        SoundSynth.playPop()
        triggerBoardRefresh++
    }

    // Layout Columns structures
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // GOLDEN SCOREBOARDS HUD BAR
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF23130A),
            border = BorderStroke(1.5.dp, Color(0xFF422617))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (woodSubTab == "classic") "SCORE" else level.name,
                            color = Color(0xFFA5B1C2),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "$score",
                        color = Color(0xFFFFD700),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Streak notifications
                AnimatedVisibility(
                    visible = comboCount > 0,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Surface(
                        color = Color(0xFFFF5252),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "COMBO x$comboCount",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (woodSubTab == "classic") {
                        Text(
                            text = "BEST SCORE",
                            color = Color(0xFFA5B1C2),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "$highScore",
                            color = Color(0xFFFFB142),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        // Level goals status
                        Text(
                            text = "GOAL & PROGRESS",
                            color = Color(0xFFA5B1C2),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Score: $score/${level.targetScore} | Lines: $levelClearedLinesCount/${level.targetClearedLines}",
                            color = Color(0xFF4BC0C0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // LEVEL DESCRIPTION DISPLAY
        if (woodSubTab == "adventure") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1E16)),
                border = BorderStroke(1.dp, Color(0xFF4C362A))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB142),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Active Challenge:",
                            color = Color(0xFFFBC531),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = level.description,
                            color = Color(0xFFE5BA73),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // PERFECTLY ALIGNED 8x8 GRID CANVAS BOARD
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF2A1B12), // Border frame surrounding 8x8 grid
            border = BorderStroke(4.dp, Color(0xFF4B2F1D)),
            tonalElevation = 6.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Key drawing component: Grid
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    for (r in 0 until 8) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (c in 0 until 8) {
                                // Dynamic Board Cell state mapping
                                val cellVal = remember(triggerBoardRefresh, r, c) {
                                    NativeVoxelEngine.getPuzzleBoardCell(r, c)
                                }

                                // If a shape is currently selected, highlight valid space placement preview projection
                                val selectedShape = selectedShapeIdx?.let { trayItems.getOrNull(it) }
                                val hoverHighlighted = remember(selectedShape, r, c, triggerBoardRefresh) {
                                    if (selectedShape == null) false
                                    else {
                                        // Simple highlight check: see if selected shape coordinates overlaps cell (r,c) base index
                                        false
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                cellVal == 99 -> Color(0xFF4A4D52) // Stone steel boulder walls on levels
                                                cellVal > 0 -> {
                                                    // Extract matching color pattern
                                                    val matchedTemplate = SHAPE_TEMPLATES.firstOrNull { it.id == cellVal }
                                                    matchedTemplate?.color ?: Color(0xFF8C5533)
                                                }
                                                hoverHighlighted -> Color(0x33FFB142)
                                                else -> Color(0xFF1E120A) // Background color of empty grid square cells
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                cellVal == 99 -> Color(0xFF6B7280)
                                                cellVal > 0 -> Color(0xFF1E120A)
                                                else -> Color(0xFF2B1C13)
                                            },
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            if (isHammerPowerUpActive) {
                                                executeHammerSledge(r, c)
                                            } else if (selectedShape != null) {
                                                placeSelectedItemOnGrid(r, c)
                                            }
                                        }
                                        .testTag("board_cell_${r}_${c}")
                                ) {
                                    // Custom visual render decorations inside cells
                                    if (cellVal > 0) {
                                        // Draw beveled wood board grain details with circular rings or simple overlays
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val w = size.width
                                            val h = size.height
                                            // Golden bevel outline borders
                                            drawRect(
                                                color = Color(0x22FFFFFF),
                                                topLeft = Offset(0f, 0f),
                                                size = size
                                            )
                                            // Core vintage tree trunk center dot
                                            drawCircle(
                                                color = Color(0x11000000),
                                                radius = w * 0.28f,
                                                center = Offset(w/2f, h/2f)
                                            )
                                        }
                                    } else {
                                        // Empty cell indent outline depth shading
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .border(0.7.dp, Color(0xFF110A06), RoundedCornerShape(6.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // DYNAMICS CONTROLS POWERUPS TOOLBAR
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF251710)),
            border = BorderStroke(1.2.dp, Color(0xFF452B1F))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SLEDGEHAMMER DESTRUCTION powerup tool
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            if (remainsHammersCount > 0) {
                                isHammerPowerUpActive = !isHammerPowerUpActive
                                selectedShapeIdx = null
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isHammerPowerUpActive) Color(0xFFE18743) else Color(0xFF1C0E06)
                        ),
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("tool_hammer")
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = "Hammer Powerup",
                            tint = if (isHammerPowerUpActive) Color.Black else Color(0xFFE18743)
                        )
                    }
                    Text(
                        "Hammer ($remainsHammersCount)",
                        fontSize = 10.sp,
                        color = Color(0xFFE5BA73),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // ROTATE SELECTED SHAPE Tool
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            val activeIdx = selectedShapeIdx
                            if (activeIdx != null) {
                                val currentShape = trayItems[activeIdx]
                                if (currentShape != null) {
                                    trayItems[activeIdx] = currentShape.rotated90()
                                    SoundSynth.playPop()
                                    triggerBoardRefresh++
                                    verifyGameOverState()
                                }
                            }
                        },
                        enabled = selectedShapeIdx != null,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF1C0E06),
                            disabledContainerColor = Color(0x331C0E06)
                        ),
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("tool_rotate")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Rotate",
                            tint = if (selectedShapeIdx != null) Color(0xFFFFB142) else Color(0x55FFB142)
                        )
                    }
                    Text(
                        "Rotate",
                        fontSize = 10.sp,
                        color = if (selectedShapeIdx != null) Color(0xFFE5BA73) else Color(0x55E5BA73),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // UNDO LAST TURN MOVE
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { performUndo() },
                        enabled = canUndo,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF1C0E06),
                            disabledContainerColor = Color(0x331C0E06)
                        ),
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("tool_undo")
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Undo Last Move",
                            tint = if (canUndo) Color(0xFF48D1CC) else Color(0x5548D1CC)
                        )
                    }
                    Text(
                        "Undo",
                        fontSize = 10.sp,
                        color = if (canUndo) Color(0xFFE5BA73) else Color(0x55E5BA73),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // RESET / SWAP SHAPES suggestion options
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            saveBoardStateForUndo()
                            replenishTray()
                            verifyGameOverState()
                            SoundSynth.playPop()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF1C0E06)
                        ),
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("tool_swap")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Swap shapes suggestion",
                            tint = Color(0xFF20B2AA)
                        )
                    }
                    Text(
                        "Swap Pieces",
                        fontSize = 10.sp,
                        color = Color(0xFFE5BA73),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // FLOATING SELECTIVE HUD STATS MESSAGE
        if (isHammerPowerUpActive) {
            Surface(
                color = Color(0xFFE18743),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "🔨 HAMMER ACTIVE: Tap any filled tile grid cell to wipe it clean!",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // MULTI-TRAY 3 BLOCKS SUGGESTIONS LIST (BOTTOM SUGGESTIONS CONTAINER)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "TRAY SUGGESTIONS (Tap shape to select, then tap grid cell to place center!):",
            fontSize = 9.sp,
            color = Color(0xBBFFD700),
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (idx in 0 until 3) {
                val shapeItem = trayItems.getOrNull(idx)
                val isActiveSelected = selectedShapeIdx == idx

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .background(
                            if (isActiveSelected) Color(0x3BFFD700) else Color(0x0C000000),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.2.dp,
                            if (isActiveSelected) Color(0xFFFFB142) else Color(0x1Affffff),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (shapeItem != null) {
                                isHammerPowerUpActive = false
                                selectedShapeIdx = if (isActiveSelected) null else idx
                                SoundSynth.playPop()
                            }
                        }
                        .testTag("tray_suggestion_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    if (shapeItem != null) {
                        // Drawing miniature puzzle preview of suggestions centered inside the card
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Render mini shape grid representation
                            val maxR = shapeItem.coords.maxOf { it.first }
                            val maxC = shapeItem.coords.maxOf { it.second }
                            for (ri in 0..maxR) {
                                Row(horizontalArrangement = Arrangement.Center) {
                                    for (ci in 0..maxC) {
                                        val hasBlock = shapeItem.coords.any { it.first == ri && it.second == ci }
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .padding(1.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(
                                                    if (hasBlock) shapeItem.color else Color.Transparent
                                                )
                                                .border(
                                                    if (hasBlock) 0.5.dp else 0.dp,
                                                    if (hasBlock) Color.Black else Color.Transparent,
                                                    RoundedCornerShape(3.dp)
                                                )
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                shapeItem.name,
                                fontSize = 8.sp,
                                color = Color(0xFFA5B1C2),
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            "PLACED",
                            fontSize = 10.sp,
                            color = Color(0x44FFFFFF),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // ADVENTURE LEVELS NAVIGATION CONTROLLER (Map Pathway selector)
        if (woodSubTab == "adventure") {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E110A),
                border = BorderStroke(1.dp, Color(0xFF382014))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "ADV LEVEL PATHWAY MAP:",
                        fontSize = 10.sp,
                        color = Color(0xFFA5B1C2),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ADVENTURE_LEVELS.forEach { levelItem ->
                            val isCurrentActive = activeLevelId == levelItem.id
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isCurrentActive) Color(0xFFCD853F) else Color(0xFF4C3020),
                                        CircleShape
                                    )
                                    .clickable {
                                        activeLevelId = levelItem.id
                                    }
                                    .testTag("btn_select_level_${levelItem.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${levelItem.id}",
                                    color = if (isCurrentActive) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // GAME OVER LOSE MODAL OVERLAY
    if (isGameOver) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(
                    onClick = { restartPuzzleGame(woodSubTab == "adventure") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCD853F))
                ) {
                    Text("TRY AGAIN", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NO MOVES REMAINING!", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
            },
            text = {
                Column {
                    Text(
                        "Alas! No woody shapes on your suggestion tray can fit in any of the remaining slots on the board.",
                        color = Color(0xFFD2B48C)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your Final Score: $score", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            containerColor = Color(0xFF331E12),
            tonalElevation = 8.dp
        )
    }

    // LEVEL CLEAR SUCCESSFUL WIN MODAL OVERLAY
    if (reachedLevelWinModal) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(
                    onClick = {
                        if (activeLevelId < ADVENTURE_LEVELS.size) {
                            activeLevelId++
                        } else {
                            restartPuzzleGame(true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                ) {
                    Text("NEXT CHALLENGE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { restartPuzzleGame(true) }) {
                    Text("PLAY AGAIN", color = Color.White)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LEVEL COMPLETED!", style = MaterialTheme.typography.titleLarge, color = Color(0xFFFFD700))
                }
            },
            text = {
                Column {
                    Text(
                        "Incredible woody engineering craftsmanship! You have successfully completed this mountain range puzzle level target!",
                        color = Color(0xFFE5BA73)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(28.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(28.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Points Harvested: $score", fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Rows Cut Clear: $levelClearedLinesCount", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            containerColor = Color(0xFF2C190D),
            tonalElevation = 8.dp
        )
    }
}

// ----------------------------------------------------
// ORIGINAL 3D ISOMETRIC CREATIVE SANDBOX VIEW
// (Preserved perfectly to support both games!)
// ----------------------------------------------------
@Composable
fun VoxelCreativeBuilderView() {
    // Voxel screen dynamic constants
    var seed by remember { mutableStateOf(101) }
    var activePreset by remember { mutableStateOf(0) } // 0: Hills, 1: Islands, 2: Castle, 3: Caves, 4: Ruins
    
    // Camera metrics
    var yaw by remember { mutableStateOf(-0.65f) } 
    var pitch by remember { mutableStateOf(0.45f) } 
    var zoom by remember { mutableStateOf(42f) } 
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(80f) } 
    
    // Tools State
    var selectedBlockType by remember { mutableStateOf<Byte>(1) }
    var actionToolMode by remember { mutableStateOf("place") } 
    var isSettingsOpen by remember { mutableStateOf(false) }
    var activeVoxelCursor by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var cursorFaceNormal by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    
    // Environment values
    var dayNightFactor by remember { mutableStateOf(0.85f) } 
    var outlineWeight by remember { mutableStateOf(1.2f) } 
    var isFogEnabled by remember { mutableStateOf(true) }
    var benchmarkTrigger by remember { mutableStateOf(0) }
    
    // Benchmark reports
    var lastSortedCount by remember { mutableStateOf(0) }
    var cppTimeMicros by remember { mutableStateOf(0L) }
    var kotlinTimeMicros by remember { mutableStateOf(0L) }
    
    // Load default world on startup
    LaunchedEffect(seed, activePreset) {
        NativeVoxelEngine.initWorld(seed, activePreset)
        benchmarkTrigger++
    }

    // Benchmark depth-sorted voxels in real-time
    val sortedBlocks = remember(yaw, pitch, seed, activePreset, benchmarkTrigger) {
        val radius = 30f
        val camX = NativeVoxelEngine.WORLD_SIZE_X / 2f + radius * cos(yaw) * cos(pitch)
        val camY = NativeVoxelEngine.WORLD_SIZE_Y / 2f + radius * sin(pitch)
        val camZ = NativeVoxelEngine.WORLD_SIZE_Z / 2f + radius * sin(yaw) * cos(pitch)
        
        var cppResult = intArrayOf()
        val cTime = measureNanoTime {
            cppResult = NativeVoxelEngine.getSortedBlocks(camX, camY, camZ)
        }
        
        var ktTime = 0L
        if (benchmarkTrigger % 5 == 0) {
            ktTime = measureNanoTime {
                val list = mutableListOf<Triple<Int, Float, Int>>()
                for (x in 0 until NativeVoxelEngine.WORLD_SIZE_X) {
                    for (y in 0 until NativeVoxelEngine.WORLD_SIZE_Y) {
                        for (z in 0 until NativeVoxelEngine.WORLD_SIZE_Z) {
                            val type = NativeVoxelEngine.getBlock(x, y, z).toInt()
                            if (type > 0) {
                                val dx = (x + 0.5f) - camX
                                val dy = (y + 0.5f) - camY
                                val dz = (z + 0.5f) - camZ
                                val distSq = dx * dx + dy * dy + dz * dz
                                val packed = (x and 0xFF) or ((y and 0xFF) shl 8) or ((z and 0xFF) shl 16) or ((type and 0xFF) shl 24)
                                list.add(Triple(packed, distSq, packed))
                            }
                        }
                    }
                }
                list.sortByDescending { it.second }
            }
            kotlinTimeMicros = max(1L, ktTime / 1000L)
        }
        
        lastSortedCount = cppResult.size
        cppTimeMicros = max(1L, cTime / 1000L)
        cppResult
    }

    val skyBgBrush = remember(dayNightFactor) {
        val sunsetColor = Color(0xFFE65100)
        val daylightSky = Color(0xFF1E88E5)
        val spaceMidnight = Color(0xFF030712)
        
        when {
            dayNightFactor < 0.25f -> { 
                Brush.verticalGradient(listOf(Color(0xFF02040A), spaceMidnight, Color(0xFF0D111D)))
            }
            dayNightFactor < 0.5f -> { 
                Brush.verticalGradient(listOf(spaceMidnight, Color(0xFF451A03), sunsetColor))
            }
            else -> { 
                Brush.verticalGradient(listOf(Color(0xFF0D47A1), daylightSky, Color(0xFFBBDEFB)))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(skyBgBrush)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            yaw -= dragAmount.x * 0.005f
                            pitch = (pitch + dragAmount.y * 0.005f).coerceIn(0.1f, 1.4f)
                        }
                    )
                }
                .pointerInput(seed, activePreset, zoom, offsetX, offsetY, dayNightFactor, isFogEnabled, outlineWeight) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            var minDistance = Float.MAX_VALUE
                            var closestCoord: Triple<Int, Int, Int>? = null
                            
                            for (i in 0 until sortedBlocks.size) {
                                val packed = sortedBlocks[i]
                                val bX = packed and 0xFF
                                val bY = (packed shr 8) and 0xFF
                                val bZ = (packed shr 16) and 0xFF
                                
                                val centerProj = projectVoxel(
                                    x = bX + 0.5f,
                                    y = bY + 0.5f,
                                    z = bZ + 0.5f,
                                    width = size.width.toFloat(),
                                    height = size.height.toFloat(),
                                    yaw = yaw,
                                    pitch = pitch,
                                    zoom = zoom,
                                    offsetX = offsetX,
                                    offsetY = offsetY
                                )
                                
                                val dist = sqrt((offset.x - centerProj.x).pow(2) + (offset.y - centerProj.y).pow(2))
                                if (dist < minDistance && dist < zoom * 1.5f) {
                                    minDistance = dist
                                    closestCoord = Triple(bX, bY, bZ)
                                }
                            }
                            
                            if (closestCoord != null) {
                                activeVoxelCursor = closestCoord
                                val bX = closestCoord.first
                                val bY = closestCoord.second
                                val bZ = closestCoord.third
                                
                                val camX = NativeVoxelEngine.WORLD_SIZE_X / 2f + 30f * cos(yaw) * cos(pitch)
                                val camY = NativeVoxelEngine.WORLD_SIZE_Y / 2f + 30f * sin(pitch)
                                val camZ = NativeVoxelEngine.WORLD_SIZE_Z / 2f + 30f * sin(yaw) * cos(pitch)
                                
                                val dirX = bX + 0.5f - camX
                                val dirY = bY + 0.5f - camY
                                val dirZ = bZ + 0.5f - camZ
                                
                                val rayResult = NativeVoxelEngine.raycast(
                                    startX = camX, startY = camY, startZ = camZ,
                                    dirX = dirX, dirY = dirY, dirZ = dirZ,
                                    maxDist = 100f
                                )
                                if (rayResult[6] == 1) { 
                                    cursorFaceNormal = Triple(rayResult[3], rayResult[4], rayResult[5])
                                } else {
                                    cursorFaceNormal = Triple(0, 1, 0) 
                                }
                            } else {
                                activeVoxelCursor = null
                                cursorFaceNormal = null
                            }
                        },
                        onDrag = { _, _ -> }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val boundsColor = if (dayNightFactor > 0.3f) Color(0x33455A64) else Color(0x4490A4AE)
            drawWorldBoundaryCube(width, height, yaw, pitch, zoom, offsetX, offsetY, boundsColor)

            for (i in 0 until sortedBlocks.size) {
                val packed = sortedBlocks[i]
                val bX = packed and 0xFF
                val bY = (packed shr 8) and 0xFF
                val bZ = (packed shr 16) and 0xFF
                val bType = ((packed shr 24) and 0xFF).toByte()
                
                val blockModel = VOXEL_TYPES[bType] ?: continue
                
                val shadowFactor = if (isFogEnabled) {
                    val distMultiplier = 1f - (i.toFloat() / sortedBlocks.size * 0.35f)
                    distMultiplier.coerceIn(0.5f, 1f)
                } else 1f
                
                val shadedTop = colorMultiply(blockModel.topColor, dayNightFactor * shadowFactor)
                val shadedSide = colorMultiply(blockModel.sideColor, dayNightFactor * 0.82f * shadowFactor)
                val shadedFront = colorMultiply(blockModel.frontColor, dayNightFactor * 0.65f * shadowFactor)
                
                val selected = activeVoxelCursor?.let { it.first == bX && it.second == bY && it.third == bZ } ?: false

                drawVoxelCube(
                    x = bX, y = bY, z = bZ,
                    width = width, height = height,
                    yaw = yaw, pitch = pitch, zoom = zoom,
                    offsetX = offsetX, offsetY = offsetY,
                    topColor = shadedTop,
                    sideColor = shadedSide,
                    frontColor = shadedFront,
                    outlineWeight = outlineWeight,
                    isSelected = selected
                )
            }

            // Draw selection face highlight outline overlay
            val cursor = activeVoxelCursor
            val normal = cursorFaceNormal
            if (cursor != null && normal != null) {
                drawCursorHighlightFace(
                    cx = cursor.first, cy = cursor.second, cz = cursor.third,
                    nx = normal.first, ny = normal.second, nz = normal.third,
                    width = width, height = height,
                    yaw = yaw, pitch = pitch, zoom = zoom,
                    offsetX = offsetX, offsetY = offsetY
                )
            }
        }

        // CONTROL OVERLAYS HUD BAR
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2090F1B))))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xD90F172A),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Native performance telemetry logs",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "NATIVE C++: ${cppTimeMicros}μs",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "JVM: ${kotlinTimeMicros}μs",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = Color(0xFFD1D8E0)
                            )
                        }
                    }
                    Text(
                        text = "VOXELS: $lastSortedCount",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = Color(0xFFA5B1C2)
                    )
                }
            }

            // Interactive Tool Actions Panel row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Brush color selection strip list
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xD91E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(VOXEL_TYPES.keys.toList()) { bId ->
                            val model = VOXEL_TYPES[bId]
                            if (model != null) {
                                val activeVal = selectedBlockType == bId
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            model.topColor,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            2.dp,
                                            if (activeVal) Color.White else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            selectedBlockType = bId
                                        }
                                )
                            }
                        }
                    }
                }

                // Settings gears button
                IconButton(
                    onClick = { isSettingsOpen = true },
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings menu", tint = Color.White)
                }
            }

            // Placing and Erasing trigger action row buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val cursor = activeVoxelCursor
                        val normal = cursorFaceNormal
                        if (cursor != null && normal != null) {
                            val px = cursor.first + normal.first
                            val py = cursor.second + normal.second
                            val pz = cursor.third + normal.third
                            NativeVoxelEngine.setBlock(px, py, pz, selectedBlockType)
                            benchmarkTrigger++
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PLACE BLOCK", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        val cursor = activeVoxelCursor
                        if (cursor != null) {
                            NativeVoxelEngine.setBlock(cursor.first, cursor.second, cursor.third, 0)
                            activeVoxelCursor = null
                            benchmarkTrigger++
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("DEMOLISH", fontSize = 11.sp)
                }
            }
        }
    }

    // Settings overlay dialog popup
    if (isSettingsOpen) {
        AlertDialog(
            onDismissRequest = { isSettingsOpen = false },
            confirmButton = {},
            title = {
                Text("Voxel Sandbox Settings", color = Color.White)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("Lighting level: ${dayNightFactor}", color = Color.White)
                    Slider(
                        value = dayNightFactor,
                        onValueChange = { dayNightFactor = it }
                    )

                    Button(
                        onClick = {
                            seed = Random.nextInt(100, 9999)
                            isSettingsOpen = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                    ) {
                        Text("MUTATE SEED", color = Color.White)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fog Rendering:", color = Color.White)
                        Switch(checked = isFogEnabled, onCheckedChange = { isFogEnabled = it })
                    }
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

// ----------------------------------------------------
// HELPER DRAW SEGMENT MAPS (3D PROJECTION ENGINE)
// ----------------------------------------------------
private fun projectVoxel(
    x: Float, y: Float, z: Float,
    width: Float, height: Float,
    yaw: Float, pitch: Float,
    zoom: Float,
    offsetX: Float, offsetY: Float
): Offset {
    val rx = x - NativeVoxelEngine.WORLD_SIZE_X / 2f
    val ry = y - NativeVoxelEngine.WORLD_SIZE_Y / 2f
    val rz = z - NativeVoxelEngine.WORLD_SIZE_Z / 2f

    val cosY = cos(yaw)
    val sinY = sin(yaw)
    val rotX = rx * cosY - rz * sinY
    val rotZ = rx * sinY + rz * cosY

    val cosP = cos(pitch)
    val sinP = sin(pitch)
    val projX = rotX
    val projY = ry * cosP - rotZ * sinP

    val u = (width / 2f) + (projX * zoom) + offsetX
    val v = (height / 2f) - (projY * zoom) + offsetY
    return Offset(u, v)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVoxelCube(
    x: Int, y: Int, z: Int,
    width: Float, height: Float,
    yaw: Float, pitch: Float,
    zoom: Float,
    offsetX: Float, offsetY: Float,
    topColor: Color,
    sideColor: Color,
    frontColor: Color,
    outlineWeight: Float,
    isSelected: Boolean
) {
    val x0 = x.toFloat()
    val x1 = x0 + 1f
    val y0 = y.toFloat()
    val y1 = y0 + 1f
    val z0 = z.toFloat()
    val z1 = z0 + 1f

    val p1 = projectVoxel(x0, y0, z0, width, height, yaw, pitch, zoom, offsetX, offsetY)
    val p2 = projectVoxel(x1, y0, z0, width, height, yaw, pitch, zoom, offsetX, offsetY)
    val p3 = projectVoxel(x1, y1, z0, width, height, yaw, pitch, zoom, offsetX, offsetY)
    val p4 = projectVoxel(x0, y1, z0, width, height, yaw, pitch, zoom, offsetX, offsetY)
    
    val p5 = projectVoxel(x0, y0, z1, width, height, yaw, pitch, zoom, offsetX, offsetY)
    val p6 = projectVoxel(x1, y0, z1, width, height, yaw, pitch, zoom, offsetX, offsetY)
    val p7 = projectVoxel(x1, y1, z1, width, height, yaw, pitch, zoom, offsetX, offsetY)
    val p8 = projectVoxel(x0, y1, z1, width, height, yaw, pitch, zoom, offsetX, offsetY)

    val topPath = Path().apply {
        moveTo(p3.x, p3.y)
        lineTo(p4.x, p4.y)
        lineTo(p8.x, p8.y)
        lineTo(p7.x, p7.y)
        close()
    }

    val leftPath = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p4.x, p4.y)
        lineTo(p8.x, p8.y)
        lineTo(p5.x, p5.y)
        close()
    }

    val rightPath = Path().apply {
        moveTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        lineTo(p7.x, p7.y)
        lineTo(p6.x, p6.y)
        close()
    }

    drawPath(path = topPath, color = topColor)
    drawPath(path = leftPath, color = sideColor)
    drawPath(path = rightPath, color = frontColor)

    if (isSelected) {
        drawPath(path = topPath, color = Color(0x3B10B981))
        drawPath(path = leftPath, color = Color(0x2B10B981))
        drawPath(path = rightPath, color = Color(0x2B10B981))
    }

    if (outlineWeight > 0.1f) {
        val strokeColor = if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B)
        val styleStroke = Stroke(width = outlineWeight)
        drawPath(path = topPath, color = strokeColor, style = styleStroke)
        drawPath(path = leftPath, color = strokeColor, style = styleStroke)
        drawPath(path = rightPath, color = strokeColor, style = styleStroke)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorldBoundaryCube(
    width: Float, height: Float,
    yaw: Float, pitch: Float, zoom: Float,
    offsetX: Float, offsetY: Float,
    color: Color
) {
    val sx = NativeVoxelEngine.WORLD_SIZE_X.toFloat()
    val sy = NativeVoxelEngine.WORLD_SIZE_Y.toFloat()
    val sz = NativeVoxelEngine.WORLD_SIZE_Z.toFloat()

    val corners = arrayOf(
        projectVoxel(0f, 0f, 0f, width, height, yaw, pitch, zoom, offsetX, offsetY),
        projectVoxel(sx, 0f, 0f, width, height, yaw, pitch, zoom, offsetX, offsetY),
        projectVoxel(sx, sy, 0f, width, height, yaw, pitch, zoom, offsetX, offsetY),
        projectVoxel(0f, sy, 0f, width, height, yaw, pitch, zoom, offsetX, offsetY),
        projectVoxel(0f, 0f, sz, width, height, yaw, pitch, zoom, offsetX, offsetY),
        projectVoxel(sx, 0f, sz, width, height, yaw, pitch, zoom, offsetX, offsetY),
        projectVoxel(sx, sy, sz, width, height, yaw, pitch, zoom, offsetX, offsetY),
        projectVoxel(0f, sy, sz, width, height, yaw, pitch, zoom, offsetX, offsetY)
    )

    val edges = arrayOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 0, 
        4 to 5, 5 to 6, 6 to 7, 7 to 4, 
        0 to 4, 1 to 5, 2 to 6, 3 to 7  
    )

    for ((u, v) in edges) {
        drawLine(
            color = color,
            start = corners[u],
            end = corners[v],
            strokeWidth = 2f
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCursorHighlightFace(
    cx: Int, cy: Int, cz: Int,
    nx: Int, ny: Int, nz: Int,
    width: Float, height: Float,
    yaw: Float, pitch: Float,
    zoom: Float,
    offsetX: Float, offsetY: Float
) {
    val fx0: Float
    val fy0: Float
    val fz0: Float
    
    when {
        nx != 0 -> { 
            fx0 = cx.toFloat() + if (nx > 0) 1f else 0f
            fy0 = cy.toFloat()
            fz0 = cz.toFloat()
            
            val p1 = projectVoxel(fx0, fy0, fz0, width, height, yaw, pitch, zoom, offsetX, offsetY)
            val p2 = projectVoxel(fx0, fy0 + 1f, fz0, width, height, yaw, pitch, zoom, offsetX, offsetY)
            val p3 = projectVoxel(fx0, fy0 + 1f, fz0 + 1f, width, height, yaw, pitch, zoom, offsetX, offsetY)
            val p4 = projectVoxel(fx0, fy0, fz0 + 1f, width, height, yaw, pitch, zoom, offsetX, offsetY)
            
            drawFaceOutline(p1, p2, p3, p4, Color(0xFF10B981))
        }
        ny != 0 -> { 
            fy0 = cy.toFloat() + if (ny > 0) 1f else 0f
            fx0 = cx.toFloat()
            fz0 = cz.toFloat()
            
            val p1 = projectVoxel(fx0, fy0, fz0, width, height, yaw, pitch, zoom, offsetX, offsetY)
            val p2 = projectVoxel(fx0 + 1f, fy0, fz0, width, height, yaw, pitch, zoom, offsetX, offsetY)
            val p3 = projectVoxel(fx0 + 1f, fy0, fz0 + 1f, width, height, yaw, pitch, zoom, offsetX, offsetY)
            val p4 = projectVoxel(fx0, fy0, fz0 + 1f, width, height, yaw, pitch, zoom, offsetX, offsetY)
            
            drawFaceOutline(p1, p2, p3, p4, Color(0xFF10B981))
        }
        else -> { 
            fz0 = cz.toFloat() + if (nz > 0) 1f else 0f
            fx0 = cx.toFloat()
            fy0 = cy.toFloat()
            
            val p1 = projectVoxel(fx0, fy0, fz0, width, height, yaw, pitch, zoom, offsetX, offsetY)
            val p2 = projectVoxel(fx0 + 1f, fy0, fz0, width, height, yaw, pitch, zoom, offsetX, offsetY)
            val p3 = projectVoxel(fx0 + 1f, fy0 + 1f, fz0, width, height, yaw, pitch, zoom, offsetX, offsetY)
            val p4 = projectVoxel(fx0, fy0 + 1f, fz0, width, height, yaw, pitch, zoom, offsetX, offsetY)
            
            drawFaceOutline(p1, p2, p3, p4, Color(0xFF10B981))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFaceOutline(
    p1: Offset, p2: Offset, p3: Offset, p4: Offset, color: Color
) {
    val path = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        lineTo(p4.x, p4.y)
        close()
    }
    drawPath(path = path, color = color, style = Stroke(width = 3.5f))
    drawPath(path = path, color = color.copy(alpha = 0.2f))
}

private fun colorMultiply(color: Color, factor: Float): Color {
    val f = factor.coerceIn(0f, 1f)
    return Color(
        red = (color.red * f).coerceIn(0f, 1f),
        green = (color.green * f).coerceIn(0f, 1f),
        blue = (color.blue * f).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}
