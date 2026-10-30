# Skim tokens

Material 3 Expressive. Surfaces are neutral, in the same gray family as Chrome and Safari. Light accent is #0B57D0; dark accent is #8AB4F8. AI stays on the tertiary roles.
Light is the default. Dark is used for incognito (09b, 09c) and for every screen when Theme is Dark (12).

## Color roles

| Role | Light | Dark | Used for |
|---|---|---|---|
| primary | #0B57D0 | #8AB4F8 | Links, Send, selected toggles, section labels, progress, reader highlight at 32% |
| onPrimary | #FFFFFF | #202124 | Text and icons on primary |
| primaryContainer | #0B57D0 | #8AB4F8 | New tab FABs, Default browser tile |
| onPrimaryContainer | #FFFFFF | #202124 | Text and icons on primaryContainer |
| secondaryContainer | #E8EAED | #3C4043 | Tonal buttons, chat bubbles, badges, list tiles |
| onSecondaryContainer | #202124 | #E8EAED | Text and icons on secondaryContainer |
| inversePrimary | #8AB4F8 | #0B57D0 | Snackbar actions |
| inverseSurface | #303134 | #E8EAED | Snackbars |
| tertiary | #8E3A88 | #FFAFF2 | AI labels, the AI icon on 09, AI progress |
| tertiaryContainer | #FC99EF | #FC99EF | AI FAB, Sunny tiles, AI hero card, AI banner |
| onTertiaryContainer | #651162 | #651162 | Text and icons on AI surfaces |
| inverseOnSurface | #E8EAED | #202124 | Snackbar text |
| error | #B3261E | #F2B8B5 | Destructive actions in dialogs and on 10 |
| errorContainer | #F9DEDC | #8C1D18 | Delete tiles in lists (10, 12, 13) |
| onErrorContainer | #410E0B | #F9DEDC | Icons on errorContainer |
| scrim | #000000 | #000000 | Behind sheets and dialogs, black at 32% |
| surface | #FFFFFF | #202124 | Screen background |
| surfaceContainerLowest | #FFFFFF | #000000 | List items, AI answer cards, tab previews |
| surfaceContainerLow | #F8F9FA | #292A2D | List screens, sheets, tab cards, cards on surface |
| surfaceContainer | #F1F3F4 | #303134 | Floating toolbar (light), menus, unselected buttons |
| surfaceContainerHigh | #E8EAED | #303134 | Address bar, search bar, input pill, dialogs, dark toolbar |
| surfaceContainerHighest | #DADCE0 | #3C4043 | Shortcut tiles, favicon tiles, switch track off |
| onSurface | #202124 | #E8EAED | Main text |
| onSurfaceVariant | #5F6368 | #9AA0A6 | Supporting text and icons |
| outline | #747775 | #8E918F | Switch border and thumb when off, diagram lines |
| outlineVariant | #C4C7C5 | #444746 | Outlined buttons, dividers, card borders |
| disabled content | see note | see note | onSurface at 38%: disabled icons and text |
| disabled container | see note | see note | onSurface at 10% (dark 12%): the disabled AI FAB |

Scrim is black at 32%. Disabled content is onSurface at 38%; disabled containers are onSurface at 10% (12% in dark).
The full fallback schemes, every role included, are in compose/Color.kt and tokens.json.

## Type: Google Sans Flex

Letter spacing 0 for every style. Text follows the system font scale. Serif in 08b is FontFamily.Serif.

| Style | Size, weight | Used for | Sample |
|---|---|---|---|
| displaySmallEmphasized | 36/44, 500 | Welcome headline (01) | Browse less. Understand more. |
| headlineLargeEmphasized | 32/40, 500 | Large titles (03, 04c, 10, 12, 13, 14) | Settings |
| headlineMediumEmphasized | 28/36, 500 | Titles on 08, 09, 09b, 09c and 11 | 7 tabs |
| headlineSmall | 24/32, 400 | Dialog titles (06d, 15) | Let Skim read this page? |
| headlineSmall, 600 | 24/32, 600 | Skim wordmark beside the logo (01) | Skim |
| titleLargeEmphasized | 22/28, 500 | Letter tiles for shortcuts without an icon (04) | W  G  Y  H |
| titleMediumEmphasized | 16/24, 600 | Card titles and section titles (03, 04, 08b) | Summarize my 7 open tabs |
| titleMedium | 16/24, 500 | Sheet titles, Filled M button labels | Why heat pumps are winning in cold climates |
| titleSmallEmphasized | 14/20, 600 | Section labels in primary, AI labels in tertiary | General |
| bodyLarge | 16/24, 400 | List items, inputs, the address | daylightjournal.com |
| bodyMedium | 14/20, 400 | Supporting text, AI answers, dialog text | 1 question about daylightjournal.com |
| bodySmall | 12/16, 400 | Captions, footnotes, shortcut names | Stored only on this phone. |
| labelLarge | 14/20, 500 | Buttons S and XS, menu items | Set as default |
| labelLargeEmphasized | 14/20, 600 | Selected button in a connected group | Open |
| labelMedium | 12/16, 500 | Tab card titles | Heat pump sizing calculator |
| labelMediumEmphasized | 12/16, 600 | Badges, favicon letters, the tab count | 4 tabs |
| Reader body | 17/28, 400 | Reader text (08), scaled 80 to 200 percent in 08b | For decades the advice was simple. |

