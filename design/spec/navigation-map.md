# Skim navigation map

Every control on every screen and what it opens. Screen numbers match the board titles; (15) is the board with dialogs, menus and snackbars.
Rows marked AI belong to Skim AI and use the tertiary roles with the Sunny shape.

## Main path

01 Welcome, Get started → 03 Setup, Start browsing → 04 Home, open a page → 05 Browsing, AI FAB → 06 Ask about this page.
The first time Skim reads a site, 05 shows 06d first: Allow opens 06, Not now returns to 05.

## Rules for every screen

- Floating toolbar: On 04, 05, 05c, 06d, 08, 09c and 11. Back, Forward, Tabs, More options, then the AI FAB.
- AI FAB (AI): Disabled in incognito (09b, 09c) and on 11 until the page loads.
- Tab switcher: 09 and 09b swap the toolbar for tab actions and add a New tab or New incognito tab FAB.
- Sheets and menus: Close on swipe down, scrim tap or Back. 08b has no scrim: tap the page instead.
- Snackbars: One at a time, 8 above the floating toolbar. With an action 10 s, without one 4 s (15).
- Reload: Pull to refresh on 05 only, no button. Loading shows wavy progress under the address.
- Incognito: Dark scheme, no AI or history, FLAG_SECURE. Own WebView profile, deleted with the last tab.
- Color: Dynamic color on 12+, brand seed otherwise. Skim AI always uses the tertiary roles.
- Groups and lists: Connected button groups on 09, 09b and 08b. Segmented lists in menus, sheets and settings.
- Edge to edge: Pad status and nav bars with WindowInsets. Pages scroll under the floating toolbar.
- Process death: Restore tabs, scroll position, open sheets. Each tab keeps its Skim chat.
- AI requests (AI): Page text over HTTPS, answer streamed back. Never sent from incognito tabs.

## Screens

### 01 Welcome

| Control | Opens or does | AI |
|---|---|---|
| Get started | 03 Setup |  |
| Terms of Service, Privacy Policy | Open in a tab; Back returns here |  |
| System Back | Leaves the app |  |
| Later launches | Skipped; the app opens on the last tab |  |

### 03 Default browser & import

| Control | Opens or does | AI |
|---|---|---|
| Set as default | Android role dialog (ROLE_BROWSER) |  |
| Skim became default | Button turns into a disabled Done |  |
| Choose file | File picker, then a snackbar (15) |  |
| Start browsing | 04 Home; onboarding leaves the back stack |  |
| System Back | 01 Welcome |  |

### 04 Home / New tab

| Control | Opens or does | AI |
|---|---|---|
| Search or ask anything | 04b Search with the keyboard up |  |
| Voice search | Android speech input; text goes to 04b |  |
| Summarize my 7 open tabs | 06b with the prompt already sent | AI |
| Shortcut | Opens the site in this tab (05) |  |
| Long-press a shortcut | Shortcut menu (15) |  |
| Add | Add shortcut dialog (15) |  |
| See all | 04c Skim history |  |
| Continue with Skim card | 05 with its saved chat open (06) |  |
| Back, Forward | Disabled on a new tab |  |
| Tabs, More options | 09, and 05b without the page actions |  |
| Ask Skim | 06b Ask Skim | AI |

### 04b Search & suggestions

| Control | Opens or does | AI |
|---|---|---|
| Close search, system Back | Returns to 04 or 05 |  |
| Clear text | Empties the field; the keyboard stays up |  |
| Typing | Engine suggestions, history, bookmarks |  |
| Ask Skim row | 06b with the text as the question | AI |
| Search suggestion | Results from the default engine (05) |  |
| Edit search arrow | Copies the suggestion into the field |  |
| History item | Opens that page (05) |  |
| Enter | A URL opens; other text is searched (05) |  |
| Opened from the address in 05 | Field starts with the URL selected |  |

### 11 Page failed to load

| Control | Opens or does | AI |
|---|---|---|
| Edit address | 04b with the URL selected |  |
| Try again | Loads the URL again |  |
| Internet settings | Android Internet panel, No internet only |  |
| Connection comes back | No internet state reloads by itself |  |
| Which state shows | Picked from the WebView error code |  |
| Back, Forward, Tabs, More options | Same as 05 |  |
| Ask Skim about this page | Disabled until a page loads | AI |

### 05 Browsing

