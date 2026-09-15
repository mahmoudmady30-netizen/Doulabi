package com.doulabi.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Gold = Color(0xFFE9B957)
private val GoldBright = Color(0xFFFFD77A)
private val Bg = Color(0xFF08090C)
private val Card = Color(0xFF12151B)
private val Card2 = Color(0xFF191E27)
private val TextPrimary = Color(0xFFF7F3EA)
private val TextMuted = Color(0xFF9EA5B2)

private enum class Lang { AR, EN }
private enum class Screen { HOME, WARDROBE, SUGGEST, OCCASIONS, ANALYTICS, SETTINGS }
enum class Category { TOP, BOTTOM, SHOES, OUTERWEAR, ACCESSORY }
enum class Season { SUMMER, WINTER, ALL }
enum class Occasion { CASUAL, FORMAL, WORK, SPORT, PARTY }

data class ClothingItem(
    val id: Long,
    val name: String,
    val category: Category,
    val season: Season,
    val occasion: Occasion,
    val imagePath: String? = null,
    val color: String = "",
    val material: String = "",
    val favorite: Boolean = false,
    val wornCount: Int = 0
)

private data class Avatar(val id: String, val labelAr: String, val labelEn: String, val emoji: String)
private val avatars = listOf(
    Avatar("classic", "كلاسيكي", "Classic", "🧑🏻‍💼"), Avatar("modern", "عصري", "Modern", "😎"),
    Avatar("formal", "رسمي", "Formal", "👔"), Avatar("sport", "رياضي", "Sport", "🏃🏻"),
    Avatar("casual", "كاجوال", "Casual", "🧢"), Avatar("street", "ستريت", "Street", "🧥")
)

private fun t(lang: Lang, ar: String, en: String) = if (lang == Lang.AR) ar else en
private fun Category.label(l: Lang) = when (this) {
    Category.TOP -> t(l, "توب", "Tops"); Category.BOTTOM -> t(l, "بناطيل", "Bottoms"); Category.SHOES -> t(l, "أحذية", "Shoes")
    Category.OUTERWEAR -> t(l, "جاكيتات", "Outerwear"); Category.ACCESSORY -> t(l, "إكسسوارات", "Accessories")
}
private fun Season.label(l: Lang) = when (this) { Season.SUMMER -> t(l, "صيفي", "Summer"); Season.WINTER -> t(l, "شتوي", "Winter"); Season.ALL -> t(l, "كل المواسم", "All seasons") }
private fun Occasion.label(l: Lang) = when (this) { Occasion.CASUAL -> t(l, "كاجوال", "Casual"); Occasion.FORMAL -> t(l, "رسمي", "Formal"); Occasion.WORK -> t(l, "عمل", "Work"); Occasion.SPORT -> t(l, "رياضي", "Sport"); Occasion.PARTY -> t(l, "مناسبات", "Party") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { DoulabiApp() } }
}

@Composable
private fun DoulabiApp() {
    val context = LocalContext.current
    val store = remember { WardrobeStore(context) }
    var lang by remember { mutableStateOf(store.language) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var onboarding by remember { mutableStateOf(!store.onboarded) }
    var items by remember { mutableStateOf(store.items) }
    var avatar by remember { mutableStateOf(store.avatar) }
    var profileImage by remember { mutableStateOf(store.profileImage) }
    val aiStore = remember { AiSettingsStore(context) }
    var aiSettings by remember { mutableStateOf(aiStore.load()) }

    fun persist() { store.items = items; store.avatar = avatar; store.language = lang; store.profileImage = profileImage; aiStore.save(aiSettings) }

    CompositionLocalProvider(LocalLayoutDirection provides if (lang == Lang.AR) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Gold, secondary = GoldBright, background = Bg, surface = Card, onSurface = TextPrimary)) {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                if (onboarding) Onboarding(lang, avatar, profileImage) { chosen, photo -> avatar = chosen; profileImage = photo; store.onboarded = true; persist(); onboarding = false }
                else MainShell(lang, screen, { screen = it }, items, avatar, profileImage, aiSettings, { items = it; persist() }, { avatar = it; persist() }, { lang = it; persist() }, { profileImage = it; persist() }, { aiSettings = it; persist() })
            }
        }
    }
}

