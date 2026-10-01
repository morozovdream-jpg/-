package com.dmitry.wadaru

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
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

/*
 * Основная тема строится на сочетании Wada I·139:
 * Neutral Gray #B6BFC1 + Salvia Blue #97ACC8 + Deep Indigo #051230.
 * Для избранного используется акцент из I·006:
 * Grenadine Pink #F48067 + Deep Indigo #051230.
 */
private val WadaDeepIndigo = ComposeColor(0xFF051230)
private val WadaSalviaBlue = ComposeColor(0xFF97ACC8)
private val WadaNeutralGray = ComposeColor(0xFFB6BFC1)
private val WadaGrenadinePink = ComposeColor(0xFFF48067)

private val AppBackground = ComposeColor(0xFF040B1C)
private val AppSurface = ComposeColor(0xFF08162B)
private val AppSurfaceRaised = ComposeColor(0xFF0D1E37)
private val AppSurfaceSoft = ComposeColor(0xFF10243F)
private val AppBorder = ComposeColor(0xFF263A56)
private val TextPrimary = ComposeColor(0xFFE7EDF4)
private val TextSecondary = ComposeColor(0xFFAAB6C4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WadaTheme { WadaApp() } }
    }
}

@Composable
private fun WadaTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = WadaSalviaBlue,
        onPrimary = WadaDeepIndigo,
        secondary = WadaNeutralGray,
        onSecondary = WadaDeepIndigo,
        tertiary = WadaGrenadinePink,
        onTertiary = WadaDeepIndigo,
        background = AppBackground,
        onBackground = TextPrimary,
        surface = AppSurface,
        onSurface = TextPrimary,
        surfaceVariant = AppSurfaceRaised,
        onSurfaceVariant = TextSecondary,
        outline = AppBorder,
        outlineVariant = AppBorder.copy(alpha = .65f)
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
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
        containerColor = AppBackground,
        topBar = { AppHeader() },
        bottomBar = {
            NavigationBar(
                containerColor = AppSurface,
                tonalElevation = 0.dp
            ) {
                Tab.entries.forEach { item ->
                    val icon = when (item) {
                        Tab.CATALOG -> Icons.Default.Palette
                        Tab.CATEGORIES -> Icons.Default.GridView
                        Tab.FAVORITES -> Icons.Default.Favorite
                    }
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icon, null) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = WadaDeepIndigo,
                            selectedTextColor = WadaSalviaBlue,
                            indicatorColor = WadaSalviaBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
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
private fun AppHeader() {
    Surface(color = AppBackground, tonalElevation = 0.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Сандзо Вада",
                        fontSize = 29.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.6).sp,
                        color = TextPrimary
                    )
                    Text(
                        "581 сочетание · тома I–II · офлайн",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                ThemePaletteMark()
            }
            HorizontalDivider(
                color = AppBorder.copy(alpha = .58f),
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun ThemePaletteMark() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(WadaNeutralGray, WadaSalviaBlue, WadaDeepIndigo).forEach { color ->
            Box(
                Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            "I·139",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(start = 3.dp)
        )
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
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, "Очистить")
                    }
                }
            },
            placeholder = { Text("Номер, цвет, HEX или категория") },
            shape = RoundedCornerShape(13.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface,
                focusedBorderColor = WadaSalviaBlue,
                unfocusedBorderColor = AppBorder,
                cursorColor = WadaSalviaBlue,
                focusedLeadingIconColor = WadaSalviaBlue,
                unfocusedLeadingIconColor = TextSecondary,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedPlaceholderColor = TextSecondary,
                unfocusedPlaceholderColor = TextSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { WadaFilterChip("Все", volume == 0) { volume = 0 } }
            item { WadaFilterChip("Том I", volume == 1) { volume = 1 } }
            item { WadaFilterChip("Том II", volume == 2) { volume = 2 } }
            item {
                VerticalDivider(
                    Modifier.height(30.dp),
                    color = AppBorder
                )
            }
            for (n in listOf(2, 3, 4, 5, 6)) {
                item {
                    val label = if (n == 6) "6+" else n.toString()
                    WadaFilterChip(label, size == n) { size = if (size == n) 0 else n }
                }
            }
        }

        if (categoryFilter != null) {
            AssistChip(
                onClick = onClearCategory,
                label = { Text(categoryFilter) },
                leadingIcon = { Icon(Icons.Default.FilterAlt, null, Modifier.size(17.dp)) },
                trailingIcon = { Icon(Icons.Default.Close, "Сбросить", Modifier.size(17.dp)) },
                shape = RoundedCornerShape(10.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = AppSurfaceRaised,
                    labelColor = WadaSalviaBlue,
                    leadingIconContentColor = WadaSalviaBlue,
                    trailingIconContentColor = TextSecondary
                ),
                border = BorderStroke(1.dp, AppBorder),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
            )
        }

        Row(
            Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "КАТАЛОГ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = WadaSalviaBlue
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${shown.size} / ${palettes.size}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
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
private fun WadaFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(10.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = AppBorder,
            selectedBorderColor = WadaSalviaBlue
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = AppSurface,
            labelColor = TextSecondary,
            selectedContainerColor = WadaSalviaBlue,
            selectedLabelColor = WadaDeepIndigo
        )
    )
}