| Control | Opens or does | AI |
|---|---|---|
| Site information | Site information sheet (15) |  |
| Edit address | 04b with the URL selected |  |
| Reader view | 08 Reader; hidden when there is no article |  |
| Pull down at the top | Reloads the page |  |
| Long-press a link or image | 05d Link or Image options |  |
| Back, Forward | This tab's page history |  |
| Back on the first page | Closes a tab another app opened, returns |  |
| Tabs, More options | 09 Tabs, and 05b More menu |  |
| Ask Skim about this page | 06, or 06d first on a new site | AI |
| Page fails to load | 11 in the same tab |  |
| Download starts | DownloadManager, then a snackbar (15) |  |
| File input, fullscreen video | System file picker, immersive mode |  |
| tel:, mailto:, intent: links | Handed to Android |  |

### 05b More menu

| Control | Opens or does | AI |
|---|---|---|
| Add bookmark | Saves to Mobile bookmarks, snackbar |  |
| Edit bookmark (filled star) | Edit bookmark dialog (15) |  |
| Share page | Android share sheet with title and URL |  |
| Find in page | 05c Find in page |  |
| Desktop site | Toggle; reloads with a desktop user agent |  |
| New tab, New incognito tab | 04 and 09c |  |
| Bookmarks, History | 14 and 13 |  |
| Settings | 12 |  |

### 05c Find in page

| Control | Opens or does | AI |
|---|---|---|
| Typing | Highlights matches, shows the count |  |
| Previous match, Next match | Moves between matches; wraps around |  |
| Close find in page, system Back | Clears highlights, back to 05 |  |
| Back, Forward, Tabs, More options | Same as 05 |  |
| Ask Skim about this page | Same as 05 | AI |

### 05d Link & image options

| Control | Opens or does | AI |
|---|---|---|
| Open in new tab | Opens behind, snackbar with Switch |  |
| Open in incognito tab | Dark incognito tab with the link loaded |  |
| Copy link | Clipboard; snackbar on Android 12 or lower |  |
| Share link | Android share sheet |  |
| Long-press an image | Image options: the Image variant of 05d |  |
| Open image in new tab | Opens behind, snackbar with Switch |  |
| Download image | DownloadManager, then a snackbar (15) |  |
| Share image | Android share sheet with the image |  |
| Swipe down, tap outside | Closes |  |

### 08 Reader view

| Control | Opens or does | AI |
|---|---|---|
| Exit reader view, system Back | 05 at the same scroll position |  |
| Text size | 08b Reader text settings |  |
| Back, Forward, Tabs | Same as 05 |  |
| More options | 05b More menu |  |
| Ask Skim about this page | 06, reading the article text | AI |
| Offered when | The page has an article (Readability) |  |

### 08b Reader text settings

| Control | Opens or does | AI |
|---|---|---|
| Size slider | 80 to 200 percent in steps of 10, live |  |
| Font: Sans serif, Serif | Connected button group, one selected |  |
| Saved | For every reader page until changed |  |
| Text size again, tap the page | Closes the sheet |  |
| Exit reader view | Same as 08; the sheet closes too |  |

### 06 Ask about this page

| Control | Opens or does | AI |
|---|---|---|
| Opens as | Half-height sheet; drag up for full height |  |
| Summary | Starts on open; 06c while it streams |  |
| Copy summary, Good summary, Poor summary | Clipboard, and a feedback toggle |  |
| From paragraph 6 | Sheet lowers; page scrolls and highlights |  |
| Key numbers, Explain simply, The catch? | Sent as a question |  |
| Ask about this page, Send | The answer streams in; no voice input |  |
| Close, swipe down | Closes; the chat stays with the tab |  |
| Errors | 06c Not enough text or No connection |  |

### 06b Ask Skim from Home & Tabs

| Control | Opens or does | AI |
|---|---|---|
| Reads | All open tabs, never incognito ones |  |
| Heat pump research, Osaka trip, Other | 09 Tabs scrolled to that group |  |
| Copy answer, Good answer, Poor answer | Same as 06 |  |
| Suggested question, Ask anything, Send | Sent as a question |  |
| Close, swipe down | Closes; saved to Skim history |  |

### 06c AI generating & errors

| Control | Opens or does | AI |
|---|---|---|
| Generating | Contained loading indicator, wavy progress |  |
| Stop | Ends the stream, keeps the text so far |  |
| Not enough text | Questions still work in the input |  |
| Try again (No connection) | Retries; input stays off until online |  |
| Close, Ask about this page, Send | Same as 06 |  |

### 06d AI permission, first time per site

| Control | Opens or does | AI |
|---|---|---|
| Opens as | Centered dialog over 05, scrim behind |  |
| Allow | Saves the site, opens 06, starts the summary |  |
| Not now, system Back, scrim tap | Closes; nothing is sent; asks next time |  |
| Floating toolbar | Under the scrim, inactive while it shows |  |
| Shown when | Ask before sending is on (10), site is new |  |
| Change later | Site information › Skim AI switch (15) |  |

### 09 Tabs