@Composable
private fun Onboarding(lang: Lang, current: Avatar, profileImage: String?, onDone: (Avatar, String?) -> Unit) {
    var chosen by remember { mutableStateOf(current) }
    var photo by remember { mutableStateOf(profileImage) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) photo = copyUri(context, uri) }
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(30.dp)); Text("دولابي", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Gold); Text("Doulabi", color = TextMuted)
        Spacer(Modifier.height(18.dp)); ProfilePhoto(photo, 92.dp)
        TextButton(onClick = { picker.launch("image/*") }) { Text(t(lang, "اختار صورتك", "Choose your photo"), color = Gold) }
        Text(t(lang, "اختار شخصيتك", "Choose your style"), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(t(lang, "هتساعد دولابي في اقتراح اللوك المناسب ليك.", "This helps Doulabi personalize your outfits."), color = TextMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(avatars.chunked(2)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { a -> AvatarCard(a, a.id == chosen.id, lang) { chosen = a }; if (row.size == 1) Spacer(Modifier.weight(1f)) } } }
        }
        Button(onClick = { onDone(chosen, photo) }, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black), shape = RoundedCornerShape(17.dp)) { Text(t(lang, "ابدأ الآن", "Start now"), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    }
}

@Composable private fun AvatarCard(a: Avatar, selected: Boolean, lang: Lang, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (selected) Gold.copy(.14f) else Card).border(1.5.dp, if (selected) Gold else Color.Transparent, RoundedCornerShape(20.dp)).clickable { onClick() }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(a.emoji, fontSize = 43.sp); Text(if (lang == Lang.AR) a.labelAr else a.labelEn, color = TextPrimary, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun MainShell(lang: Lang, screen: Screen, onScreen: (Screen) -> Unit, items: List<ClothingItem>, avatar: Avatar, profileImage: String?, aiSettings: AiSettings, onItems: (List<ClothingItem>) -> Unit, onAvatar: (Avatar) -> Unit, onLang: (Lang) -> Unit, onProfileImage: (String?) -> Unit, onAiSettings: (AiSettings) -> Unit) {
    Scaffold(containerColor = Bg, bottomBar = {
        NavigationBar(containerColor = Color(0xFF0D0F13)) {
            NavItem(Screen.HOME, Icons.Default.Home, t(lang, "الرئيسية", "Home"), screen, onScreen)
            NavItem(Screen.WARDROBE, Icons.Default.Checkroom, t(lang, "دولابي", "Wardrobe"), screen, onScreen)
            NavItem(Screen.SUGGEST, Icons.Default.AutoAwesome, t(lang, "اقتراحات", "Style"), screen, onScreen)
            NavItem(Screen.OCCASIONS, Icons.Default.Event, t(lang, "المناسبات", "Events"), screen, onScreen)
            NavItem(Screen.SETTINGS, Icons.Default.Settings, t(lang, "إعدادات", "Settings"), screen, onScreen)
        }
    }) { pad -> AnimatedContent(screen, modifier = Modifier.padding(pad), label = "screen") { s ->
        when (s) {
            Screen.HOME -> HomeScreen(lang, items, avatar, profileImage, { onScreen(Screen.SUGGEST) }, { onScreen(Screen.WARDROBE) })
            Screen.WARDROBE -> WardrobeScreen(lang, items, aiSettings, onItems)
            Screen.SUGGEST -> SuggestScreen(lang, items, onItems)
            Screen.OCCASIONS -> OccasionsScreen(lang, items, { onScreen(Screen.SUGGEST) })
            Screen.ANALYTICS -> AnalyticsScreen(lang, items)
            Screen.SETTINGS -> SettingsScreen(lang, avatar, profileImage, aiSettings, onAvatar, onLang, onProfileImage, { onScreen(Screen.ANALYTICS) }, onAiSettings)
        }
    } }
}

@Composable private fun RowScope.NavItem(target: Screen, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, current: Screen, onClick: (Screen) -> Unit) {
    NavigationBarItem(selected = current == target, onClick = { onClick(target) }, icon = { Icon(icon, null) }, label = { Text(label, maxLines = 1, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold, indicatorColor = Gold.copy(.12f), unselectedIconColor = TextMuted, unselectedTextColor = TextMuted))
}

@Composable private fun Header(lang: Lang, title: String, subtitle: String? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold); subtitle?.let { Text(it, color = TextMuted, fontSize = 13.sp) } }
        action?.let { IconButton(onClick = it) { Icon(Icons.Default.AddCircleOutline, null, tint = Gold) } }
    }
}

@Composable private fun HomeScreen(lang: Lang, items: List<ClothingItem>, avatar: Avatar, profileImage: String?, goSuggest: () -> Unit, goWardrobe: () -> Unit) {
    val outfit = RecommendationEngine.buildOutfit(items, Season.SUMMER, Occasion.CASUAL)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(lang, t(lang, "مرحباً 👋", "Welcome 👋"), t(lang, "كل دولابك في مكان واحد", "Your wardrobe in one place")) }
        item { Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Card2)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { ProfilePhoto(profileImage, 64.dp); Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(t(lang, "ستايلك", "Your style"), color = TextMuted); Text(if (lang == Lang.AR) avatar.labelAr else avatar.labelEn, color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold) }; Text(avatar.emoji, fontSize = 30.sp) } } }
        item { Spacer(Modifier.height(16.dp)); StatsRow(lang, items) }
        item { SectionTitle(lang, t(lang, "اقتراح اليوم", "Today's look"), t(lang, "اختارنا لك لوك سريع من دولابك", "A quick look from your wardrobe")) }
        item { OutfitCard(lang, outfit, Occasion.CASUAL, goSuggest) }
        item { SectionTitle(lang, t(lang, "تصنيف دولابك", "Wardrobe categories"), null) }
        item { CategoryGrid(lang, items, goWardrobe) }
    }
}