## Corner scale

Shapes() defaults; the theme does not override them.

| Token | dp | Used for |
|---|---|---|
| none | 0 | Web content, the reader page, full bleed art |
| extraSmall | 4 | Snackbars, inner corners of segmented lists |
| small | 8 | Inner corners of connected buttons |
| medium | 12 | Menu items, page previews, thumbnails |
| large | 16 | FABs and menus |
| largeIncreased | 20 | Cards, tab cards, list outer corners, bubbles |
| extraLarge | 28 | Sheets, dialogs and hero cards |
| full | full | Buttons, toolbar, bars, chips, badges, switches |

extraLargeIncreased (32) and extraExtraLarge (48) exist in the scale but are not used.

## Shape library (MaterialShapes)

Clip with Modifier.clip(MaterialShapes.Sunny.toShape()).

| Shape | Used for |
|---|---|
| Sunny | Skim AI mark: logo, AI tiles and badges |
| Cookie4Sided | Favicons and site tiles |
| Cookie9Sided | AI hero tile, loading indicator frame |
| Cookie12Sided | Incognito hero, error art, a shortcut |
| Clover4Leaf | Welcome art, Setup tile, a shortcut |
| Clover8Leaf | Error art (11) |
| SoftBurst | Error art (11), a shortcut (04) |
| Flower | A shortcut (04) |
| Pentagon | A shortcut (04) |
| Circle | Icon tiles, Send, loading container |

## Motion: MotionScheme.expressive()

Read springs from MaterialTheme.motionScheme. Spatial springs may overshoot, effects springs never do.

| Spec | Damping | Stiffness | Used for |
|---|---|---|---|
| fastSpatialSpec | 0.6 | 800 | Switch thumb, press shape morphs, toggles |
| defaultSpatialSpec | 0.8 | 380 | Sheets, search bar expanding, toolbar and FAB |
| slowSpatialSpec | 0.8 | 200 | Whole screen changes such as 05 to 09 and 08 |
| fastEffectsSpec | 1 | 3800 | Icon fill and color on press or selection |
| defaultEffectsSpec | 1 | 1600 | Scrim, snackbars and menus fading in or out |
| slowEffectsSpec | 1 | 800 | The summary replacing the loading state (06c) |

With Remove animations on, show a still frame of the loading indicator and a flat wave.

## Spacing (dp)

| Value | Used for |
|---|---|
| 2 | Segmented list and connected group gaps |
| 4 | Icon buttons in a row, between text lines |
| 8 | Toolbar to AI FAB, chips, dialog buttons |
| 12 | Tab card grid, stacked cards |
| 16 | Screen side margin, card padding |
| 24 | Dialog padding, toolbar above the bottom |
| 32 | Section labels inset, Welcome layout |
| 40 | Under the onboarding buttons |

## Elevation

Keep the Compose component defaults. Dark adds depth with lighter containers.

| Level | Used for |
|---|---|
| Level 0 | Page, cards, list items |
| Level 1 | Floating toolbar with its FAB, sheets |
| Level 2 | Menus |
| Level 3 | FABs, dialogs, snackbars |

## Icons: Material Symbols Rounded

Weight 400, grade 0, optical size 24. Vector drawables are in ../icons as `ic_<name>.xml`, and `ic_<name>_filled.xml` for the filled ones.
Drawn filled: auto_awesome, folder, format_size when selected, stop. The star fills once a page is bookmarked.

add, arrow_back, arrow_drop_down, arrow_forward, arrow_upward, article, auto_awesome, bookmarks, check, chevron_right, chrome_reader_mode, close, cloud_upload, code, content_copy, cookie, delete, delete_sweep, description, desktop_windows, download, drive_file_move, drive_file_rename_outline, edit, error, find_in_page, folder, format_quote, format_size, history, image, info, keyboard, keyboard_arrow_down, keyboard_arrow_up, layers_clear, link, location_on, lock, mic, more_vert, north_west, palette, public, public_off, refresh, router, schedule, search, settings, share, shield, star, stop, tab, thumb_down, thumb_up, travel_explore, verified_user, visibility_off, wifi, wifi_off, work