@Composable
private fun PaletteCard(
    p: WadaPalette,
    analysis: PaletteAnalysis,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit
) {
    Surface(
        color = AppSurface,
        contentColor = TextPrimary,
        border = BorderStroke(1.dp, AppBorder.copy(alpha = .8f)),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column {
            ColorStrip(p.colors, 90.dp)
            Column(Modifier.padding(start = 12.dp, end = 9.dp, top = 11.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (p.volume == 1) "ТОМ I  ·  %03d".format(p.number)
                        else "ТОМ II  ·  %03d".format(p.number),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .7.sp,
                        color = WadaSalviaBlue,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFavorite, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Избранное",
                            tint = if (favorite) WadaGrenadinePink else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Text(
                    p.colors.joinToString(" · ") { RussianColorNames.of(it.name) },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    "${p.colors.size} цветов · ${analysis.temperature.label.lowercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
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

private data class CategoryGroup(val title: String, val keys: List<String>)

@Composable
private fun CategoriesScreen(
    palettes: List<WadaPalette>,
    analyses: Map<String, PaletteAnalysis>,
    onCategory: (String) -> Unit
) {
    val groups = remember {
        listOf(
            CategoryGroup("ИСТОЧНИК", listOf("Том I", "Том II")),
            CategoryGroup("КОЛИЧЕСТВО ЦВЕТОВ", listOf("2 цвета", "3 цвета", "4 цвета", "5 цветов", "6+ цветов")),
            CategoryGroup("ТЕМПЕРАТУРА", listOf("Тёплые", "Холодные", "Смешанные")),
            CategoryGroup("СВЕТЛОТА", listOf("Светлые", "Средние", "Тёмные")),
            CategoryGroup("НАСЫЩЕННОСТЬ", listOf("Приглушённые", "Умеренные", "Насыщенные")),
            CategoryGroup("КОНТРАСТ", listOf("Низкий контраст", "Средний контраст", "Высокий контраст")),
            CategoryGroup(
                "ЦВЕТОВОЕ СЕМЕЙСТВО",
                listOf("Красные", "Оранжевые", "Жёлтые", "Зелёные", "Бирюзовые", "Синие", "Фиолетовые", "Розовые", "Нейтральные")
            )
        )
    }
    val counts = remember(palettes, analyses) {
        groups.flatMap { it.keys }.associateWith { key ->
            palettes.count { matchesCategory(it, analyses.getValue(it.id), key) }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Категории",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                "Структура палитр по составу, светлоте, насыщенности и температуре.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
        }

        groups.forEach { group ->
            item {
                Text(
                    group.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = WadaSalviaBlue,
                    modifier = Modifier.padding(start = 2.dp, top = 12.dp, bottom = 3.dp)
                )
            }
            items(group.keys) { key ->
                CategoryRow(key, counts[key] ?: 0) { onCategory(key) }
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, count: Int, onClick: () -> Unit) {
    Surface(
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder.copy(alpha = .75f)),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(5.dp, 28.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(WadaSalviaBlue)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                name,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                count.toString(),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = WadaSalviaBlue,
                modifier = Modifier.size(20.dp)
            )
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(30.dp)
            ) {
                Surface(
                    color = WadaGrenadinePink.copy(alpha = .12f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, WadaGrenadinePink.copy(alpha = .35f))
                ) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        null,
                        tint = WadaGrenadinePink,
                        modifier = Modifier.padding(20.dp).size(42.dp)
                    )
                }
                Text(
                    "Избранных сочетаний пока нет",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Text(
                    "Нажимай на сердце в каталоге — палитры сохраняются только на устройстве.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                    lineHeight = 20.sp
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
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
        containerColor = AppBackground,
        topBar = {
            Surface(color = AppBackground, tonalElevation = 0.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(60.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад", tint = WadaSalviaBlue)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (palette.volume == 1) "ТОМ I · %03d".format(palette.number)
                            else "ТОМ II · %03d".format(palette.number),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = WadaSalviaBlue
                        )
                        Text(
                            "Сочетание цветов",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onFavorite) {
                        Icon(
                            if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Избранное",
                            tint = if (favorite) WadaGrenadinePink else TextSecondary
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
            item {
                Surface(
                    color = AppSurface,
                    border = BorderStroke(1.dp, AppBorder.copy(alpha = .6f)),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    ColorStrip(palette.colors, 184.dp)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                    Text(
                        palette.colors.joinToString(" · ") { RussianColorNames.of(it.name) },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        palette.colors.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.padding(top = 14.dp)
                    ) {
                        item { DetailChip(analysis.temperature.label) }
                        item { DetailChip(analysis.contrast.label) }
                        item { DetailChip(analysis.chroma.label) }
                        item { DetailChip(analysis.dominantFamily) }
                    }
                }
            }
            item { HorizontalDivider(color = AppBorder.copy(alpha = .55f)) }
            item {
                AnalysisBlock("ХАРАКТЕР", analysis.short)
                AnalysisBlock("ПОЧЕМУ РАБОТАЕТ", analysis.why)
                AnalysisBlock("КАК ПРИМЕНЯТЬ", analysis.usage)
            }
            item {
                HorizontalDivider(
                    color = AppBorder.copy(alpha = .55f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "ЦВЕТА",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = WadaSalviaBlue,
                    modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 8.dp)
                )
            }
            items(palette.colors) { c ->
                ColorFactRow(c) { clipboard.setText(AnnotatedString(c.hex)) }
            }
            item {
                Surface(
                    color = AppSurfaceRaised,
                    border = BorderStroke(1.dp, AppBorder.copy(alpha = .7f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        "Русское описание формируется локально по параметрам палитры и не является переводом сторонних редакционных комментариев.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailChip(text: String) {
    Surface(
        color = AppSurfaceRaised,
        contentColor = WadaNeutralGray,
        border = BorderStroke(1.dp, AppBorder),
        shape = RoundedCornerShape(9.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun AnalysisBlock(title: String, text: String) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 11.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp,
            color = WadaSalviaBlue
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 7.dp)
        )
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

    Surface(
        color = ComposeColor.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ComposeColor(android.graphics.Color.parseColor(c.hex)))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    RussianColorNames.of(c.name),
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                if (RussianColorNames.of(c.name) != c.name) {
                    Text(
                        c.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                Text(
                    c.wadaId?.let { "№ $it · ${c.hex}" } ?: c.hex,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = WadaSalviaBlue,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    extra,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = copyHex) {
                Icon(
                    Icons.Default.ContentCopy,
                    "Копировать HEX",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