@Composable private fun StatsRow(lang: Lang, items: List<ClothingItem>) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) { Stat(t(lang, "كل القطع", "Items"), items.size.toString()); Stat(t(lang, "صيفي", "Summer"), items.count { it.season == Season.SUMMER }.toString()); Stat(t(lang, "شتوي", "Winter"), items.count { it.season == Season.WINTER }.toString()) } }
@Composable private fun Stat(label: String, value: String) { Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(17.dp)) { Column(Modifier.padding(13.dp)) { Text(value, color = Gold, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(label, color = TextMuted, fontSize = 11.sp) } } }
@Composable private fun SectionTitle(lang: Lang, title: String, sub: String?) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp); sub?.let { Text(it, color = TextMuted, fontSize = 12.sp) } } } }

@Composable private fun CategoryGrid(lang: Lang, items: List<ClothingItem>, go: () -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) { Category.values().take(4).forEach { c -> Card(Modifier.weight(1f).height(88.dp).clickable { go() }, colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(17.dp)) { Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) { Text(c.label(lang), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp); Text(items.count { it.category == c }.toString(), color = Gold, fontWeight = FontWeight.Bold, fontSize = 21.sp) } } } } }

@Composable private fun WardrobeScreen(lang: Lang, items: List<ClothingItem>, aiSettings: AiSettings, onItems: (List<ClothingItem>) -> Unit) {
    var filter by remember { mutableStateOf<Category?>(null) }; var seasonFilter by remember { mutableStateOf<Season?>(null) }; var search by remember { mutableStateOf("") }; var showAdd by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<ClothingItem?>(null) }
    val context = LocalContext.current
    val visible = items.filter { (filter == null || it.category == filter) && (seasonFilter == null || it.season == seasonFilter || it.season == Season.ALL) && it.name.contains(search, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        Header(lang, t(lang, "دولابي", "My wardrobe"), t(lang, "${items.size} ${t(lang, "قطعة منظمة", "organized pieces")}")) { showAdd = true }
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), placeholder = { Text(t(lang, "ابحث عن قطعة...", "Search your wardrobe...")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(15.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(t(lang, "الكل", "All")) }); Category.values().forEach { c -> FilterChip(selected = filter == c, onClick = { filter = c }, label = { Text(c.label(lang)) }) } }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { FilterChip(selected = seasonFilter == null, onClick = { seasonFilter = null }, label = { Text(t(lang, "كل المواسم", "All seasons")) }); Season.values().filter { it != Season.ALL }.forEach { s -> FilterChip(selected = seasonFilter == s, onClick = { seasonFilter = s }, label = { Text(s.label(lang)) }) } }
        if (visible.isEmpty()) EmptyState(lang) { showAdd = true }
        else LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { lazyItems(visible, key = { it.id }) { item -> ItemCard(lang, item, { editing = item }, { onItems(items.filterNot { it.id == item.id }); item.imagePath?.let { deleteFile(it) } }, { onItems(items.map { if (it.id == item.id) it.copy(favorite = !it.favorite) else it }) }) } }
    }
    if (showAdd) AddItemDialog(lang, context, aiSettings, null, { showAdd = false }) { newItem -> onItems(items + newItem); showAdd = false }
    editing?.let { current -> AddItemDialog(lang, context, aiSettings, current, { editing = null }) { updated -> onItems(items.map { if (it.id == current.id) updated else it }); editing = null } }
}

@Composable private fun ItemCard(lang: Lang, item: ClothingItem, onEdit: () -> Unit, onDelete: () -> Unit, onFavorite: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onEdit() }, colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(19.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(Card2)) { if (item.imagePath != null) AsyncImage(item.imagePath, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Default.Checkroom, null, tint = TextMuted, modifier = Modifier.padding(24.dp)) }
            Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(item.name, color = TextPrimary, fontWeight = FontWeight.Bold); Text("${item.category.label(lang)} • ${item.season.label(lang)}", color = TextMuted, fontSize = 12.sp); Text(item.occasion.label(lang) + if (item.color.isNotBlank()) " • ${item.color}" else "", color = Gold, fontSize = 12.sp); if (item.wornCount > 0) Text(t(lang, "لبسها ${item.wornCount} مرة", "Worn ${item.wornCount} times"), color = TextMuted, fontSize = 10.sp) }
            IconButton(onClick = onFavorite) { Icon(if (item.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (item.favorite) Gold else TextMuted) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null, tint = TextMuted) }
        }
    }
}

