# Skim components

How each component is drawn and which Compose Material 3 Expressive API builds it. Where a drawn value differs from a component default, the drawing wins: pass colors, shapes and text styles through the component parameters.

## Floating toolbar

Screens: 04, 05, 05c, 08, 09c, 11

- 64 high, full radius, surfaceContainer (surfaceContainerHigh in dark).
- Back, Forward, Tabs and More options as 48 icon buttons.
- Sits 24 above the bottom edge; pages scroll underneath it.
- Tabs shows the count in a 22 square with a 2 border and radius 7.

```kotlin
HorizontalFloatingToolbar(expanded = true,
    floatingActionButton = { AiFab() },
    colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
) { BackButton(); ForwardButton(); TabsButton(); MoreButton() }
```

## AI FAB

Screens: 04, 05, 05c, 08, 09c, 11

- 56, radius 16, tertiaryContainer with a filled auto_awesome, 8 from the bar.
- On a page it opens 06; on Home (04) it opens 06b.
- Disabled on 09c and 11: onSurface at 10% behind 38% content, no shadow.
- Compose FABs have no enabled state: draw that look and drop the click.

```kotlin
FloatingToolbarDefaults.StandardFloatingActionButton(
    onClick = openAsk,
    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
```

## Address bar

Screens: 05 to 05d, 06 to 06d, 11

- 56 high, full radius, surfaceContainerHigh, with 8 by 16 padding around it.
- Site information (lock, or error on 11), the address as a button, Reader view.
- While a page loads, wavy progress in primary runs along its bottom edge.
- Tapping the address opens 04b with the URL ready to edit.

```kotlin
Surface(shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Row { IconButton(); TextButton(); IconButton() } }
LinearWavyProgressIndicator(progress = { loadProgress })
```

## Search bar

Screens: 04, 04b, 09c

- 56 high, full radius, surfaceContainerHigh; search icon, placeholder, mic.
- Expands into 04b: the field moves up, suggestions sit in segmented groups.
- Close search and Clear text replace the icons while typing.
- 09c draws the same bar with the dark scheme.

```kotlin
val state = rememberSearchBarState()
SearchBar(state, inputField = { SearchBarDefaults.InputField(...) })
ExpandedFullScreenContainedSearchBar(state, inputField) {
    SuggestionGroups() }
```

## Large title bar

Screens: 04c, 10, 12, 13, 14

- A 64 row with Back and up to two trailing icon buttons.
- The title sits below in headlineLargeEmphasized, 16 from the edge.
- On scroll the title collapses into the 64 row.
- 03 uses the same title without a bar; 09 uses headlineMediumEmphasized.

```kotlin
LargeFlexibleTopAppBar(title = { Text("Settings") },
    navigationIcon = { BackButton() },
    scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior())
```

## Segmented list

Screens: 04b, 04c, 05b, 05d, 09c, 10, 12 to 14

- Items in surfaceContainerLowest on surfaceContainerLow, 2 apart.
- Outer corners 20 and inner corners 4; a single item is 20 all round.
- 56 for one line, 72 for two; leading tile 40, Cookie4Sided for sites.
- Section labels in titleSmallEmphasized and primary, 32 from the edge.

```kotlin
SegmentedListItem(onClick = { open(item) },
    shapes = ListItemDefaults.segmentedShapes(index, count),
    colors = ListItemDefaults.segmentedColors(...)) { Text(title) }
Column(verticalArrangement = spacedBy(ListItemDefaults.SegmentedGap))
```

## Connected button group

Screens: 08b, 09, 09b

- 40 high, 2 apart. The selected button is a full pill in primary with a check.
- The others use surfaceContainer, with 20 on the outer side and 8 inside.
- Single choice, so one button is always selected.
- Tab list: Open, Incognito. Font: Sans serif, Serif.

