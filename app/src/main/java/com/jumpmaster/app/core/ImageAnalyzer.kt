package com.jumpmaster.app.core

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Core image analysis algorithm ported from Python wechat_jump tool.
 * Detects the game piece (棋子) and target platform (目标平台) positions,
 * then calculates the distance and required press duration.
 */
class ImageAnalyzer(private var config: DeviceConfig = DeviceConfig()) {

    companion object {
        private const val TAG = "ImageAnalyzer"
    }

    data class AnalysisResult(
        val pieceX: Int,
        val pieceY: Int,
        val boardX: Int,
        val boardY: Int,
        val distance: Float,
        val pressTimeMs: Long,
        val analysisTimeMs: Long
    )

    private data class OriginalDetection(
        val pieceX: Int,
        val pieceY: Int,
        val boardX: Int,
        val boardY: Int,
        val deltaPieceY: Int
    )

    fun updateConfig(config: DeviceConfig) {
        this.config = config
    }

    /**
     * Main analysis entry point.
     * Finds piece position and board position, calculates press duration.
     */
    fun analyze(bitmap: Bitmap): AnalysisResult? {
        val startTime = System.currentTimeMillis()
        val width = bitmap.width
        val height = bitmap.height
        Log.d(
            TAG,
            "analyze: bitmap=${width}x${height}, config=scoreY=${config.underGameScoreY} " +
                    "coeff=${config.pressCoefficient} head=${config.headDiameter}"
        )

        // Extract pixel data for fast access
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Log some sample pixels for debugging
        val cx = width / 2
        val cy = height / 2
        val samples = listOf(
            "center($cx,$cy)" to pixels[cy * width + cx],
            "top-center($cx,${height/4})" to pixels[(height/4) * width + cx],
            "bot-center($cx,${height*3/4})" to pixels[(height*3/4) * width + cx],
            "left(${width/4},$cy)" to pixels[cy * width + width/4],
            "right(${width*3/4},$cy)" to pixels[cy * width + width*3/4]
        )
        for ((label, px) in samples) {
            Log.d(TAG, "  pixel $label: R=${Color.red(px)} G=${Color.green(px)} B=${Color.blue(px)}")
        }

        val original = findPieceAndBoardOriginal(pixels, width, height)
        if (original != null) {
            val dx = (original.pieceX - original.boardX).toFloat()
            val dy = (original.pieceY - original.boardY).toFloat()
            val distance = sqrt(dx * dx + dy * dy)
            val pressTime = JumpTiming.calculatePressTimeMs(
                distance = distance,
                deltaPieceY = original.deltaPieceY,
                pressCoefficient = config.pressCoefficient,
                headDiameter = config.headDiameter
            )
            val elapsed = System.currentTimeMillis() - startTime

            Log.i(
                TAG,
                "Original detection: piece=(${original.pieceX}, ${original.pieceY}), " +
                        "board=(${original.boardX}, ${original.boardY}), distance=$distance, " +
                        "delta=${original.deltaPieceY}, press=$pressTime"
            )
            return AnalysisResult(
                pieceX = original.pieceX,
                pieceY = original.pieceY,
                boardX = original.boardX,
                boardY = original.boardY,
                distance = distance,
                pressTimeMs = pressTime,
                analysisTimeMs = elapsed
            )
        }

        val piece = findPiece(pixels, width, height)
        if (piece == null) {
            Log.w(TAG, "findPiece returned null")
            return null
        }
        Log.i(TAG, "Piece found at (${piece.first}, ${piece.second})")

        val board = findBoard(pixels, width, height, piece)
        if (board == null) {
            Log.w(TAG, "findBoard returned null")
            return analyzeByPlatforms(pixels, width, height, startTime)
        }
        Log.i(TAG, "Board found at (${board.first}, ${board.second})")

        val dx = (piece.first - board.first).toFloat()
        val dy = (piece.second - board.second).toFloat()
        val distance = sqrt(dx * dx + dy * dy)
        val pressTime = JumpTiming.calculatePressTimeMs(
            distance = distance,
            deltaPieceY = 0,
            pressCoefficient = config.pressCoefficient,
            headDiameter = config.headDiameter
        )

        if (isLikelyBadDetection(width, height, piece, board, distance)) {
            Log.w(TAG, "Classic detection looks invalid, trying platform fallback")
            analyzeByPlatforms(pixels, width, height, startTime)?.let { return it }
        }

        val elapsed = System.currentTimeMillis() - startTime

        return AnalysisResult(
            pieceX = piece.first,
            pieceY = piece.second,
            boardX = board.first,
            boardY = board.second,
            distance = distance,
            pressTimeMs = pressTime,
            analysisTimeMs = elapsed
        )
    }