@Composable private fun EmptyState(lang: Lang, onAdd: () -> Unit) { Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("👕", fontSize = 62.sp); Text(t(lang, "دولابك لسه فاضي", "Your wardrobe is empty"), color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(t(lang, "صور أول قطعة وابدأ بناء دولابك الذكي.", "Add your first piece and build your smart wardrobe."), color = TextMuted, textAlign = TextAlign.Center); Spacer(Modifier.height(18.dp)); Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(t(lang, "إضافة قطعة", "Add item")) } } }

@Composable
private fun AddItemDialog(lang: Lang, context: Context, aiSettings: AiSettings, existing: ClothingItem?, onDismiss: () -> Unit, onSaved: (ClothingItem) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }; var category by remember(existing?.id) { mutableStateOf(existing?.category ?: Category.TOP) }; var season by remember(existing?.id) { mutableStateOf(existing?.season ?: Season.ALL) }; var occasion by remember(existing?.id) { mutableStateOf(existing?.occasion ?: Occasion.CASUAL) }; var color by remember(existing?.id) { mutableStateOf(existing?.color ?: "") }; var material by remember(existing?.id) { mutableStateOf(existing?.material ?: "") }; var favorite by remember(existing?.id) { mutableStateOf(existing?.favorite ?: false) }; var image by remember(existing?.id) { mutableStateOf(existing?.imagePath) }; var cameraPath by remember(existing?.id) { mutableStateOf<String?>(null) }
    var cameraUri by remember(existing?.id) { mutableStateOf<Uri?>(null) }
    var pendingCamera by remember { mutableStateOf(false) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiConfidence by remember { mutableStateOf<Double?>(null) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) pendingCamera = true }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) image = copyUri(context, uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) image = cameraPath }
    LaunchedEffect(pendingCamera) { if (pendingCamera) { pendingCamera = false; val file = createCameraFile(context); cameraPath = file.absolutePath; cameraUri = FileProvider.getUriForFile(context, "com.doulabi.app.fileprovider", file); camera.launch(cameraUri!!) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) t(lang, "إضافة قطعة", "Add clothing") else t(lang, "تعديل القطعة", "Edit clothing"), fontWeight = FontWeight.Bold) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t(lang, "اسم القطعة *", "Item name *")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) { ChoiceMenu(t(lang, "النوع", "Type"), category.label(lang), Category.values().toList(), { it.label(lang) }, Modifier.weight(1f)) { category = it }; ChoiceMenu(t(lang, "الموسم", "Season"), season.label(lang), Season.values().toList(), { it.label(lang) }, Modifier.weight(1f)) { season = it } }
            Text(t(lang, "المناسبة", "Occasion"), color = TextMuted, fontSize = 12.sp); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Occasion.values().forEach { o -> FilterChip(selected = occasion == o, onClick = { occasion = o }, label = { Text(o.label(lang)) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) { OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text(t(lang, "اللون", "Color")) }, singleLine = true, modifier = Modifier.weight(1f)); OutlinedTextField(value = material, onValueChange = { material = it }, label = { Text(t(lang, "الخامة", "Material")) }, singleLine = true, modifier = Modifier.weight(1f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) { Button(onClick = { picker.launch("image/*") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Card2)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(5.dp)); Text(t(lang, "المعرض", "Gallery")) }; Button(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) pendingCamera = true else permission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Card2)) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(5.dp)); Text(t(lang, "الكاميرا", "Camera")) } }
            if (image != null) {
                Text(t(lang, "✓ الصورة جاهزة", "✓ Photo ready"), color = Gold)
                AsyncImage(image, null, Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(4.dp))
                Button(
                    enabled = !aiLoading && aiSettings.activeConfig().enabled && aiSettings.activeConfig().apiKey.isNotBlank(),
                    onClick = {
                        val path = image
                        if (path != null) {
                            aiLoading = true; aiError = null
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { AiVisionService.analyze(path, aiSettings, lang) } }
                                    .onSuccess { result ->
                                        name = result.name; category = result.category; season = result.season; occasion = result.occasion
                                        color = result.color; material = result.material; aiConfidence = result.confidence
                                    }
                                    .onFailure { aiError = it.message ?: "AI Vision failed" }
                                aiLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (aiLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                    else Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(7.dp))
                    Text(t(lang, "حلل الصورة بالـ AI", "Analyze with AI Vision"), fontWeight = FontWeight.Bold)
                }
                if (!aiSettings.activeConfig().enabled || aiSettings.activeConfig().apiKey.isBlank()) {
                    Text(t(lang, "فعّل مزود AI وأضف API Key من الإعدادات أولاً.", "Enable an AI provider and add an API key in Settings first."), color = TextMuted, fontSize = 11.sp)
                }
                aiConfidence?.let { Text(t(lang, "ثقة التحليل: ${"%.0f".format(it * 100)}%", "Analysis confidence: ${"%.0f".format(it * 100)}%"), color = Gold, fontSize = 11.sp) }
                aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = favorite, onCheckedChange = { favorite = it }); Text(t(lang, "مفضلة", "Favorite"), color = TextPrimary) }
        }
    }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSaved(ClothingItem(existing?.id ?: System.currentTimeMillis(), name.trim(), category, season, occasion, image, color.trim(), material.trim(), favorite, existing?.wornCount ?: 0)) }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) { Text(t(lang, "حفظ", "Save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(t(lang, "إلغاء", "Cancel")) } })
}

@Composable private fun <T> ChoiceMenu(label: String, value: String, options: List<T>, display: (T) -> String, modifier: Modifier, onValue: (T) -> Unit) { var open by remember { mutableStateOf(false) }; Box(modifier) { OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(value, maxLines = 1) }; DropdownMenu(expanded = open, onDismissRequest = { open = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(display(option)) }, onClick = { open = false; onValue(option) }) } } } }

