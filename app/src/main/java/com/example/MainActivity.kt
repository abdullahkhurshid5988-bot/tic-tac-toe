package com.example

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// --- Brand Colors (Sophisticated Dark) ---
val BackgroundColor = Color(0xFF1C1B1F)
val SurfaceColor = Color(0xFF2B2930)
val OutlineColor = Color(0xFF49454F)
val PrimaryColor = Color(0xFFD0BCFF)
val OnPrimaryColor = Color(0xFF381E72)
val TextPrimary = Color(0xFFE6E1E5)
val TextSecondary = Color(0xFFCAC4D0)

val GlassSurfaceColor = SurfaceColor
val GlassBorderColor = OutlineColor

// --- ViewModel ---
enum class Player { X, O, NONE }
enum class GameState { PLAYING, WON, DRAW }
enum class GameDifficulty { PVP, AI_EASY, AI_UNBEATABLE }

data class TicTacToeState(
    val board: List<Player> = List(9) { Player.NONE },
    val currentPlayer: Player = Player.X,
    val gameState: GameState = GameState.PLAYING,
    val winningLine: List<Int>? = null,
    val playerXColor: Color = PrimaryColor,
    val playerOColor: Color = Color(0xFFE8DEF8),
    val difficulty: GameDifficulty = GameDifficulty.PVP,
    val showSettings: Boolean = false
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("tictactoe_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(TicTacToeState(
        playerXColor = Color(prefs.getLong("x_color", PrimaryColor.value.toLong()).toULong()),
        playerOColor = Color(prefs.getLong("o_color", Color(0xFFE8DEF8).value.toLong()).toULong()),
        difficulty = GameDifficulty.valueOf(prefs.getString("difficulty", GameDifficulty.PVP.name) ?: GameDifficulty.PVP.name)
    ))
    val uiState: StateFlow<TicTacToeState> = _uiState.asStateFlow()

    private val winningCombinations = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // Rows
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // Columns
        listOf(0, 4, 8), listOf(2, 4, 6)                   // Diagonals
    )

    fun playMove(index: Int) {
        val currentState = _uiState.value
        if (currentState.gameState != GameState.PLAYING || currentState.board[index] != Player.NONE) return
        
        // Prevent human from moving during AI's turn
        if (currentState.difficulty != GameDifficulty.PVP && currentState.currentPlayer == Player.O) return

        applyMove(index)
    }

    private fun applyMove(index: Int) {
        val currentState = _uiState.value
        if (currentState.gameState != GameState.PLAYING || currentState.board[index] != Player.NONE) return

        val newBoard = currentState.board.toMutableList()
        newBoard[index] = currentState.currentPlayer

        var newGameState = GameState.PLAYING
        var winLine: List<Int>? = null

        for (combination in winningCombinations) {
            val (a, b, c) = combination
            if (newBoard[a] != Player.NONE && newBoard[a] == newBoard[b] && newBoard[a] == newBoard[c]) {
                newGameState = GameState.WON
                winLine = combination
                break
            }
        }

        if (newGameState == GameState.PLAYING && !newBoard.contains(Player.NONE)) {
            newGameState = GameState.DRAW
        }

        val nextPlayer = if (newGameState == GameState.PLAYING) {
            if (currentState.currentPlayer == Player.X) Player.O else Player.X
        } else currentState.currentPlayer

        _uiState.update {
            it.copy(
                board = newBoard,
                currentPlayer = nextPlayer,
                gameState = newGameState,
                winningLine = winLine
            )
        }

        if (newGameState == GameState.PLAYING && _uiState.value.difficulty != GameDifficulty.PVP && nextPlayer == Player.O) {
            viewModelScope.launch {
                delay(400)
                makeAIMove()
            }
        }
    }

    private fun makeAIMove() {
        val currentState = _uiState.value
        if (currentState.gameState != GameState.PLAYING || currentState.currentPlayer != Player.O) return

        val moveIndex = when (currentState.difficulty) {
            GameDifficulty.AI_UNBEATABLE -> getBestMove(currentState.board, Player.O)
            GameDifficulty.AI_EASY -> currentState.board.indices.filter { currentState.board[it] == Player.NONE }.randomOrNull() ?: -1
            else -> -1
        }

        if (moveIndex != -1) {
            applyMove(moveIndex)
        }
    }

    private fun checkWinner(board: List<Player>): Player? {
        for (combination in winningCombinations) {
            val (a, b, c) = combination
            if (board[a] != Player.NONE && board[a] == board[b] && board[a] == board[c]) {
                return board[a]
            }
        }
        return null
    }

    private fun minimax(board: List<Player>, depth: Int, isMaximizing: Boolean, aiPlayer: Player, humanPlayer: Player): Int {
        val winner = checkWinner(board)
        if (winner == aiPlayer) return 10 - depth
        if (winner == humanPlayer) return -10 + depth
        if (!board.contains(Player.NONE)) return 0

        if (isMaximizing) {
            var bestScore = Int.MIN_VALUE
            for (i in board.indices) {
                if (board[i] == Player.NONE) {
                    val newBoard = board.toMutableList()
                    newBoard[i] = aiPlayer
                    val score = minimax(newBoard, depth + 1, false, aiPlayer, humanPlayer)
                    bestScore = maxOf(bestScore, score)
                }
            }
            return bestScore
        } else {
            var bestScore = Int.MAX_VALUE
            for (i in board.indices) {
                if (board[i] == Player.NONE) {
                    val newBoard = board.toMutableList()
                    newBoard[i] = humanPlayer
                    val score = minimax(newBoard, depth + 1, true, aiPlayer, humanPlayer)
                    bestScore = minOf(bestScore, score)
                }
            }
            return bestScore
        }
    }

    private fun getBestMove(board: List<Player>, aiPlayer: Player): Int {
        var bestScore = Int.MIN_VALUE
        var move = -1
        val humanPlayer = if (aiPlayer == Player.X) Player.O else Player.X

        for (i in board.indices) {
            if (board[i] == Player.NONE) {
                val newBoard = board.toMutableList()
                newBoard[i] = aiPlayer
                val score = minimax(newBoard, 0, false, aiPlayer, humanPlayer)
                if (score > bestScore) {
                    bestScore = score
                    move = i
                }
            }
        }
        return move
    }

    fun resetGame() {
        val currentX = _uiState.value.playerXColor
        val currentO = _uiState.value.playerOColor
        val currentDiff = _uiState.value.difficulty
        _uiState.value = TicTacToeState(playerXColor = currentX, playerOColor = currentO, difficulty = currentDiff)
    }

    fun updateColor(player: Player, color: Color) {
        if (player == Player.X) {
            prefs.edit().putLong("x_color", color.value.toLong()).apply()
            _uiState.update { it.copy(playerXColor = color) }
        } else if (player == Player.O) {
            prefs.edit().putLong("o_color", color.value.toLong()).apply()
            _uiState.update { it.copy(playerOColor = color) }
        }
    }
    
    fun setDifficulty(difficulty: GameDifficulty) {
        prefs.edit().putString("difficulty", difficulty.name).apply()
        _uiState.update { it.copy(difficulty = difficulty) }
        resetGame()
    }
    
    fun toggleSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }
}

