package com.dmitry.wadaru

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Paper = ComposeColor(0xFFF7F2E8)
private val Ink = ComposeColor(0xFF26231F)
private val Moss = ComposeColor(0xFF5F725D)
private val Brick = ComposeColor(0xFFA5503C)
private val NightPaper = ComposeColor(0xFF15130F)
private val NightSurface = ComposeColor(0xFF211E19)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WadaTheme { WadaApp() } }
    }
}

@Composable
private fun WadaTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = ComposeColor(0xFFD9B98C),
            secondary = ComposeColor(0xFFAFC3AB),
            tertiary = ComposeColor(0xFFE7A28D),
            background = NightPaper,
            surface = NightSurface,
            onBackground = ComposeColor(0xFFF1E9DC),
            onSurface = ComposeColor(0xFFF1E9DC)
        )
    } else {
        lightColorScheme(
            primary = Ink,
            secondary = Moss,
            tertiary = Brick,
            background = Paper,
            surface = ComposeColor(0xFFFFFBF3),
            onBackground = Ink,
            onSurface = Ink,
            outline = ComposeColor(0xFFD6CDBF)
        )
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

private enum class Tab(val label: String) {
    CATALOG("Сочетания"), CATEGORIES("Категории"), FAVORITES("Избранное")
}

@Composable
private fun WadaApp() {
    val context = LocalContext.current
    val allPalettes = remember { PaletteRepository.load(context) }
    val analyses = remember(allPalettes) { allPalettes.associate { it.id to PaletteAnalyzer.analyze(it) } }
    val prefs = remember { context.getSharedPreferences("wada", android.content.Context.MODE_PRIVATE) }
    val favorites = remember {
        mutableStateListOf<String>().also { list ->
            list.addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
        }
    }

    var tab by remember { mutableStateOf(Tab.CATALOG) }
    var selected by remember { mutableStateOf<WadaPalette?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }

    fun toggleFavorite(id: String) {
        if (favorites.contains(id)) favorites.remove(id) else favorites.add(id)
        prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
    }

    if (selected != null) {
        BackHandler { selected = null }
        PaletteDetail(
            palette = selected!!,
            analysis = analyses.getValue(selected!!.id),
            favorite = favorites.contains(selected!!.id),
            onFavorite = { toggleFavorite(selected!!.id) },
            onBack = { selected = null }
        )
        return
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 1.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(
                        "Сандзо Вада",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        "581 сочетание · офлайн-справочник",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.CATALOG,
                    onClick = { tab = Tab.CATALOG },
                    icon = { Icon(Icons.Default.Palette, null) },
                    label = { Text(Tab.CATALOG.label) }
                )
                NavigationBarItem(
                    selected = tab == Tab.CATEGORIES,
                    onClick = { tab = Tab.CATEGORIES },
                    icon = { Icon(Icons.Default.Category, null) },
                    label = { Text(Tab.CATEGORIES.label) }
                )
                NavigationBarItem(
                    selected = tab == Tab.FAVORITES,
                    onClick = { tab = Tab.FAVORITES },
                    icon = { Icon(Icons.Default.Favorite, null) },
                    label = { Text(Tab.FAVORITES.label) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.CATALOG -> CatalogScreen(
                    palettes = allPalettes,
                    analyses = analyses,
                    categoryFilter = categoryFilter,
                    onClearCategory = { categoryFilter = null },
                    favorites = favorites,
                    onOpen = { selected = it },
                    onFavorite = { toggleFavorite(it.id) }
                )
                Tab.CATEGORIES -> CategoriesScreen(allPalettes, analyses) { key ->
                    categoryFilter = key
                    tab = Tab.CATALOG
                }
                Tab.FAVORITES -> FavoritesScreen(
                    palettes = allPalettes.filter { favorites.contains(it.id) },
                    analyses = analyses,
                    onOpen = { selected = it },
                    onFavorite = { toggleFavorite(it.id) }
                )
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    palettes: List<WadaPalette>,
    analyses: Map<String, PaletteAnalysis>,
    categoryFilter: String?,
    onClearCategory: () -> Unit,
    favorites: SnapshotStateList<String>,
    onOpen: (WadaPalette) -> Unit,
    onFavorite: (WadaPalette) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var volume by remember { mutableIntStateOf(0) }
    var size by remember { mutableIntStateOf(0) }

    val shown = remember(palettes, analyses, query, volume, size, categoryFilter) {
        palettes.filter { p ->
            val a = analyses.getValue(p.id)
            val queryOk = query.isBlank() || PaletteAnalyzer.searchBlob(p, a).contains(query.trim().lowercase())
            val volumeOk = volume == 0 || p.volume == volume
            val sizeOk = size == 0 || if (size == 6) p.colors.size >= 6 else p.colors.size == size
            val categoryOk = categoryFilter == null || matchesCategory(p, a, categoryFilter)
            queryOk && volumeOk && sizeOk && categoryOk
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Номер, цвет, HEX или категория") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { FilterChip(selected = volume == 0, onClick = { volume = 0 }, label = { Text("Оба тома") }) }
            item { FilterChip(selected = volume == 1, onClick = { volume = 1 }, label = { Text("Том I") }) }
            item { FilterChip(selected = volume == 2, onClick = { volume = 2 }, label = { Text("Том II") }) }
            item { VerticalDivider(Modifier.height(32.dp)) }
            for (n in listOf(2, 3, 4, 5, 6)) {
                item {
                    FilterChip(
                        selected = size == n,
                        onClick = { size = if (size == n) 0 else n },
                        label = { Text(if (n == 6) "6+ цветов" else "$n цвета") }
                    )
                }
            }
        }

        if (categoryFilter != null) {
            AssistChip(
                onClick = onClearCategory,
                label = { Text("Фильтр: $categoryFilter  ×") },
                leadingIcon = { Icon(Icons.Default.FilterAlt, null) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Text(
            "${shown.size} из ${palettes.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 165.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            gridItems(shown, key = { it.id }) { p ->
                PaletteCard(
                    p = p,
                    analysis = analyses.getValue(p.id),
                    favorite = favorites.contains(p.id),
                    onOpen = { onOpen(p) },
                    onFavorite = { onFavorite(p) }
                )
            }
        }
    }
}

@Composable
private fun PaletteCard(
    p: WadaPalette,
    analysis: PaletteAnalysis,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp)
    ) {
        ColorStrip(p.colors, 76.dp)
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.displayNumber,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Избранное",
                        tint = if (favorite) MaterialTheme.colorScheme.tertiary else LocalContentColor.current
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                p.colors.joinToString(" · ") { it.name },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${p.colors.size} цветов · ${analysis.temperature.label.lowercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .60f)
            )
        }
    }
}

@Composable
private fun ColorStrip(colors: List<WadaColor>, height: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        colors.forEach { c ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(ComposeColor(android.graphics.Color.parseColor(c.hex)))
            )
        }
    }
}