@Composable private fun SuggestScreen(lang: Lang, items: List<ClothingItem>, onItems: (List<ClothingItem>) -> Unit) {
    var selectedOcc by remember { mutableStateOf(Occasion.CASUAL) }; var selectedSeason by remember { mutableStateOf(Season.SUMMER) }; val outfit = RecommendationEngine.buildOutfit(items, selectedSeason, selectedOcc)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { Header(lang, t(lang, "ستايل الذكاء الاصطناعي", "AI Stylist"), t(lang, "اقتراحات من القطع الموجودة عندك" , "Suggestions from your wardrobe")) }
        item { Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(15.dp)) { Text(t(lang, "المناسبة", "Occasion"), color = TextMuted); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Occasion.values().forEach { o -> FilterChip(selected = selectedOcc == o, onClick = { selectedOcc = o }, label = { Text(o.label(lang)) }) } }; Spacer(Modifier.height(8.dp)); Text(t(lang, "الموسم", "Season"), color = TextMuted); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Season.values().forEach { s -> FilterChip(selected = selectedSeason == s, onClick = { selectedSeason = s }, label = { Text(s.label(lang)) }) } } } } }
        item { OutfitCard(lang, outfit, selectedOcc, null) }
        item { SectionTitle(lang, t(lang, "القطع المستخدمة", "Pieces used"), t(lang, "تقدر تضغط على القطعة لتعديلها", "Tap a piece to edit it")) }
        lazyItems(outfit.pieces, key = { it.id }) { piece -> ItemCard(lang, piece, {}, { onItems(items.filterNot { it.id == piece.id }) }, { onItems(items.map { if (it.id == piece.id) it.copy(favorite = !it.favorite) else it }) }) }
        item { SectionTitle(lang, t(lang, "ليه اللوك ده؟", "Why this look?"), null) }
        item { InsightCard(lang, selectedOcc, selectedSeason, outfit) }
    }
}

@Composable private fun OutfitCard(lang: Lang, outfit: Outfit, occasion: Occasion, onClick: (() -> Unit)?) { Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), colors = CardDefaults.cardColors(containerColor = Card2), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(15.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(t(lang, "لوك مقترح", "Recommended look"), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(occasion.label(lang), color = Gold) }; Text("✨", fontSize = 30.sp) }; Spacer(Modifier.height(12.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(outfit.top, outfit.bottom, outfit.outerwear, outfit.shoes, outfit.accessory).forEach { MiniItem(it, lang) } } } } }
@Composable private fun MiniItem(item: ClothingItem?, lang: Lang) { Card(Modifier.width(82.dp).height(102.dp), colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(14.dp)) { if (item?.imagePath != null) AsyncImage(item.imagePath, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Checkroom, null, tint = TextMuted); if (item != null) Text(item.category.label(lang), color = TextMuted, fontSize = 9.sp) } } } }
@Composable private fun InsightCard(lang: Lang, occasion: Occasion, season: Season, outfit: Outfit) { Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(19.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Default.AutoAwesome, null, tint = Gold); Spacer(Modifier.width(10.dp)); Text(if (outfit.pieces.size >= 3) t(lang, "اللوك متوازن لأن عندك قطع متوافقة للمناسبة والموسم المختارين.", "This look is balanced because your wardrobe has pieces matching the selected occasion and season.") else t(lang, "أضف قطع أكثر خصوصاً من الأنواع الناقصة للحصول على لوكات مكتملة.", "Add more pieces, especially missing categories, to get complete looks."), color = TextMuted, lineHeight = 20.sp) } } }