```kotlin
Row(horizontalArrangement =
    Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
  ToggleButton(checked, onCheckedChange, shapes = ButtonGroupDefaults
      .connectedLeadingButtonShapes()) }  // then Middle, Trailing
```

## Buttons

Screens: 01, 03, 06, 06b, 15

- Full radius. M is 56 high (Get started), S is 40, XS is 32.
- S comes filled, tonal or text. XS is outlined for AI suggestions
- and tonal for citations such as From paragraph 6.
- M uses titleMedium; S and XS use labelLarge. Dialog actions are text.

```kotlin
Button, FilledTonalButton, TextButton, OutlinedButton
// 32, 40 and 56 high, sized with the ButtonDefaults size helpers
```

## Icon buttons

Screens: every screen

- 48 standard, transparent, onSurfaceVariant icon at 24 (20 in the address bar).
- Send is a filled 40 in primary.
- Text size on 08 is a toggle: once selected it becomes a tonal rounded
- square with a filled icon (08b).

```kotlin
IconButton(onClick) { Icon(painterResource(id), contentDescription) }
FilledIconButton(onClick = send)  // Send
FilledTonalIconToggleButton(checked, onCheckedChange)  // Text size
```

## FAB and tab switcher toolbar

Screens: 09, 09b

- 56, radius 16, primaryContainer: New tab (09), New incognito tab (09b).
- Beside it, a floating toolbar holds Ask Skim about these tabs in tertiary,
- New incognito tab and Close all tabs. On 09b the AI button is disabled.
- Both sit 24 above the bottom edge.

```kotlin
HorizontalFloatingToolbar(expanded = true, floatingActionButton = {
    FloatingToolbarDefaults.StandardFloatingActionButton(newTab,
        containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Icon(painterResource(R.drawable.ic_add), "New tab") } }) { }
```

## Bottom sheet

Screens: 05b, 05d, 06, 06b, 06c, 08b

- Top radius 28, surfaceContainerLow, 32 by 4 handle in a 36 row, scrim 32%.
- 06 opens at half height and drags to full; Close or swipe down dismisses.
- 08b has no scrim, and tapping the page closes it.
- Content inside follows the segmented list rules.

```kotlin
val sheet = rememberModalBottomSheetState()
ModalBottomSheet(onDismissRequest = close, sheetState = sheet) { }
// 08b: ModalBottomSheet(..., scrimColor = Color.Transparent)
```

## Dialog

Screens: 06d, 15

- 344 wide, radius 28, surfaceContainerHigh, padding 24.
- Optional icon above a centered title; 06d uses Sunny in tertiaryContainer.
- Text buttons on the right, for example Not now, then Allow.
- Destructive confirm buttons use error.

```kotlin
AlertDialog(onDismissRequest = notNow, icon = { SunnyMark() },
    title = { Text(title) }, text = { Text(body) },
    confirmButton = { TextButton(allow) { Text("Allow") } },
    dismissButton = { TextButton(notNow) { Text("Not now") } })
```

## Menu

Screens: 15

- Items grouped on surfaceContainer, radius 16 and padding 4 per group.
- 2 between groups, and destructive items get a group of their own.
- Items are 48 high with radius 12, labelLarge and a 24 leading icon.
- Anchored to the control that opened it.

```kotlin
DropdownMenuPopup(expanded, onDismissRequest = close) {
    DropdownMenuGroup(...) { DropdownMenuItem(text, onClick) }
    DropdownMenuGroup(...) { DropdownMenuItem(...) }  // Delete
}
```

## Snackbar

Screens: 15

- inverseSurface, radius 4, at least 48 high, text in inverseOnSurface.
- Action in inversePrimary. One at a time, 8 above the floating toolbar.
- With an action it stays 10 s, without one 4 s.

```kotlin
SnackbarHost(hostState, Modifier.padding(bottom = aboveToolbar))
hostState.showSnackbar("Tab closed", actionLabel = "Undo",
    duration = SnackbarDuration.Long)
```

## Switch