@Composable
private fun CategoriesScreen(
    palettes: List<WadaPalette>,
    analyses: Map<String, PaletteAnalysis>,
    onCategory: (String) -> Unit
) {
    val categories = remember(palettes, analyses) {
        listOf(
            "Том I", "Том II",
            "2 цвета", "3 цвета", "4 цвета", "5 цветов", "6+ цветов",
            "Тёплые", "Холодные", "Смешанные",
            "Светлые", "Средние", "Тёмные",
            "Приглушённые", "Умеренные", "Насыщенные",
            "Низкий контраст", "Средний контраст", "Высокий контраст",
            "Красные", "Оранжевые", "Жёлтые", "Зелёные", "Бирюзовые",
            "Синие", "Фиолетовые", "Розовые", "Нейтральные"
        ).map { key ->
            key to palettes.count { matchesCategory(it, analyses.getValue(it.id), key) }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text(
                "Разновидности палитр",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Категории рассчитываются локально по составу цветов, светлоте, насыщенности и температуре.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
        }
        items(categories) { pair ->
            val name = pair.first
            val count = pair.second
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategory(name) }
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("$count", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
    }
}

private fun matchesCategory(p: WadaPalette, a: PaletteAnalysis, key: String): Boolean = when (key) {
    "Том I" -> p.volume == 1
    "Том II" -> p.volume == 2
    "2 цвета" -> p.colors.size == 2
    "3 цвета" -> p.colors.size == 3
    "4 цвета" -> p.colors.size == 4
    "5 цветов" -> p.colors.size == 5
    "6+ цветов" -> p.colors.size >= 6
    a.temperature.label -> true
    a.lightness.label -> true
    a.chroma.label -> true
    a.contrast.label -> true
    a.dominantFamily -> true
    else -> false
}

@Composable
private fun FavoritesScreen(
    palettes: List<WadaPalette>,
    analyses: Map<String, PaletteAnalysis>,
    onOpen: (WadaPalette) -> Unit,
    onFavorite: (WadaPalette) -> Unit
) {
    if (palettes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Здесь появятся выбранные сочетания", fontWeight = FontWeight.Medium)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(165.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            gridItems(palettes, key = { it.id }) { p ->
                PaletteCard(
                    p, analyses.getValue(p.id), true,
                    onOpen = { onOpen(p) },
                    onFavorite = { onFavorite(p) }
                )
            }
        }
    }
}