@Composable private fun OccasionsScreen(lang: Lang, items: List<ClothingItem>, goSuggest: () -> Unit) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) { item { Header(lang, t(lang, "المناسبات", "Occasions"), t(lang, "اعرف تجهيزات دولابك لكل حدث", "Know what your wardrobe has for every event")) }; lazyItems(Occasion.values().toList()) { o -> val count = items.count { it.occasion == o }; Card(Modifier.padding(horizontal = 18.dp, vertical = 5.dp).fillMaxWidth().clickable { goSuggest() }, colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(46.dp).clip(CircleShape).background(Gold.copy(.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Event, null, tint = Gold) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(o.label(lang), color = TextPrimary, fontWeight = FontWeight.Bold); Text(t(lang, "قطع مناسبة: ", "Matching pieces: ") + count, color = TextMuted, fontSize = 12.sp) }; Icon(Icons.Default.ChevronLeft, null, tint = TextMuted) } } } } }

@Composable private fun AnalyticsScreen(lang: Lang, items: List<ClothingItem>) { val total = items.size; val favorites = items.count { it.favorite }; val missing = Category.values().filter { c -> items.none { it.category == c } }; LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) { item { Header(lang, t(lang, "تحليل دولابك", "Wardrobe analytics"), t(lang, "إيه الموجود وإيه اللي ناقص", "What you own and what is missing")) }; item { Card(Modifier.padding(18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Card2), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(18.dp)) { Text(t(lang, "إجمالي القطع", "Total items"), color = TextMuted); Text(total.toString(), color = Gold, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold); Text(t(lang, "$favorites قطعة مفضلة", "$favorites favorites"), color = TextMuted); Spacer(Modifier.height(14.dp)); Category.values().forEach { c -> val n = items.count { it.category == c }; Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(c.label(lang), color = TextPrimary, modifier = Modifier.weight(1f)); Text(n.toString(), color = Gold, fontWeight = FontWeight.Bold) } } } } }; item { SectionTitle(lang, t(lang, "نصيحة دولابي", "Doulabi insight"), null) }; item { Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(20.dp)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Default.Lightbulb, null, tint = Gold); Spacer(Modifier.width(10.dp)); Text(if (missing.isNotEmpty()) t(lang, "ممكن تبدأ بإضافة: ${missing.joinToString { it.label(lang) }} عشان تبني لوكات أكتر.", "Consider adding: ${missing.joinToString { it.label(lang) }} to build more complete looks.") else t(lang, "دولابك متنوع! جرّب المناسبات والمواسم المختلفة للحصول على لوكات جديدة.", "Your wardrobe is diverse! Try different occasions and seasons for new looks."), color = TextMuted, lineHeight = 20.sp) } } } } }