Screens: 10, 15

- 52 by 32. On: primary track and a 24 onPrimary thumb with a check.
- Off: surfaceContainerHighest track, 2 outline border, 16 outline thumb.

```kotlin
Switch(checked, onCheckedChange, thumbContent = if (checked) {
    { Icon(painterResource(R.drawable.ic_check), null) }
} else null)
```

## Slider

Screens: 08b

- Track 16 high with a 6 gap on each side of a 4 by 44 handle.
- Active track primary, inactive secondaryContainer, a stop dot at the end.
- Text size from 80 to 200 percent in steps of 10, applied live.

```kotlin
Slider(value = textSize, onValueChange = { textSize = it },
    valueRange = 80f..200f, steps = 11)
```

## Loading indicator

Screens: 06c

- A 48 circle in tertiaryContainer with a shape morphing in onTertiaryContainer.
- The board shows one frame, Cookie9Sided.
- Pull to refresh on 05 uses the same indicator with its defaults.

```kotlin
ContainedLoadingIndicator(
    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    indicatorColor = MaterialTheme.colorScheme.onTertiaryContainer)
```

## Wavy progress

Screens: 05, 06c

- Stroke 4, amplitude 3, wavelength 40, then a gap of 8 to a flat track.
- Track in secondaryContainer with a stop dot at the end.
- primary for page loads (05), tertiary while Skim writes (06c).

```kotlin
LinearWavyProgressIndicator(progress = { loadProgress })  // 05
LinearWavyProgressIndicator(
    color = MaterialTheme.colorScheme.tertiary)  // 06c, indeterminate
```

## Badge

Screens: 09

- 24 high, full radius, secondaryContainer, labelMediumEmphasized.
- Tab counts on group headers.

```kotlin
Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text("4") }
```

## Cards

Screens: 03, 04, 06, 06b

- Radius 20: surfaceContainerLow on surface, white on surfaceContainerLow.
- Hero cards use radius 28; the AI hero card on Home is tertiaryContainer.
- AI answer cards are white with radius 20 and padding 16.

```kotlin
Card(shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
```

## Tab card

Screens: 09, 09b

- Two columns 12 apart, radius 20, surfaceContainerLow.
- 44 title row with a Cookie4Sided favicon and Close tab.
- 92 preview with radius 12. The current tab gets a 3 inset ring in primary.

```kotlin
LazyVerticalGrid(GridCells.Fixed(2),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)) { }
// Preview: a bitmap captured from the tab WebView
```

## Chat

Screens: 06, 06b

- User bubble in secondaryContainer, corners 20 20 4 20, bodyMedium.
- Answers as plain text with a Tonal XS citation, then Copy and feedback.
- Input pill 56, surfaceContainerHigh, with a 40 filled Send.
- Text input only; chat has no voice input.

```kotlin
LazyColumn { items(messages) { Message(it) } }
BasicTextField(state) inside Surface(shape = CircleShape)
FilledIconButton(onClick = send) { Icon(...) }
```

## Pull to refresh

Screens: 05

- Web pages only, when the page is scrolled to the top.
- There is no reload button; entering the URL again also reloads.

```kotlin
PullToRefreshBox(isRefreshing, onRefresh = { webView.reload() },
    state = ptr, indicator = { PullToRefreshDefaults.LoadingIndicator(
        ptr, isRefreshing, Modifier.align(Alignment.TopCenter)) }) {
    WebPage() }
```

## Web content

Screens: 05, 06, 08, 09b

- One WebView per tab. Recent tabs stay alive; others restore from saved state.
- Incognito tabs share their own profile, deleted with the last incognito tab.
- Incognito sets FLAG_SECURE: no screenshots and a blank Recents preview.

```kotlin
AndroidView(factory = { context -> WebView(context) })
ProfileStore.getInstance().getOrCreateProfile("incognito")
WebViewCompat.setProfile(webView, "incognito")  // androidx.webkit
```