    /**
     * Find the game piece (棋子).
     * The piece has a red/dark top circle and a black cylindrical base.
     * We scan for the distinctive color pattern of the piece top.
     */
    private fun findPiece(pixels: IntArray, width: Int, height: Int): Pair<Int, Int>? {
        val pieceTopCandidates = mutableListOf<Pair<Int, Int>>()

        // Scan from bottom portion of screen (piece is usually in lower half)
        val scanStartY = (height * 0.3).toInt()
        val scanEndY = (height * 0.85).toInt()

        for (y in scanStartY until scanEndY) {
            for (x in 50 until width - 50) {
                val idx = y * width + x
                val pixel = pixels[idx]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Piece top has a distinctive red/brown color
                // The piece body is dark (low RGB) with slight reddish tint
                if (isPieceColor(r, g, b)) {
                    // Check if surrounding pixels match piece pattern
                    if (isPieceRegion(pixels, x, y, width, height)) {
                        pieceTopCandidates.add(x to y)
                    }
                }
            }
        }

        Log.d(TAG, "findPiece: scanned ${scanStartY}..${scanEndY}, candidates=${pieceTopCandidates.size}")

        if (pieceTopCandidates.isEmpty()) {
            Log.d(TAG, "findPiece: no color candidates, trying base detection fallback")
            val fallback = findPieceByBase(pixels, width, height)
            Log.d(TAG, "findPiece fallback result: ${fallback?.first}, ${fallback?.second}")
            return fallback
        }

        // Find the center of the piece top region
        val avgX = pieceTopCandidates.map { it.first }.average().toInt()
        val avgY = pieceTopCandidates.map { it.second }.average().toInt()

        // Refine: find the bottom of the piece (where it meets the platform)
        var bottomY = avgY
        for (y in (avgY + 1)..(avgY + config.pieceBodyWidth).coerceAtMost(height - 1)) {
            val idx = y * width + avgX
            val pixel = pixels[idx]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            if (r + g + b < 100) {
                // Still dark (piece body)
                bottomY = y
            } else {
                break
            }
        }

        return avgX to (avgY + config.pieceBaseHeight12)
    }

    /**
     * Check if pixel color matches the game piece.
     */
    private fun isPieceColor(r: Int, g: Int, b: Int): Boolean {
        // Piece top: distinctive dark red/brown, not matching background colors
        val brightness = r + g + b
        // The piece is darker than most platforms but has color contrast
        if (brightness < 50 || brightness > 600) return false
        // Piece tends to have red > green > blue or all dark
        return (r > 40 && r < 200 && abs(r - g) < 80 && abs(g - b) < 80)
                || (r > 60 && g > 30 && g < 120 && b > 20 && b < 100 && r > g && g > b)
    }