@Composable private fun SettingsScreen(lang: Lang, avatar: Avatar, profileImage: String?, aiSettings: AiSettings, onAvatar: (Avatar) -> Unit, onLang: (Lang) -> Unit, onProfileImage: (String?) -> Unit, analytics: () -> Unit, onAiSettings: (AiSettings) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onProfileImage(copyUri(context, uri)) }
    var aiDialog by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item { Header(lang, t(lang, "الإعدادات", "Settings")) }
        item { SettingSection(t(lang, "صورتك", "Your photo")) { Row(verticalAlignment = Alignment.CenterVertically) { ProfilePhoto(profileImage, 70.dp); Spacer(Modifier.width(12.dp)); Button(onClick = { picker.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = Card2)) { Text(t(lang, "تغيير الصورة", "Change photo")) } } } }
        item { SettingSection(t(lang, "اللغة", "Language")) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = lang == Lang.AR, onClick = { onLang(Lang.AR) }, label = { Text("العربية") }); FilterChip(selected = lang == Lang.EN, onClick = { onLang(Lang.EN) }, label = { Text("English") }) } } }
        item { SettingSection(t(lang, "شخصيتك", "Your style")) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { avatars.forEach { a -> AvatarCardSmall(a, a.id == avatar.id, lang) { onAvatar(a) } } } } }
        item { SettingSection(t(lang, "AI Vision", "AI Vision")) {
            val active = aiSettings.activeProvider
            Text(t(lang, "مزود التحليل الحالي", "Active vision provider"), color = TextMuted, fontSize = 12.sp)
            Text(active.title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(5.dp))
            val activeConfig = aiSettings.activeConfig()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (activeConfig.enabled && activeConfig.apiKey.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (activeConfig.enabled && activeConfig.apiKey.isNotBlank()) Gold else TextMuted)
                Spacer(Modifier.width(7.dp))
                Text(if (activeConfig.enabled && activeConfig.apiKey.isNotBlank()) t(lang, "جاهز لتحليل صور الملابس", "Ready for clothing image analysis") else t(lang, "لم يتم إعداد مزود AI بعد", "No AI provider configured yet"), color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { aiDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Card2)) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(7.dp)); Text(t(lang, "إدارة مزودي AI و API Keys", "Manage AI providers & API keys")) }
            Text(t(lang, "المفتاح يُحفظ مشفراً داخل Android Keystore ولا يتم وضعه داخل الكود.", "Keys are encrypted with Android Keystore and are not embedded in the app."), color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 7.dp))
        } }
        item { SettingSection(t(lang, "التقارير", "Insights")) { Button(onClick = analytics, colors = ButtonDefaults.buttonColors(containerColor = Card2)) { Icon(Icons.Default.BarChart, null); Spacer(Modifier.width(8.dp)); Text(t(lang, "تحليل دولابي", "Wardrobe analytics")) } } }
        item { SettingSection(t(lang, "عن دولابي", "About Doulabi")) { Text(t(lang, "دولابي ينظم صور ملابسك ويصنفها ويقترح لوكات حسب الموسم والمناسبة والستايل. يمكنك اختيار مزود AI بنفسك وإضافة مفتاحك من الإعدادات.", "Doulabi organizes your clothing photos and suggests outfits by season, occasion and style. You can choose your own AI provider and add your API key in Settings."), color = TextMuted, lineHeight = 20.sp) } }
    }
    if (aiDialog) AiProvidersDialog(lang, aiSettings, onAiSettings) { aiDialog = false }
}

@Composable private fun AiProvidersDialog(lang: Lang, initial: AiSettings, onSave: (AiSettings) -> Unit, onDismiss: () -> Unit) {
    var active by remember { mutableStateOf(initial.activeProvider) }
    var selected by remember { mutableStateOf(initial.activeProvider) }
    var configs by remember { mutableStateOf(initial.providers) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val config = configs[selected] ?: AiProviderConfig(model = selected.defaultModel, baseUrl = selected.defaultBaseUrl)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t(lang, "مزودو AI Vision", "AI Vision providers"), fontWeight = FontWeight.Bold) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(t(lang, "اختار المزود الذي سيحلل صور الملابس. يمكنك حفظ أكثر من مزود وتحديد واحد نشط.", "Choose the provider that analyzes clothing photos. You can save multiple providers and select one active provider."), color = TextMuted, fontSize = 12.sp)
            AiProvider.values().forEach { p ->
                val c = configs[p] ?: AiProviderConfig(model = p.defaultModel, baseUrl = p.defaultBaseUrl)
                Card(Modifier.fillMaxWidth().clickable { selected = p; testResult = null }, colors = CardDefaults.cardColors(containerColor = if (selected == p) Gold.copy(.10f) else Card2), border = BorderStroke(1.dp, if (selected == p) Gold else Color.Transparent), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(p.title, color = TextPrimary, fontWeight = FontWeight.Bold); Text(if (c.enabled && c.apiKey.isNotBlank()) t(lang, "مُعدّ", "Configured") else t(lang, "غير مُعدّ", "Not configured"), color = if (c.enabled && c.apiKey.isNotBlank()) Gold else TextMuted, fontSize = 11.sp) }
                        Switch(checked = c.enabled, onCheckedChange = { configs = configs + (p to c.copy(enabled = it)) })
                        if (active == p) Icon(Icons.Default.RadioButtonChecked, null, tint = Gold) else IconButton(onClick = { active = p; testResult = null }) { Icon(Icons.Default.RadioButtonUnchecked, null, tint = TextMuted) }
                    }
                }
            }
            Text(selected.title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            OutlinedTextField(value = config.apiKey, onValueChange = { configs = configs + (selected to config.copy(apiKey = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text(t(lang, "API Key", "API Key")) }, singleLine = true, visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } })
            OutlinedTextField(value = config.model, onValueChange = { configs = configs + (selected to config.copy(model = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text(t(lang, "Model", "Model")) }, singleLine = true)
            if (selected == AiProvider.CUSTOM || selected == AiProvider.OPENROUTER) OutlinedTextField(value = config.baseUrl, onValueChange = { configs = configs + (selected to config.copy(baseUrl = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text(t(lang, "Base URL", "Base URL")) }, singleLine = true)
            if (selected != AiProvider.CUSTOM && selected != AiProvider.OPENROUTER) Text(t(lang, "يمكنك تغيير الـ Model. الـ Base URL مضبوط تلقائياً.", "You can change the model. Base URL is preset."), color = TextMuted, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(enabled = !testing && config.apiKey.isNotBlank(), onClick = {
                    testing = true; testResult = null
                    scope.launch { runCatching { withContext(Dispatchers.IO) { AiVisionService.test(selected, config, lang) } }.onSuccess { testResult = t(lang, "✓ الاتصال يعمل", "✓ Connection works") }.onFailure { testResult = it.message ?: "Test failed" }; testing = false }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Card2)) { if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.NetworkCheck, null); Spacer(Modifier.width(5.dp)); Text(t(lang, "اختبار", "Test")) }
                Button(onClick = { onSave(AiSettings(active, configs)); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(5.dp)); Text(t(lang, "حفظ", "Save")) }
            }
            testResult?.let { Text(it, color = if (it.startsWith("✓")) Gold else MaterialTheme.colorScheme.error, fontSize = 11.sp) }
            Text(t(lang, "مهم: الـ API Key يُستخدم مباشرة من هاتفك للوصول إلى المزود. راجع حدود المفتاح وصلاحياته قبل الاستخدام.", "Important: the API key is used directly from your phone to reach the provider. Review key restrictions and limits before use."), color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(t(lang, "إغلاق", "Close")) } })
}

@Composable private fun SettingSection(title: String, content: @Composable () -> Unit) { Column(Modifier.padding(horizontal = 18.dp, vertical = 7.dp)) { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(7.dp)); Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp)) { content() } } } }
@Composable private fun AvatarCardSmall(a: Avatar, selected: Boolean, lang: Lang, onClick: () -> Unit) { Column(Modifier.width(82.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) Gold.copy(.12f) else Card2).border(1.dp, if (selected) Gold else Color.Transparent, RoundedCornerShape(15.dp)).clickable { onClick() }.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(a.emoji, fontSize = 30.sp); Text(if (lang == Lang.AR) a.labelAr else a.labelEn, color = TextPrimary, fontSize = 10.sp, maxLines = 1) } }
@Composable private fun ProfilePhoto(path: String?, size: androidx.compose.ui.unit.Dp) { Box(Modifier.size(size).clip(CircleShape).background(Gold.copy(.12f)), contentAlignment = Alignment.Center) { if (path != null) AsyncImage(path, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Default.Person, null, tint = Gold, modifier = Modifier.size(size / 2)) } }