| Control | Opens or does | AI |
|---|---|---|
| Search tabs | Filters by title or URL in place |  |
| Open, Incognito | Connected group: 09 and 09b |  |
| Undo in the banner | Restores the order before grouping |  |
| Group options | Tab group menu (15) |  |
| Tab card | Opens it (05) |  |
| Close tab, or swipe a card | Closes it, snackbar with Undo |  |
| Long-press and drag | Reorders tabs inside a group |  |
| Ask Skim about these tabs | 06b Ask Skim | AI |
| New incognito tab | 09c in a new incognito tab |  |
| Close all tabs | Closes every tab, snackbar with Undo |  |
| New tab | 04 Home in a new tab |  |

### 09b Incognito tabs

| Control | Opens or does | AI |
|---|---|---|
| Search incognito tabs | Filters incognito tabs in place |  |
| Open, Incognito | 09 Tabs, and this screen |  |
| Tab card | Opens the incognito tab, dark |  |
| Close tab | Closes it, snackbar with Undo |  |
| Ask Skim, off in incognito | Disabled | AI |
| Close all incognito tabs | Closes every incognito tab, then 09 |  |
| New incognito tab | 09c |  |

### 09c Incognito new tab

| Control | Opens or does | AI |
|---|---|---|
| Search or type URL | 04b in dark, no history or Ask Skim row |  |
| Voice search | Speech input; text goes to the dark 04b |  |
| Back, Forward | Disabled on a new tab |  |
| Incognito tabs, More options | 09b, and 05b in dark |  |
| Ask Skim, off in incognito | Disabled | AI |
| Storage | Own WebView profile (androidx.webkit) |  |
| Last incognito tab closes | Profile deleted: cookies, cache, history |  |

### 12 Settings

| Control | Opens or does | AI |
|---|---|---|
| Back | Wherever Settings was opened from |  |
| Search engine, Theme | Choice dialogs (15) |  |
| Default browser | Role dialog, or Android default apps |  |
| AI & privacy | 10 AI & privacy settings | AI |
| Clear browsing data | Dialog (15) |  |
| Privacy policy, Terms of service | Open in a new tab (05) |  |
| Open-source licenses | Generated list (AboutLibraries) |  |
| Version | Not tappable |  |

### 10 AI & privacy settings

| Control | Opens or does | AI |
|---|---|---|
| Back | 12 Settings |  |
| Ask before sending a page to the cloud | On: 06d once per new site. Off: no prompt |  |
| Keep chat history on this device | Off: new chats aren't saved |  |
| Skim history | 04c Skim history |  |
| Delete all AI data on this device | Dialog (15) |  |
| How Skim AI handles your data | Opens the policy in a new tab (05) |  |

### 04c Skim history

| Control | Opens or does | AI |
|---|---|---|
| Back | 04 Home or 10, wherever it opened from |  |
| Search Skim history | Field replaces the title, filters in place |  |
| Delete all Skim history | Clear Skim history dialog (15) |  |
| Page item | 05 with that chat reopened (06) |  |
| Tabs item | 06b with the saved answer | AI |
| Remove from Skim history | Removes it, snackbar with Undo |  |
| Empty list | Text: Summaries you ask for show up here |  |

### 13 History

| Control | Opens or does | AI |
|---|---|---|
| Back | Wherever History was opened from |  |
| Search history | Filters in place |  |
| Clear browsing data | Dialog (15) |  |
| History item | Opens in the current tab (05) |  |
| Remove from history | Removes it, snackbar with Undo |  |
| Never listed | Pages visited in incognito |  |

### 14 Bookmarks

| Control | Opens or does | AI |
|---|---|---|
| Back | Wherever Bookmarks was opened from |  |
| Search bookmarks | Searches every folder |  |
| Mobile bookmarks, Imported | Same layout with the folder name as title |  |
| Bookmark | Opens in the current tab (05) |  |
| Bookmark options | Bookmark menu (15) |  |
| Imported folder | Keeps the file's subfolders |  |

### Android system UI, not drawn

| Control | Opens or does | AI |
|---|---|---|
| Default browser | RoleManager ROLE_BROWSER, Android 10+ |  |
| Voice input | RecognizerIntent.ACTION_RECOGNIZE_SPEECH |  |
| Bookmarks file | ACTION_OPEN_DOCUMENT, type text/html |  |
| Share | ACTION_SEND chooser with title and URL |  |
| Internet panel | Settings.Panel: INTERNET_CONNECTIVITY |  |
| Downloads | DownloadManager with its notification |  |
| Site permissions | Skim prompt (15), then runtime permission |  |
| Links from other apps | VIEW intent filter for http and https |  |
| Clipboard | System confirmation on Android 13+ |  |
