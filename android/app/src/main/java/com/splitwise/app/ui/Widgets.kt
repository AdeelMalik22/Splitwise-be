package com.splitwise.app.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitwise.app.data.Member

private val avatarColors = listOf(
    Color(0xFF0F766E), Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFFBE123C), Color(0xFFB45309), Color(0xFF475569),
)

@Composable
fun Avatar(label: String, colorKey: Int, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(avatarColors[colorKey.mod(avatarColors.size)]),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AvatarStack(members: List<Member>, modifier: Modifier = Modifier, max: Int = 5) {
    Row(modifier) {
        members.take(max).forEachIndexed { i, m ->
            Avatar(
                initials(m.name, m.username).take(1), m.id, 24.dp,
                Modifier.offset(x = (-8 * i).dp).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

/** White rounded surface with the design's soft outline shadow. */
@Composable
fun SplitCard(modifier: Modifier = Modifier, radius: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .shadow(2.dp, RoundedCornerShape(radius), ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(radius)),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    Button(
        onClick = onClick, enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.height(32.dp).clip(RoundedCornerShape(8.dp))
            .background(if (primary) colors.primary else MaterialTheme.split.input)
            .then(if (primary) Modifier else Modifier.border(1.dp, colors.outline, RoundedCornerShape(8.dp)))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Color.White else colors.onSurface, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun IconField(
    value: String, onChange: (String) -> Unit, label: String, icon: ImageVector,
    modifier: Modifier = Modifier, password: Boolean = false, error: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text, onDone: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            leadingIcon = { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.split.fg3) },
            shape = RoundedCornerShape(12.dp),
            isError = error,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboard,
                imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Next,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.split.input,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.split.errorBg)
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.ErrorOutline, null, Modifier.size(16.dp).padding(top = 1.dp), tint = MaterialTheme.colorScheme.error)
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, link: String? = null, onLink: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(16.dp, 20.dp, 16.dp, 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title.uppercase(), style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.8.sp), color = MaterialTheme.split.fg2)
        if (link != null) Text(link, Modifier.clickable(onClick = onLink), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.primary)
    }
}

/** Square back button + title, as in the design's nav bar. */
@Composable
fun NavBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(18.dp), tint = MaterialTheme.split.fg2) }
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun StatusChip(text: String, kind: ChipKind) {
    val c = MaterialTheme.split
    val (bg, fg) = when (kind) {
        ChipKind.Pending -> c.oweBg to c.owe
        ChipKind.Done -> c.owedBg to c.owed
        ChipKind.Declined -> c.errorBg to MaterialTheme.colorScheme.error
    }
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(5.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp))
    }
}

enum class ChipKind { Pending, Done, Declined }

@Composable
fun Divider16() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)

/** Renders loading / error / content for a [Load]. */
@Composable
fun <T> LoadView(load: Load<T>, onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    when (load) {
        Load.Loading -> SkeletonList(modifier)
        is Load.Error -> Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(load.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            SmallButton("Retry", onRetry)
        }
        is Load.Ready -> content(load.data)
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
        Text(text, color = MaterialTheme.split.fg2, style = MaterialTheme.typography.bodyMedium)
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Groups("Groups", Icons.Default.Groups),
    Activity("Activity", Icons.Default.Notifications),
    Account("Account", Icons.Default.Person),
}

/** Bottom bar with the design's raised center "+" button. */
@Composable
fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit, onAdd: () -> Unit, unread: Boolean) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(top = 6.dp)) {
            fun item(t: Tab) = @Composable {
                val active = t == selected
                val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.split.fg3
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(onClick = { onSelect(t) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box {
                        Icon(t.icon, t.label, Modifier.size(24.dp), tint = tint)
                        if (t == Tab.Activity && unread) {
                            Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                    Text(t.label, color = tint, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.2.sp))
                }
            }
            item(Tab.Home)(); item(Tab.Groups)()
            Spacer(Modifier.weight(1f))
            item(Tab.Activity)(); item(Tab.Account)()
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-14).dp).size(54.dp)
                .shadow(8.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Add, "Add expense", Modifier.size(24.dp), tint = Color.White) }
    }
}

/** Shimmering placeholder rows shown while a screen loads. */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = 5) {
    val alpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(800), androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "alpha",
    )
    val block = MaterialTheme.split.input
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)).background(block.copy(alpha = alpha)))
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(block.copy(alpha = alpha)))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(180.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(block.copy(alpha = alpha)))
                    Box(Modifier.width(100.dp).height(10.dp).clip(RoundedCornerShape(6.dp)).background(block.copy(alpha = alpha)))
                }
            }
        }
    }
}