private class WardrobeStore(context: Context) {
    private val p = context.getSharedPreferences("doulabi", Context.MODE_PRIVATE)
    var onboarded: Boolean get() = p.getBoolean("onboarded", false) set(v) = p.edit().putBoolean("onboarded", v).apply()
    var language: Lang get() = if (p.getString("lang", "ar") == "en") Lang.EN else Lang.AR set(v) = p.edit().putString("lang", if (v == Lang.EN) "en" else "ar").apply()
    var avatar: Avatar get() = avatars.firstOrNull { it.id == p.getString("avatar", "classic") } ?: avatars.first() set(v) = p.edit().putString("avatar", v.id).apply()
    var profileImage: String? get() = p.getString("profile_image", null) set(v) = p.edit().putString("profile_image", v).apply()
    var items: List<ClothingItem>
        get() { val arr = JSONArray(p.getString("items", "[]")); return (0 until arr.length()).mapNotNull { runCatching { val o = arr.getJSONObject(it); ClothingItem(o.getLong("id"), o.getString("name"), Category.valueOf(o.getString("category")), Season.valueOf(o.getString("season")), Occasion.valueOf(o.getString("occasion")), o.optString("image", null), o.optString("color", ""), o.optString("material", ""), o.optBoolean("favorite", false), o.optInt("wornCount", 0)) }.getOrNull() } }
        set(value) { val arr = JSONArray(); value.forEach { o -> arr.put(JSONObject().apply { put("id", o.id); put("name", o.name); put("category", o.category.name); put("season", o.season.name); put("occasion", o.occasion.name); put("image", o.imagePath); put("color", o.color); put("material", o.material); put("favorite", o.favorite); put("wornCount", o.wornCount) }) }; p.edit().putString("items", arr.toString()).apply() }
}

private fun copyUri(context: Context, uri: Uri): String? = runCatching { val dir = File(context.filesDir, "images").apply { mkdirs() }; val file = File(dir, "item_${System.currentTimeMillis()}.jpg"); context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }; file.absolutePath }.getOrNull()
private fun createCameraFile(context: Context): File { val dir = File(context.filesDir, "images").apply { mkdirs() }; return File(dir, "camera_${System.currentTimeMillis()}.jpg") }
private fun deleteFile(path: String) { runCatching { File(Uri.parse(path).path ?: path).delete() } }
