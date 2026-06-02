package com.example

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object NativeVoxelEngine {
    var isNativeLoaded = false
        private set

    init {
        try {
            System.loadLibrary("native-lib")
            isNativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            println("NativeVoxelEngine JNI library failed to load; using Kotlin fallback engine. Error: ${e.message}")
        }
    }

    // World size bounds matched with C++ native core constants
    const val WORLD_SIZE_X = 24
    const val WORLD_SIZE_Y = 24
    const val WORLD_SIZE_Z = 24

    // Fallback Kotlin state arrays
    private val fallbackWorld = Array(WORLD_SIZE_X) { Array(WORLD_SIZE_Y) { ByteArray(WORLD_SIZE_Z) } }

    fun initWorld(seed: Int, preset: Int) {
        if (isNativeLoaded) {
            try {
                initWorldNative(seed, preset)
                return
            } catch (e: UnsatisfiedLinkError) {
                // Ignore and use local fallback
            }
        }
        
        // Pure Kotlin voxel world generation fallback
        for (x in 0 until WORLD_SIZE_X) {
            for (y in 0 until WORLD_SIZE_Y) {
                for (z in 0 until WORLD_SIZE_Z) {
                    fallbackWorld[x][y][z] = 0
                }
            }
        }

        when (preset) {
            0 -> { // Rolling Hills
                for (x in 0 until WORLD_SIZE_X) {
                    for (z in 0 until WORLD_SIZE_Z) {
                        val noise = sin(x * 0.2f + seed) * cos(z * 0.2f + seed * 1.5f) * 3f
                        var height = (WORLD_SIZE_Y / 2f + noise).toInt()
                        if (height < 1) height = 1
                        if (height >= WORLD_SIZE_Y) height = WORLD_SIZE_Y - 1
                        for (y in 0 until height) {
                            fallbackWorld[x][y][z] = when {
                                y == height - 1 -> 1 // Grass
                                y >= height - 3 -> 2 // Dirt
                                else -> 3            // Stone
                            }
                        }
                    }
                }
            }
            1 -> { // Floating Islands
                for (x in 0 until WORLD_SIZE_X) {
                    for (y in 0 until WORLD_SIZE_Y) {
                        for (z in 0 until WORLD_SIZE_Z) {
                            val v = sin(x * 0.35f + seed) * cos(y * 0.4f) * sin(z * 0.35f) +
                                    cos(x * 0.2f) * sin(y * 0.2f) * cos(z * 0.2f)
                            if (v > 0.25f && y >= 4 && y <= WORLD_SIZE_Y - 4) {
                                fallbackWorld[x][y][z] = if (v > 0.45f) 8 else 10 // Neon Orange or Brick
                            }
                        }
                    }
                }
                for (x in 0 until WORLD_SIZE_X) {
                    for (z in 0 until WORLD_SIZE_Z) {
                        fallbackWorld[x][0][z] = 3
                    }
                }
            }
            2 -> { // Castle / Towers
                for (x in 0 until WORLD_SIZE_X) {
                    for (z in 0 until WORLD_SIZE_Z) {
                        fallbackWorld[x][0][z] = 1
                    }
                }
                val minC = 3
                val maxC = WORLD_SIZE_X - 4
                val wallH = 5
                for (x in minC..maxC) {
                    for (y in 1..wallH) {
                        fallbackWorld[x][y][minC] = 6
                        fallbackWorld[x][y][maxC] = 6
                    }
                }
                for (z in minC..maxC) {
                    for (y in 1..wallH) {
                        fallbackWorld[minC][y][z] = 6
                        fallbackWorld[maxC][y][z] = 6
                    }
                }
                for (y in 1..3) {
                    fallbackWorld[WORLD_SIZE_X / 2][y][minC] = 0 // doors
                }
                val pillars = arrayOf(
                    intArrayOf(minC, minC), intArrayOf(minC, maxC),
                    intArrayOf(maxC, minC), intArrayOf(maxC, maxC)
                )
                for (p in pillars) {
                    for (y in 1..wallH + 3) {
                        fallbackWorld[p[0]][y][p[1]] = 3
                        if (y == wallH + 3) fallbackWorld[p[0]][y][p[1]] = 9 // Neon Cyan
                    }
                }
            }
            3 -> { // Stone Caves / Columns
                for (x in 0 until WORLD_SIZE_X) {
                    for (y in 0 until WORLD_SIZE_Y) {
                        for (z in 0 until WORLD_SIZE_Z) {
                            val cx = x - WORLD_SIZE_X / 2f
                            val cy = y - WORLD_SIZE_Y / 2f
                            val cz = z - WORLD_SIZE_Z / 2f
                            val dist = sqrt(cx * cx + cy * cy + cz * cz)
                            if (dist > 9f) {
                                fallbackWorld[x][y][z] = 3
                            } else if (dist < 6f) {
                                fallbackWorld[x][y][z] = 0
                            } else if (y < 4) {
                                fallbackWorld[x][y][z] = 2
                            } else {
                                if (x % 4 == 0 && z % 4 == 0) {
                                    fallbackWorld[x][y][z] = 6
                                }
                            }
                        }
                    }
                }
            }
            4 -> { // Golden Ruins / Aztec Steps
                for (x in 0 until WORLD_SIZE_X) {
                    for (z in 0 until WORLD_SIZE_Z) {
                        fallbackWorld[x][0][z] = 1
                    }
                }
                val levels = 7
                for (r in 0 until levels) {
                    val start = r + 1
                    val end = WORLD_SIZE_X - 2 - r
                    val y = r + 1
                    for (x in start..end) {
                        for (z in start..end) {
                            fallbackWorld[x][y][z] = if ((x + z + y) % 3 == 0) 7 else 6
                        }
                    }
                }
            }
            else -> { // Simple Grass Flat Land
                for (x in 0 until WORLD_SIZE_X) {
                    for (z in 0 until WORLD_SIZE_Z) {
                        fallbackWorld[x][0][z] = 1
                    }
                }
            }
        }
    }

    fun setBlock(x: Int, y: Int, z: Int, type: Byte) {
        if (isNativeLoaded) {
            try {
                setBlockNative(x, y, z, type)
                return
            } catch (e: UnsatisfiedLinkError) {
                // Ignore and fall back
            }
        }
        if (x in 0 until WORLD_SIZE_X && y in 0 until WORLD_SIZE_Y && z in 0 until WORLD_SIZE_Z) {
            fallbackWorld[x][y][z] = type
        }
    }

    fun getBlock(x: Int, y: Int, z: Int): Byte {
        if (isNativeLoaded) {
            try {
                return getBlockNative(x, y, z)
            } catch (e: UnsatisfiedLinkError) {
                // Ignore and fall back
            }
        }
        if (x in 0 until WORLD_SIZE_X && y in 0 until WORLD_SIZE_Y && z in 0 until WORLD_SIZE_Z) {
            return fallbackWorld[x][y][z]
        }
        return 0
    }

    fun getSortedBlocks(camX: Float, camY: Float, camZ: Float): IntArray {
        if (isNativeLoaded) {
            try {
                return getSortedBlocksNative(camX, camY, camZ)
            } catch (e: UnsatisfiedLinkError) {
                // Ignore and fall back
            }
        }

        // JVM sorting fallback
        val list = mutableListOf<Triple<IntArray, Float, Int>>()
        for (x in 0 until WORLD_SIZE_X) {
            for (y in 0 until WORLD_SIZE_Y) {
                for (z in 0 until WORLD_SIZE_Z) {
                    val type = fallbackWorld[x][y][z].toInt()
                    if (type > 0) {
                        val dx = (x + 0.5f) - camX
                        val dy = (y + 0.5f) - camY
                        val dz = (z + 0.5f) - camZ
                        val distSq = dx * dx + dy * dy + dz * dz
                        val packed = (x and 0xFF) or ((y and 0xFF) shl 8) or ((z and 0xFF) shl 16) or ((type and 0xFF) shl 24)
                        list.add(Triple(intArrayOf(x, y, z), distSq, packed))
                    }
                }
            }
        }
        list.sortByDescending { it.second }
        return list.map { it.third }.toIntArray()
    }

    fun raycast(
        startX: Float, startY: Float, startZ: Float,
        dirX: Float, dirY: Float, dirZ: Float,
        maxDist: Float
    ): IntArray {
        if (isNativeLoaded) {
            try {
                return raycastNative(startX, startY, startZ, dirX, dirY, dirZ, maxDist)
            } catch (e: UnsatisfiedLinkError) {
                // Ignore and fall back
            }
        }

        // JVM stepped-raycast calculation fallback
        val hitData = intArrayOf(-1, -1, -1, 0, 0, 0, 0)
        var dirXN = dirX
        var dirYN = dirY
        var dirZN = dirZ
        val len = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        if (len > 0.0001f) {
            dirXN /= len
            dirYN /= len
            dirZN /= len
        }

        val step = 0.05f
        var currentDist = 0.0f
        var lastX = -1
        var lastY = -1
        var lastZ = -1

        while (currentDist <= maxDist) {
            val px = startX + dirXN * currentDist
            val py = startY + dirYN * currentDist
            val pz = startZ + dirZN * currentDist

            val ix = kotlin.math.floor(px).toInt()
            val iy = kotlin.math.floor(py).toInt()
            val iz = kotlin.math.floor(pz).toInt()

            if (ix in 0 until WORLD_SIZE_X && iy in 0 until WORLD_SIZE_Y && iz in 0 until WORLD_SIZE_Z) {
                val type = fallbackWorld[ix][iy][iz].toInt()
                if (type > 0) {
                    hitData[0] = ix
                    hitData[1] = iy
                    hitData[2] = iz
                    hitData[6] = 1 // Hit indicator flag

                    if (lastX >= 0 && (lastX != ix || lastY != iy || lastZ != iz)) {
                        hitData[3] = lastX - ix
                        hitData[4] = lastY - iy
                        hitData[5] = lastZ - iz
                    }
                    break
                }
            }
            lastX = ix
            lastY = iy
            lastZ = iz
            currentDist += step
        }
        return hitData
    }

    // ============================================
    // WOODEN PUZZLE JNI BRIDGE & FALLBACK
    // ============================================

    private val fallbackPuzzleBoard = Array(8) { IntArray(8) }

    fun initPuzzleGame() {
        if (isNativeLoaded) {
            try {
                initPuzzleGameNative()
                return
            } catch (e: UnsatisfiedLinkError) {
                // fall back
            }
        }
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                fallbackPuzzleBoard[r][c] = 0
            }
        }
    }

    fun getPuzzleBoardCell(r: Int, c: Int): Int {
        if (isNativeLoaded) {
            try {
                return getPuzzleBoardCellNative(r, c)
            } catch (e: UnsatisfiedLinkError) {
                // fall back
            }
        }
        return if (r in 0..7 && c in 0..7) fallbackPuzzleBoard[r][c] else 0
    }

    fun setPuzzleBoardCell(r: Int, c: Int, valNum: Int) {
        if (isNativeLoaded) {
            try {
                setPuzzleBoardCellNative(r, c, valNum)
                return
            } catch (e: UnsatisfiedLinkError) {
                // fall back
            }
        }
        if (r in 0..7 && c in 0..7) {
            fallbackPuzzleBoard[r][c] = valNum
        }
    }

    fun canPlaceShape(shapeCoords: IntArray, r: Int, c: Int): Boolean {
        if (isNativeLoaded) {
            try {
                return canPlaceShapeNative(shapeCoords, r, c)
            } catch (e: UnsatisfiedLinkError) {
                // fall back
            }
        }
        if (shapeCoords.size % 2 != 0) return false
        for (i in shapeCoords.indices step 2) {
            val pr = r + shapeCoords[i]
            val pc = c + shapeCoords[i + 1]
            if (pr !in 0..7 || pc !in 0..7 || fallbackPuzzleBoard[pr][pc] != 0) {
                return false
            }
        }
        return true
    }

    fun placeShape(shapeCoords: IntArray, r: Int, c: Int, cellValue: Int): Boolean {
        if (isNativeLoaded) {
            try {
                return placeShapeNative(shapeCoords, r, c, cellValue)
            } catch (e: UnsatisfiedLinkError) {
                // fall back
            }
        }
        if (!canPlaceShape(shapeCoords, r, c)) return false
        for (i in shapeCoords.indices step 2) {
            val pr = r + shapeCoords[i]
            val pc = c + shapeCoords[i + 1]
            fallbackPuzzleBoard[pr][pc] = cellValue
        }
        return true
    }

    fun clearLines(): Int {
        if (isNativeLoaded) {
            try {
                return clearLinesNative()
            } catch (e: UnsatisfiedLinkError) {
                // fall back
            }
        }
        val fullRows = BooleanArray(8)
        val fullCols = BooleanArray(8)
        var clearedCount = 0

        for (r in 0 until 8) {
            var rowFull = true
            for (c in 0 until 8) {
                if (fallbackPuzzleBoard[r][c] == 0) {
                    rowFull = false
                    break
                }
            }
            if (rowFull) {
                fullRows[r] = true
                clearedCount++
            }
        }

        for (c in 0 until 8) {
            var colFull = true
            for (r in 0 until 8) {
                if (fallbackPuzzleBoard[r][c] == 0) {
                    colFull = false
                    break
                }
            }
            if (colFull) {
                fullCols[c] = true
                clearedCount++
            }
        }

        var mask = 0
        for (r in 0 until 8) {
            if (fullRows[r]) {
                mask = mask or (1 shl r)
                for (c in 0 until 8) {
                    fallbackPuzzleBoard[r][c] = 0
                }
            }
        }

        for (c in 0 until 8) {
            if (fullCols[c]) {
                mask = mask or (1 shl (c + 8))
                for (r in 0 until 8) {
                    fallbackPuzzleBoard[r][c] = 0
                }
            }
        }

        return mask or (clearedCount shl 16)
    }

    fun checkShapeFitsAnywhere(shapeCoords: IntArray): Boolean {
        if (isNativeLoaded) {
            try {
                return checkShapeFitsAnywhereNative(shapeCoords)
            } catch (e: UnsatisfiedLinkError) {
                // fall back
            }
        }
        if (shapeCoords.size % 2 != 0) return false
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                var fits = true
                for (i in shapeCoords.indices step 2) {
                    val pr = r + shapeCoords[i]
                    val pc = c + shapeCoords[i + 1]
                    if (pr !in 0..7 || pc !in 0..7 || fallbackPuzzleBoard[pr][pc] != 0) {
                        fits = false
                        break
                    }
                }
                if (fits) return true
            }
        }
        return false
    }

    // Raw external JNI entry points
    @JvmStatic
    @JvmName("initWorldNative")
    private external fun initWorldNative(seed: Int, preset: Int)

    @JvmStatic
    @JvmName("setBlockNative")
    private external fun setBlockNative(x: Int, y: Int, z: Int, type: Byte)

    @JvmStatic
    @JvmName("getBlockNative")
    private external fun getBlockNative(x: Int, y: Int, z: Int): Byte

    @JvmStatic
    @JvmName("getSortedBlocksNative")
    private external fun getSortedBlocksNative(camX: Float, camY: Float, camZ: Float): IntArray

    @JvmStatic
    @JvmName("raycastNative")
    private external fun raycastNative(
        startX: Float, startY: Float, startZ: Float,
        dirX: Float, dirY: Float, dirZ: Float,
        maxDist: Float
    ): IntArray

    // Puzzle native methods
    @JvmStatic
    private external fun initPuzzleGameNative()

    @JvmStatic
    private external fun getPuzzleBoardCellNative(r: Int, c: Int): Int

    @JvmStatic
    private external fun setPuzzleBoardCellNative(r: Int, c: Int, valNum: Int)

    @JvmStatic
    private external fun canPlaceShapeNative(shapeCoords: IntArray, r: Int, c: Int): Boolean

    @JvmStatic
    private external fun placeShapeNative(shapeCoords: IntArray, r: Int, c: Int, cellValue: Int): Boolean

    @JvmStatic
    private external fun clearLinesNative(): Int

    @JvmStatic
    private external fun checkShapeFitsAnywhereNative(shapeCoords: IntArray): Boolean
}