@Composable
private fun PaletteDetail(
    palette: WadaPalette,
    analysis: PaletteAnalysis,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Scaffold(
        topBar = {
            Surface(shadowElevation = 1.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(60.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                    Text(
                        "Сочетание ${palette.displayNumber}",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFavorite) {
                        Icon(
                            if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Избранное",
                            tint = if (favorite) MaterialTheme.colorScheme.tertiary else LocalContentColor.current
                        )
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { ColorStrip(palette.colors, 152.dp) }
            item {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        palette.colors.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item { SuggestionChip(onClick = {}, label = { Text(analysis.temperature.label) }) }
                        item { SuggestionChip(onClick = {}, label = { Text(analysis.contrast.label) }) }
                        item { SuggestionChip(onClick = {}, label = { Text(analysis.chroma.label) }) }
                        item { SuggestionChip(onClick = {}, label = { Text(analysis.dominantFamily) }) }
                    }
                }
            }
            item {
                AnalysisBlock("Характер сочетания", analysis.short)
                AnalysisBlock("Почему оно работает", analysis.why)
                AnalysisBlock("Как применять", analysis.usage)
            }
            item {
                Text(
                    "Цвета",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 8.dp)
                )
            }
            items(palette.colors) { c ->
                ColorFactRow(c) { clipboard.setText(AnnotatedString(c.hex)) }
            }
            item {
                Text(
                    "Русское описание создано внутри приложения на основе измеримых характеристик палитры. Это не перевод редакционных комментариев сторонних сайтов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AnalysisBlock(title: String, text: String) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
    }
}

@Composable
private fun ColorFactRow(c: WadaColor, copyHex: () -> Unit) {
    val rgbText = c.rgb.joinToString(", ")
    val extra = buildString {
        append("RGB $rgbText")
        c.cmyk?.let { append(" · CMYK ").append(it.joinToString(", ")) }
        c.lab?.let { append(" · LAB ").append(it.joinToString(", ") { v -> "%.1f".format(v) }) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ComposeColor(android.graphics.Color.parseColor(c.hex)))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.Medium)
            Text(
                c.wadaId?.let { "№ $it · ${c.hex}" } ?: c.hex,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                extra,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = copyHex) { Icon(Icons.Default.ContentCopy, "Копировать HEX") }
    }
}