// --- SoundManager ---
class SoundManager {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    fun playMarkSound() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
    }

    fun playWinSound() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300)
    }

    fun playResetSound() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
    }
    
    fun playDrawSound() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_NETWORK_BUSY, 200)
    }

    fun release() {
        toneGenerator.release()
    }
}

// --- MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Transparent,
                    surface = Color.Transparent,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                TicTacToeScreen()
            }
        }
    }
}

@Composable
fun TicTacToeScreen(viewModel: GameViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val soundManager = remember { SoundManager() }
    
    DisposableEffect(Unit) {
        onDispose {
            soundManager.release()
        }
    }

    // Trigger haptics and sounds on win
    LaunchedEffect(uiState.gameState) {
        when (uiState.gameState) {
            GameState.WON -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                soundManager.playWinSound()
            }
            GameState.DRAW -> {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                soundManager.playDrawSound()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { TopBar(onSettingsClick = { viewModel.toggleSettings(true) }) },
            bottomBar = {
                Column {
                    AdmobBanner(modifier = Modifier.background(BackgroundColor))
                    BottomNavBar(onReset = { 
                        soundManager.playResetSound()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.resetGame() 
                    })
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StatusCard(uiState)
                Spacer(modifier = Modifier.height(48.dp))
                GameBoard(uiState) { index ->
                    if (uiState.board[index] == Player.NONE && uiState.gameState == GameState.PLAYING) {
                        soundManager.playMarkSound()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.playMove(index)
                    } else if (uiState.board[index] != Player.NONE) {
                        // Error haptic for taken cell
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
        }
        
        // Winner Announcement Overlay
        AnimatedVisibility(
            visible = uiState.gameState != GameState.PLAYING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .background(SurfaceColor, RoundedCornerShape(28.dp))
                        .border(1.dp, OutlineColor, RoundedCornerShape(28.dp))
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isAI = uiState.difficulty != GameDifficulty.PVP
                    val statusText = when (uiState.gameState) {
                        GameState.WON -> if (isAI && uiState.currentPlayer == Player.O) "AI Wins!" else "Player ${uiState.currentPlayer.name} Wins!"
                        GameState.DRAW -> "It's a Draw!"
                        else -> ""
                    }
                    
                    Text(
                        text = statusText,
                        fontSize = 32.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // AdMob Banner Ad
                    AdmobBanner()
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = {
                            soundManager.playResetSound()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.resetGame()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor,
                            contentColor = OnPrimaryColor
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text("Play Again", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // Settings Dialog
        if (uiState.showSettings) {
            SettingsDialog(
                playerXColor = uiState.playerXColor,
                playerOColor = uiState.playerOColor,
                difficulty = uiState.difficulty,
                onColorSelected = { player, color -> viewModel.updateColor(player, color) },
                onDifficultySelected = { diff -> viewModel.setDifficulty(diff) },
                onDismiss = { viewModel.toggleSettings(false) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(onSettingsClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "Tic Tac Toe",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                color = TextPrimary,
                fontSize = 28.sp
            )
        },
        navigationIcon = {
            IconButton(onClick = {}) {
                Icon(Icons.Default.Grid3x3, contentDescription = "Grid", tint = TextPrimary)
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BackgroundColor,
            titleContentColor = TextPrimary
        )
    )
}

@Composable
fun StatusCard(state: TicTacToeState) {
    val backgroundColor = GlassSurfaceColor
    val isAI = state.difficulty != GameDifficulty.PVP
    val statusText = when (state.gameState) {
        GameState.PLAYING -> if (isAI && state.currentPlayer == Player.O) "AI's Turn" else "Player ${state.currentPlayer.name}'s Turn"
        GameState.WON -> if (isAI && state.currentPlayer == Player.O) "AI Wins!" else "Player ${state.currentPlayer.name} Wins!"
        GameState.DRAW -> "It's a Draw!"
    }
    val statusColor = when (state.gameState) {
        GameState.DRAW -> Color.Gray
        else -> if (state.currentPlayer == Player.X) state.playerXColor else state.playerOColor
    }

    val scale by animateFloatAsState(
        targetValue = if (state.gameState == GameState.WON) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Row(
        modifier = Modifier
            .scale(scale)
            .background(backgroundColor, RoundedCornerShape(percent = 50))
            .border(1.dp, GlassBorderColor, RoundedCornerShape(percent = 50))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(statusColor, CircleShape)
                .border(2.dp, statusColor.copy(alpha = 0.5f), CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = statusText,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp
        )
    }
}

@Composable
fun GameBoard(state: TicTacToeState, onCellClick: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .background(GlassSurfaceColor, RoundedCornerShape(28.dp))
            .border(1.dp, GlassBorderColor, RoundedCornerShape(28.dp))
            .padding(24.dp)
    ) {
        Box {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in 0..2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (col in 0..2) {
                            val index = row * 3 + col
                            GameCell(
                                player = state.board[index],
                                isWinningCell = state.winningLine?.contains(index) == true,
                                xColor = state.playerXColor,
                                oColor = state.playerOColor,
                                onClick = { onCellClick(index) }
                            )
                        }
                    }
                }
            }
            if (state.winningLine != null) {
                WinningLineOverlay(winningLine = state.winningLine, winner = state.currentPlayer, xColor = state.playerXColor, oColor = state.playerOColor)
            }
        }
    }
}

@Composable
fun BoxScope.WinningLineOverlay(winningLine: List<Int>, winner: Player, xColor: Color, oColor: Color) {
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(winningLine) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    val color = if (winner == Player.X) xColor else oColor

    Canvas(modifier = Modifier.matchParentSize()) {
        val cellSize = 90.dp.toPx()
        val spacing = 12.dp.toPx()

        fun getCenter(index: Int): Offset {
            val row = index / 3
            val col = index % 3
            val x = col * (cellSize + spacing) + cellSize / 2
            val y = row * (cellSize + spacing) + cellSize / 2
            return Offset(x, y)
        }

        val startOffset = getCenter(winningLine[0])
        val endOffset = getCenter(winningLine[2])

        val angle = atan2(endOffset.y - startOffset.y, endOffset.x - startOffset.x)
        val extension = cellSize * 0.4f
        
        val extendedStart = Offset(
            startOffset.x - cos(angle) * extension,
            startOffset.y - sin(angle) * extension
        )
        val extendedEnd = Offset(
            endOffset.x + cos(angle) * extension,
            endOffset.y + sin(angle) * extension
        )

        val currentEnd = Offset(
            extendedStart.x + (extendedEnd.x - extendedStart.x) * animationProgress.value,
            extendedStart.y + (extendedEnd.y - extendedStart.y) * animationProgress.value
        )

        // Glow Layer
        drawLine(
            color = color.copy(alpha = 0.4f),
            start = extendedStart,
            end = currentEnd,
            strokeWidth = 24.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Core Line
        drawLine(
            color = color,
            start = extendedStart,
            end = currentEnd,
            strokeWidth = 8.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun GameCell(player: Player, isWinningCell: Boolean, xColor: Color, oColor: Color, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val activeColor = if (player == Player.X) xColor else oColor
    
    val borderColor = if (isWinningCell) {
        activeColor.copy(alpha = pulse)
    } else {
        GlassBorderColor
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f)

    Box(
        modifier = Modifier
            .size(90.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1AFFFFFF)) // Light glass tint
            .border(
                width = if (isWinningCell) 3.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (player != Player.NONE) {
            Text(
                text = player.name,
                fontSize = 56.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                color = activeColor,
                style = TextStyle(
                    shadow = Shadow(
                        color = activeColor.copy(alpha = 0.8f),
                        blurRadius = 24f
                    )
                )
            )
        }
    }
}

@Composable
fun BottomNavBar(onReset: () -> Unit) {
    NavigationBar(
        containerColor = SurfaceColor,
        contentColor = TextPrimary,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.History, contentDescription = "History", tint = TextSecondary) },
            label = { Text("History", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
            selected = false,
            onClick = { }
        )
        NavigationBarItem(
            icon = {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(32.dp)
                        .background(PrimaryColor, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Play", tint = OnPrimaryColor)
                }
            },
            label = { Text("Home", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            selected = true,
            onClick = onReset,
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Leaderboard, contentDescription = "Scores", tint = TextSecondary) },
            label = { Text("Growth", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
            selected = false,
            onClick = { }
        )
    }
}

@Composable
fun SettingsDialog(
    playerXColor: Color,
    playerOColor: Color,
    difficulty: GameDifficulty,
    onColorSelected: (Player, Color) -> Unit,
    onDifficultySelected: (GameDifficulty) -> Unit,
    onDismiss: () -> Unit
) {
    val availableColors = listOf(
        Color(0xFFD0BCFF), // Lilac
        Color(0xFFE8DEF8), // Light Lilac
        Color(0xFF4ECCA3), // Cyan
        Color(0xFFE94560), // Soft Pink
        Color(0xFFFFD700), // Gold
        Color(0xFF00FA9A), // Medium Spring Green
        Color(0xFFFF7F50), // Coral
        Color(0xFF87CEEB)  // Sky Blue
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        titleContentColor = TextPrimary,
        title = { Text("Profile Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Player X Color", color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableColors.take(4).forEach { color ->
                        ColorCircle(color, isSelected = playerXColor == color) { onColorSelected(Player.X, color) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableColors.drop(4).take(4).forEach { color ->
                        ColorCircle(color, isSelected = playerXColor == color) { onColorSelected(Player.X, color) }
                    }
                }
                
                HorizontalDivider(color = OutlineColor)
                
                Text("Player O Color", color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableColors.take(4).forEach { color ->
                        ColorCircle(color, isSelected = playerOColor == color) { onColorSelected(Player.O, color) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableColors.drop(4).take(4).forEach { color ->
                        ColorCircle(color, isSelected = playerOColor == color) { onColorSelected(Player.O, color) }
                    }
                }
                
                HorizontalDivider(color = OutlineColor)
                
                Text("Game Mode", color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        GameDifficulty.PVP to "PvP", 
                        GameDifficulty.AI_EASY to "Easy AI", 
                        GameDifficulty.AI_UNBEATABLE to "Pro AI"
                    )
                    modes.forEach { (mode, label) ->
                        val isSelected = difficulty == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) PrimaryColor else BackgroundColor)
                                .border(1.dp, if (isSelected) PrimaryColor else OutlineColor, RoundedCornerShape(12.dp))
                                .clickable { onDifficultySelected(mode) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) OnPrimaryColor else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = PrimaryColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ColorCircle(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color.White else OutlineColor,
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

@Composable
fun AdmobBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111" // Test Banner ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