    /**
     * Verify a candidate pixel is part of the piece by checking neighborhood.
     */
    private fun isPieceRegion(pixels: IntArray, cx: Int, cy: Int, width: Int, height: Int): Boolean {
        var matchCount = 0
        val radius = 5
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                val pixel = pixels[ny * width + nx]
                if (isPieceColor(Color.red(pixel), Color.green(pixel), Color.blue(pixel))) {
                    matchCount++
                }
            }
        }
        return matchCount > 10
    }

    /**
     * Fallback: find piece by its dark cylindrical base shape.
     */
    private fun findPieceByBase(pixels: IntArray, width: Int, height: Int): Pair<Int, Int>? {
        val scanStartY = (height * 0.4).toInt()
        val scanEndY = (height * 0.85).toInt()
        val minWidth = (width * 0.03).toInt().coerceAtLeast(8)  // ~3% of screen width
        val maxWidth = (width * 0.15).toInt()                    // ~15% of screen width

        Log.d(TAG, "findPieceByBase: scanning $scanStartY..$scanEndY, darkSpan=$minWidth..$maxWidth")

        for (y in scanStartY until scanEndY) {
            var darkStart = -1
            var darkEnd = -1

            for (x in 20 until width - 20) {
                val pixel = pixels[y * width + x]
                val brightness = Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)

                if (brightness < 100) {
                    if (darkStart < 0) darkStart = x
                    darkEnd = x
                }
            }

            if (darkStart >= 0 && darkEnd - darkStart in minWidth..maxWidth) {
                val centerX = (darkStart + darkEnd) / 2
                // Verify it extends vertically (cylinder shape)
                var verticalDark = 0
                val checkHeight = (height * 0.03).toInt().coerceAtLeast(10)
                for (vy in y..(y + checkHeight).coerceAtMost(height - 1)) {
                    val pixel = pixels[vy * width + centerX]
                    if (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) < 150) {
                        verticalDark++
                    }
                }
                if (verticalDark > checkHeight / 2) {
                    Log.d(TAG, "findPieceByBase: found at ($centerX, $y), darkSpan=${darkEnd - darkStart}, verticalDark=$verticalDark/$checkHeight")
                    return centerX to y
                }
            }
        }
        return null
    }

    /**
     * Find the target board/platform.
     * Searches above the piece for the platform top surface.
     */
    private fun findBoard(
        pixels: IntArray, width: Int, height: Int,
        piece: Pair<Int, Int>
    ): Pair<Int, Int>? {
        // Board is always above the piece, between score line and piece
        val scanStartY = config.underGameScoreY.coerceAtLeast(50)
        val scanEndY = (piece.second - 20).coerceAtLeast(scanStartY + 1)
        Log.d(TAG, "findBoard: scanning Y=$scanStartY..$scanEndY (piece at ${piece.second})")

        // Use edge detection to find the platform
        // The platform top has a strong horizontal edge (color change)
        val edgeScores = mutableListOf<Triple<Int, Int, Float>>() // x, y, score

        for (y in scanStartY until scanEndY) {
            for (x in 20 until width - 20) {
                val idx = y * width + x
                val pixel = pixels[idx]
                val pixelBelow = if (y + 1 < height) pixels[(y + 1) * width + x] else pixel

                val diff = colorDiff(pixel, pixelBelow)
                if (diff > 30) {
                    edgeScores.add(Triple(x, y, diff.toFloat()))
                }
            }
        }

        if (edgeScores.isEmpty()) {
            return findBoardByColor(pixels, width, height, scanStartY, scanEndY)
        }

        // Cluster edge points to find the platform
        // Group by Y coordinate (horizontal line = platform top)
        val yGroups = edgeScores.groupBy { it.second }
        val bestRow = yGroups.maxByOrNull { group ->
            // Score = number of edge points in a row * average edge strength
            group.value.size * group.value.map { it.third }.average().toFloat()
        } ?: run {
            Log.d(TAG, "findBoard: no edge clusters found, trying color fallback")
            return findBoardByColor(pixels, width, height, scanStartY, scanEndY)
        }

        Log.d(TAG, "findBoard: best edge row at Y=${bestRow.key}, ${bestRow.value.size} edge points")
        val rowY = bestRow.key
        val rowPoints = bestRow.value

        // Find leftmost and rightmost edge in this row to get center
        val leftX = rowPoints.minOf { it.first }
        val rightX = rowPoints.maxOf { it.first }
        val centerX = (leftX + rightX) / 2

        // The board top is the center-top of the platform face
        return centerX to rowY
    }

    /**
     * Fallback: find board by looking for distinct color regions above the piece.
     */
    private fun findBoardByColor(
        pixels: IntArray, width: Int, height: Int,
        startY: Int, endY: Int
    ): Pair<Int, Int>? {
        // Scan for platforms by looking for horizontal color blocks
        for (y in startY until endY) {
            var blockStart = -1
            var currentColor = 0
            var blockLength = 0

            for (x in 20 until width - 20) {
                val pixel = pixels[y * width + x]
                if (blockStart < 0) {
                    blockStart = x
                    currentColor = pixel
                    blockLength = 1
                } else if (colorDiff(pixel, currentColor) < 20) {
                    blockLength++
                } else {
                    if (blockLength in 40..300) {
                        // Found a platform-sized block
                        return (blockStart + blockLength / 2) to y
                    }
                    blockStart = x
                    currentColor = pixel
                    blockLength = 1
                }
            }
            if (blockLength in 40..300) {
                return (blockStart + blockLength / 2) to y
            }
        }
        return null
    }

    /**
     * Calculate color difference between two pixels.
     */
    private fun colorDiff(pixel1: Int, pixel2: Int): Int {
        val r1 = Color.red(pixel1); val g1 = Color.green(pixel1); val b1 = Color.blue(pixel1)
        val r2 = Color.red(pixel2); val g2 = Color.green(pixel2); val b2 = Color.blue(pixel2)
        return abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
    }

    /**
     * Original wangshub/wechat_jump_game detector, adapted from wechat_jump_auto.py.
     */
    private fun findPieceAndBoardOriginal(
        pixels: IntArray,
        width: Int,
        height: Int
    ): OriginalDetection? {
        var scanStartY = 0
        for (y in height / 3 until height * 2 / 3 step 50) {
            val lastPixel = pixels[y * width]
            for (x in 1 until width) {
                if (pixels[y * width + x] != lastPixel) {
                    scanStartY = y - 50
                    break
                }
            }
            if (scanStartY > 0) break
        }
        if (scanStartY <= 0) scanStartY = height / 3

        val scanXBorder = width / 8
        var pieceYMax = 0
        val bottomXs = mutableListOf<Int>()

        for (y in scanStartY until height * 2 / 3) {
            for (x in scanXBorder until width - scanXBorder) {
                val pixel = pixels[y * width + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                if (r in 51..59 && g in 54..62 && b in 96..109) {
                    if (y > pieceYMax) {
                        pieceYMax = y
                        bottomXs.clear()
                    }
                    if (y == pieceYMax) bottomXs += x
                }
            }
        }

        if (bottomXs.isEmpty()) {
            Log.d(TAG, "original detector: no piece pixels")
            return null
        }

        val pieceX = bottomXs.average().toInt()
        val pieceY = pieceYMax - config.pieceBaseHeight12

        val boardXStart: Int
        val boardXEnd: Int
        if (pieceX < width / 2f) {
            boardXStart = pieceX
            boardXEnd = width
        } else {
            boardXStart = 0
            boardXEnd = pieceX
        }

        var boardX = 0
        for (y in height / 3 until height * 2 / 3) {
            var boardXSum = 0
            var boardXCount = 0
            val rowBackground = pixels[y * width]

            for (x in boardXStart until boardXEnd) {
                if (abs(x - pieceX) < config.pieceBodyWidth) continue
                if (y + 5 >= height) continue

                val pixel = pixels[y * width + x]
                val below = pixels[(y + 5) * width + x]
                if (colorDiff(pixel, rowBackground) > 10 && colorDiff(below, rowBackground) > 10) {
                    boardXSum += x
                    boardXCount++
                }
            }

            if (boardXSum > 0 && boardXCount > 0) {
                boardX = boardXSum / boardXCount
                break
            }
        }

        if (boardX == 0) {
            Log.d(TAG, "original detector: no board pixels")
            return null
        }

        val centerX = width / 2f + (24f / 1080f) * width
        val centerY = height / 2f + (17f / 1920f) * height
        val slope = 25.5f / 43.5f
        val boardY: Int
        val deltaPieceY: Int

        if (pieceX > centerX) {
            boardY = kotlin.math.round(slope * (boardX - centerX) + centerY).toInt()
            deltaPieceY = pieceY - kotlin.math.round(slope * (pieceX - centerX) + centerY).toInt()
        } else {
            boardY = kotlin.math.round(-slope * (boardX - centerX) + centerY).toInt()
            deltaPieceY = pieceY - kotlin.math.round(-slope * (pieceX - centerX) + centerY).toInt()
        }

        if (boardY <= 0 || boardY >= height) {
            Log.d(TAG, "original detector: boardY out of bounds: $boardY")
            return null
        }

        return OriginalDetection(pieceX, pieceY, boardX, boardY, deltaPieceY)
    }

    private data class PlatformComponent(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        val pixels: Int
    ) {
        val width: Int get() = maxX - minX + 1
        val height: Int get() = maxY - minY + 1
        val area: Int get() = width * height

        fun center(): Pair<Int, Int> {
            val x = (minX + maxX) / 2
            val y = minY + (height * 0.35f).toInt()
            return x to y
        }
    }

    private fun isLikelyBadDetection(
        width: Int,
        height: Int,
        piece: Pair<Int, Int>,
        board: Pair<Int, Int>,
        distance: Float
    ): Boolean {
        return distance > width * 0.9f ||
                piece.second > height * 0.72f ||
                board.second < height * 0.25f
    }

    private fun analyzeByPlatforms(
        pixels: IntArray,
        width: Int,
        height: Int,
        startTime: Long
    ): AnalysisResult? {
        val platforms = findPlatformComponents(pixels, width, height)
        Log.d(TAG, "platform fallback: components=${platforms.size}, ${platforms.take(4)}")
        if (platforms.size < 2) return null

        val selected = platforms
            .sortedWith(compareByDescending<PlatformComponent> { it.area }.thenByDescending { it.pixels })
            .take(2)

        val current = selected.maxByOrNull { it.center().second } ?: return null
        val target = selected.minByOrNull { it.center().second } ?: return null
        val piece = current.center()
        val board = target.center()

        val dx = (piece.first - board.first).toFloat()
        val dy = (piece.second - board.second).toFloat()
        val distance = sqrt(dx * dx + dy * dy)
        val pressTime = JumpTiming.calculatePressTimeMs(
            distance = distance,
            deltaPieceY = 0,
            pressCoefficient = config.pressCoefficient,
            headDiameter = config.headDiameter
        )
        val elapsed = System.currentTimeMillis() - startTime

        Log.i(TAG, "Platform fallback: current=(${piece.first}, ${piece.second}), target=(${board.first}, ${board.second}), distance=$distance, press=$pressTime")
        return AnalysisResult(
            pieceX = piece.first,
            pieceY = piece.second,
            boardX = board.first,
            boardY = board.second,
            distance = distance,
            pressTimeMs = pressTime,
            analysisTimeMs = elapsed
        )
    }

    private fun findPlatformComponents(pixels: IntArray, width: Int, height: Int): List<PlatformComponent> {
        val step = 3
        val gridW = (width + step - 1) / step
        val scanTop = config.underGameScoreY.coerceAtLeast(180)
        val scanBottom = (height * 0.82f).toInt()
        if (scanBottom <= scanTop) return emptyList()

        val bg = pixels[(height / 4).coerceIn(0, height - 1) * width + (width / 2)]
        val gridH = (scanBottom - scanTop + step - 1) / step
        val mask = BooleanArray(gridW * gridH)
        val visited = BooleanArray(gridW * gridH)

        for (gy in 0 until gridH) {
            val y = scanTop + gy * step
            for (gx in 0 until gridW) {
                val x = gx * step
                val pixel = pixels[y.coerceAtMost(height - 1) * width + x.coerceAtMost(width - 1)]
                val isUiChrome = y < height * 0.18f && x > width * 0.65f
                val isBottomToast = y > height * 0.76f && x in (width * 0.22f).toInt()..(width * 0.78f).toInt()
                mask[gy * gridW + gx] = !isUiChrome && !isBottomToast && colorDiff(pixel, bg) > 45
            }
        }

        val result = mutableListOf<PlatformComponent>()
        val queue = IntArray(mask.size)

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var minGx = Int.MAX_VALUE
            var minGy = Int.MAX_VALUE
            var maxGx = Int.MIN_VALUE
            var maxGy = Int.MIN_VALUE
            var count = 0

            while (head < tail) {
                val idx = queue[head++]
                val gx = idx % gridW
                val gy = idx / gridW
                count++
                if (gx < minGx) minGx = gx
                if (gx > maxGx) maxGx = gx
                if (gy < minGy) minGy = gy
                if (gy > maxGy) maxGy = gy

                val neighbors = intArrayOf(idx - 1, idx + 1, idx - gridW, idx + gridW)
                for (next in neighbors) {
                    if (next !in mask.indices || visited[next] || !mask[next]) continue
                    val nx = next % gridW
                    val ny = next / gridW
                    if (abs(nx - gx) + abs(ny - gy) != 1) continue
                    visited[next] = true
                    queue[tail++] = next
                }
            }

            val component = PlatformComponent(
                minX = minGx * step,
                minY = scanTop + minGy * step,
                maxX = (maxGx * step).coerceAtMost(width - 1),
                maxY = (scanTop + maxGy * step).coerceAtMost(height - 1),
                pixels = count
            )

            if (component.width > width * 0.18f &&
                component.height > height * 0.06f &&
                component.area > width * height * 0.015f
            ) {
                result += component
            }
        }

        return result
    }
}
