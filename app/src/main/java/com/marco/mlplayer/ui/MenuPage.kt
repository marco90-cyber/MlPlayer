package com.marco.mlplayer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marco.mlplayer.data.rememberCover
import com.marco.mltheme.MLChevron
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSettingsScaffold
import com.marco.mltheme.MLSwitchRow
import com.marco.mltheme.mlPrefColors

/** Sezione con menu (Libreria o Impostazioni): una pagina per livello, con animazione avanti/indietro. */
@Composable
fun MenuPage(env: AppEnv, nav: Nav) {
    AnimatedContent(
        targetState = nav.top,
        transitionSpec = {
            val from = nav.stack.indexOf(initialState)
            val forward = nav.stack.indexOf(targetState) > from && from >= 0
            if (forward) {
                (slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 8 } + fadeOut(tween(160)))
            } else {
                (slideInHorizontally(tween(220)) { -it / 8 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(160)))
            }
        },
        label = "menu"
    ) { e ->
        val onBack: (() -> Unit)? = if (nav.stack.indexOf(e) > 0) ({ env.feedback.tapScreen(); nav.pop() }) else null
        if (nav === env.libraryNav) {
            val s = e.screen as? MenuScreen ?: return@AnimatedContent
            LibraryList(env, e, s, onBack)
        } else {
            when (val s = e.screen) {
                SettingsHomeScreen -> SettingsHome(env)
                AppearanceScreen -> AppearancePage(env, onBack)
                SignalPathScreen -> MLSettingsScaffold("Percorso del segnale", onBack) { SignalPathView(env) }
                is MenuScreen -> SettingsList(env, e, s, onBack)
            }
        }
    }
}

// ---------------------------------------------------------------- libreria

private fun rootIcon(label: String): ImageVector? = when (label) {
    "Artisti" -> Icons.Filled.Person
    "Album" -> Icons.Filled.Album
    "Brani" -> Icons.Filled.MusicNote
    "Generi" -> Icons.Filled.Category
    "Cartelle" -> Icons.Filled.Folder
    "Mescola brani", "Mescola album", "Mescola cartella" -> Icons.Filled.Shuffle
    "Riproduci cartella" -> Icons.Filled.PlayArrow
    "Aggiungi cartella musicale" -> Icons.Filled.CreateNewFolder
    "Cerca le copertine" -> Icons.Filled.Image
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryList(env: AppEnv, entry: Entry, screen: MenuScreen, onBack: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val items: List<MenuItem> =
        if (screen.cache) remember(entry, env.library.revision) { screen.build(env) } else screen.build(env)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(screen.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nessun elemento", color = cs.onSurfaceVariant)
            }
            return
        }
        LazyColumn(
            state = entry.listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            itemsIndexed(items) { _, item -> LibraryRow(env, item) }
        }
    }
}

@Composable
private fun LibraryRow(env: AppEnv, item: MenuItem) {
    val cs = MaterialTheme.colorScheme
    val hasCover = item.albumKey != null || item.track != null
    val icon = if (!hasCover) rootIcon(item.label) else null
    val sub = item.sub ?: item.track?.let { t -> listOf(t.artist, t.detailLine()).filter { it.isNotBlank() }.joinToString(" · ") }
    val clickable = !item.dimmed || item.sub != null
    ListItem(
        headlineContent = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (sub != null) ({ Text(sub, maxLines = 2, overflow = TextOverflow.Ellipsis) }) else null,
        leadingContent = when {
            hasCover && env.settings.coverInMenu -> ({ CoverThumb(env, item) })
            icon != null -> ({
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(cs.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = cs.onSecondaryContainer) }
            })
            else -> null
        },
        trailingContent = {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.track != null && item.track.isHiResFile) {
                    Box(
                        Modifier
                            .border(1.dp, cs.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("Hi-Res", style = MaterialTheme.typography.labelSmall, color = cs.primary) }
                }
                if (item.value != null) {
                    Text(
                        item.value, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (item.chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
            }
        },
        colors = ListItemDefaults.colors(containerColor = cs.surface),
        modifier = Modifier
            .alpha(if (item.dimmed) 0.6f else 1f)
            .clickable(enabled = clickable) { env.feedback.tapScreen(); item.onClick() }
    )
}

@Composable
private fun CoverThumb(env: AppEnv, item: MenuItem) {
    val cs = MaterialTheme.colorScheme
    val key = item.albumKey ?: item.track?.albumKey
    val file = remember(key, item.track?.id, env.library.revision) {
        item.track?.let { env.library.trackCoverFile(it) } ?: env.library.coverFile(key)
    }
    val bmp = rememberCover(file, 160)
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.MusicNote, null, tint = cs.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- impostazioni (sottopagine)

@Composable
private fun SettingsList(env: AppEnv, entry: Entry, screen: MenuScreen, onBack: (() -> Unit)?) {
    val items: List<MenuItem> =
        if (screen.cache) remember(entry, env.library.revision) { screen.build(env) } else screen.build(env)
    MLSettingsScaffold(screen.title, onBack) {
        if (items.isEmpty()) {
            Text("Nessun elemento", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        } else {
            MLGroup { for (item in items) SettingsRow(env, item) }
        }
    }
}

@Composable
private fun SettingsRow(env: AppEnv, item: MenuItem) {
    val cs = MaterialTheme.colorScheme
    when {
        item.toggle != null -> MLSwitchRow(item.label, item.sub, item.toggle) { env.feedback.tapScreen(); item.onClick() }
        item.dimmed && item.sub == null && !item.chevron -> ListItem(
            headlineContent = { Text(item.label, color = cs.onSurfaceVariant) },
            trailingContent = if (item.value != null) ({ Text(item.value, color = cs.onSurfaceVariant) }) else null,
            colors = mlPrefColors(),
        )
        else -> MLNavRow(
            title = item.label,
            subtitle = item.sub,
            trailing = when {
                item.chevron && item.value != null -> ({
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.value, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        MLChevron()
                    }
                })
                item.chevron -> ({ MLChevron() })
                item.value != null -> ({ Text(item.value, style = MaterialTheme.typography.labelLarge, color = cs.primary) })
                else -> null
            },
        ) { env.feedback.tapScreen(); item.onClick() }
    }
}
