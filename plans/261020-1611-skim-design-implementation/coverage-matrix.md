# Coverage matrix: board, row, overlay → phase

Nguồn: `design/README.md` (bảng Boards), `design/spec/navigation-map.md` (mọi row), `design/spec/dialogs.md` (mọi overlay). "Owner" là phase làm cho mục đó chạy end-to-end và kiểm chứng nó; control có thể được vẽ hoặc nối sớm hơn qua `SkimNavigator` (quy ước tích hợp trong `plan.md`). Mỗi mục có đúng một owner.

## A. Board và state

| Board / state | PNG | Owner |
|---|---|---|
| 01 Welcome | `01-welcome.png` | 5 |
| 03 Default browser & import | `03-default-browser-import.png` | 5 |
| 04 Home / New tab | `04-home-new-tab.png` | 6 |
| 04b Search & suggestions | `04b-search-suggestions.png` | 6 |
| 04c Skim history | `04c-skim-history.png` | 12 |
| 05 Browsing: Loaded | `05-browsing.png` | 7 |
| 05 Browsing: Loading | `05-browsing--loading.png` | 7 |
| 05b More menu | `05b-more-menu.png` | 7 |
| 05c Find in page | `05c-find-in-page.png` | 7 |
| 05d Link & image options: Link | `05d-link-image-options.png` | 7 |
| 05d Link & image options: Image | `05d-link-image-options--image.png` | 7 |
| 06 Ask about this page | `06-ask-about-this-page.png` | 11 |
| 06b Ask Skim from Home & Tabs | `06b-ask-skim-from-home-tabs.png` | 12 |
| 06c Generating | `06c-ai-generating-errors.png` | 11 |
| 06c Not enough text | `06c-ai-generating-errors--not-enough-text.png` | 11 |
| 06c No connection | `06c-ai-generating-errors--no-connection.png` | 11 |
| 06d AI permission, first time per site | `06d-ai-permission-first-time-per-site.png` | 11 |
| 08 Reader view | `08-reader-view.png` | 10 |
| 08b Reader text settings | `08b-reader-text-settings.png` | 10 |
| 09 Tabs | `09-tabs.png` | 9 |
| 09b Incognito tabs | `09b-incognito-tabs.png` | 9 |
| 09c Incognito new tab | `09c-incognito-new-tab.png` | 6 |
| 10 AI & privacy settings | `10-ai-privacy-settings.png` | 8 |
| 11 Unreachable | `11-page-failed-to-load.png` | 7 |
| 11 No internet | `11-page-failed-to-load--no-internet.png` | 7 |
| 11 Timed out | `11-page-failed-to-load--timed-out.png` | 7 |
| 12 Settings | `12-settings.png` | 8 |
| 13 History | `13-history.png` | 8 |
| 14 Bookmarks | `14-bookmarks.png` | 8 |
| 15 Dialogs, menus & snackbars | `15-dialogs-menus-snackbars.png` | từng overlay ở mục E |
| 16 Navigation map, 17 Tokens, 18 Components & setup | `16-…`, `17-…`, `18-…` | Chỉ là tài liệu; phase 13 dẫn link trong `docs/design-guidelines.md` |

<!-- Red Team: Cổng kiểm chứng — parity theo phase — 2026-10-21 -->
Ảnh golden cấp board của mỗi dòng trên được chụp và duyệt so với PNG ngay trong phase owner (lệch ghi ở `docs/design-guidelines.md`); phase 13 chỉ chạy `designParity` để duyệt tổng (không đổi owner).

## B. Main path và Rules for every screen

| Mục (navigation-map.md) | Owner |
|---|---|
| Main path 01 → 03 → 04 → 05 → 06 | 11 (đoạn cuối; các bước trước thuộc owner từng row) |
| First time on a site: 05 shows 06d first; Allow opens 06, Not now returns to 05 | 11 |
<!-- Red Team: Owner sai cho rule nhiều màn — 2026-10-21 -->
| Floating toolbar: component (64 cao, FAB cách 8, đáy cách nav bar 24) | 2 |
| Floating toolbar trên 04 và 09c | 6 |
| Floating toolbar trên 05, 05c và 11 | 7 |
| Floating toolbar trên 08 | 10 |
| Floating toolbar trên 06d (dưới scrim, không tương tác; cùng mục với C: 06d "Floating toolbar") | 11 |
| AI FAB disabled in incognito (09b, 09c) and on 11 until the page loads (`AiFabMode`) | 5 |
| Tab switcher (09/09b swap toolbar, New tab / New incognito tab FAB) | 9 |
| Sheets and menus close on swipe down, scrim tap, Back (scene strategy, menu popup) | 5 |
| 08b has no scrim: tap the page instead | 10 |
| Snackbars one at a time, 8 above toolbar, 10 s with action / 4 s without (host + placement theo màn, controller của phase 2) | 5 |
| Reload: pull to refresh on 05 only, wavy progress under the address | 7 |
| Incognito: dark scheme + FLAG_SECURE | 5 |
| Incognito: no history | 4 |
| Incognito: no AI | 11 |
| Incognito: own WebView profile, deleted with the last tab | 4 |
| Color: dynamic color on 12+, brand seed otherwise; AI uses tertiary | 1 |
| Groups and lists: connected groups (09, 09b, 08b), segmented lists | 2 |
| Edge to edge: pad status and nav bars with WindowInsets | 1 |
| Edge to edge: pages scroll under the floating toolbar | 7 |
| Process death: restore tabs | 4 |
| Process death: restore scroll position | 4 |
| Process death: restore open sheets | 5 |
| Process death: each tab keeps its Skim chat | 11 |
| AI requests: page text over HTTPS, answer streamed, never from incognito | 11 |

## C. Row của từng màn

| Màn | Control (nguyên văn) | Owner |
|---|---|---|
| 01 | Get started | 5 |
| 01 | Terms of Service, Privacy Policy | 5 |
| 01 | System Back | 5 |
| 01 | Later launches | 5 |
| 03 | Set as default | 5 |
| 03 | Skim became default | 5 |
| 03 | Choose file | 5 |
| 03 | Start browsing | 5 |
| 03 | System Back | 5 |
| 04 | Search or ask anything | 6 |
| 04 | Voice search | 6 |
| 04 | Summarize my 7 open tabs | 12 |
| 04 | Shortcut | 6 |
| 04 | Long-press a shortcut | 6 |
| 04 | Add | 6 |
| 04 | See all | 12 |
| 04 | Continue with Skim card | 11 |
| 04 | Back, Forward | 6 |
| 04 | Tabs, More options | 6 |
| 04 | Ask Skim | 12 |
| 04b | Close search, system Back | 6 |
| 04b | Clear text | 6 |
| 04b | Typing | 6 |
| 04b | Ask Skim row | 12 |
| 04b | Search suggestion | 6 |
| 04b | Edit search arrow | 6 |
| 04b | History item | 6 |
| 04b | Enter | 6 |
| 04b | Opened from the address in 05 | 6 |
| 11 | Edit address | 7 |
| 11 | Try again | 7 |
| 11 | Internet settings | 7 |
| 11 | Connection comes back | 7 |
| 11 | Which state shows | 7 |
| 11 | Back, Forward, Tabs, More options | 7 |
| 11 | Ask Skim about this page (disabled until a page loads) | 7 |
| 05 | Site information | 7 |
| 05 | Edit address | 7 |
| 05 | Reader view | 10 |
| 05 | Pull down at the top | 7 |
| 05 | Long-press a link or image | 7 |
| 05 | Back, Forward | 7 |
| 05 | Back on the first page | 5 |
| 05 | Tabs, More options | 7 |
| 05 | Ask Skim about this page | 11 |
| 05 | Page fails to load | 7 |
| 05 | Download starts | 7 |
| 05 | File input, fullscreen video | 4 |
| 05 | tel:, mailto:, intent: links | 4 |
| 05b | Add bookmark | 7 |
| 05b | Edit bookmark (filled star) | 7 |
| 05b | Share page | 7 |
| 05b | Find in page | 7 |
| 05b | Desktop site | 7 |
| 05b | New tab, New incognito tab | 7 |
| 05b | Bookmarks, History | 7 |
| 05b | Settings | 7 |
| 05c | Typing | 7 |
| 05c | Previous match, Next match | 7 |
| 05c | Close find in page, system Back | 7 |
| 05c | Back, Forward, Tabs, More options | 7 |
| 05c | Ask Skim about this page | 11 |
| 05d | Open in new tab | 7 |
| 05d | Open in incognito tab | 7 |
| 05d | Copy link | 7 |
| 05d | Share link | 7 |
| 05d | Long-press an image | 7 |
| 05d | Open image in new tab | 7 |
| 05d | Download image | 7 |
| 05d | Share image | 7 |
| 05d | Swipe down, tap outside | 7 |
| 08 | Exit reader view, system Back | 10 |
| 08 | Text size | 10 |
| 08 | Back, Forward, Tabs | 10 |
| 08 | More options | 10 |
| 08 | Ask Skim about this page | 11 |
| 08 | Offered when | 10 |
| 08b | Size slider | 10 |
| 08b | Font: Sans serif, Serif | 10 |
| 08b | Saved | 10 |
| 08b | Text size again, tap the page | 10 |
| 08b | Exit reader view | 10 |
| 06 | Opens as | 11 |
| 06 | Summary | 11 |
| 06 | Copy summary, Good summary, Poor summary | 11 |
| 06 | From paragraph 6 | 11 |
| 06 | Key numbers, Explain simply, The catch? | 11 |
| 06 | Ask about this page, Send | 11 |
| 06 | Close, swipe down | 11 |
| 06 | Errors | 11 |
| 06b | Reads | 12 |
| 06b | Heat pump research, Osaka trip, Other | 12 |
| 06b | Copy answer, Good answer, Poor answer | 12 |
| 06b | Suggested question, Ask anything, Send | 12 |
| 06b | Close, swipe down | 12 |
| 06c | Generating | 11 |
| 06c | Stop | 11 |
| 06c | Not enough text | 11 |
| 06c | Try again (No connection) | 11 |
| 06c | Close, Ask about this page, Send | 11 |
| 06d | Opens as | 11 |
| 06d | Allow | 11 |
| 06d | Not now, system Back, scrim tap | 11 |
| 06d | Floating toolbar | 11 |
| 06d | Shown when | 11 |
| 06d | Change later | 11 |
| 09 | Search tabs | 9 |
| 09 | Open, Incognito | 9 |
| 09 | Undo in the banner | 12 |
| 09 | Group options | 9 |
| 09 | Tab card | 9 |
| 09 | Close tab, or swipe a card | 9 |
| 09 | Long-press and drag | 9 |
| 09 | Ask Skim about these tabs | 12 |
| 09 | New incognito tab | 9 |
| 09 | Close all tabs | 9 |
| 09 | New tab | 9 |
| 09b | Search incognito tabs | 9 |
| 09b | Open, Incognito | 9 |
| 09b | Tab card | 9 |
| 09b | Close tab | 9 |
| 09b | Ask Skim, off in incognito | 9 |
| 09b | Close all incognito tabs | 9 |
| 09b | New incognito tab | 9 |
| 09c | Search or type URL | 6 |
| 09c | Voice search | 6 |
| 09c | Back, Forward | 6 |
| 09c | Incognito tabs, More options | 6 |
| 09c | Ask Skim, off in incognito | 6 |
| 09c | Storage | 4 |
| 09c | Last incognito tab closes | 4 |
| 12 | Back | 8 |
| 12 | Search engine, Theme | 8 |
| 12 | Default browser | 8 |
| 12 | AI & privacy | 8 |
| 12 | Clear browsing data | 8 |
| 12 | Privacy policy, Terms of service | 8 |
| 12 | Open-source licenses | 8 |
| 12 | Version | 8 |
| 10 | Back | 8 |
| 10 | Ask before sending a page to the cloud | 8 |
| 10 | Keep chat history on this device | 8 |
| 10 | Skim history | 8 |
| 10 | Delete all AI data on this device | 8 |
| 10 | How Skim AI handles your data | 8 |
| 04c | Back | 12 |
| 04c | Search Skim history | 12 |
| 04c | Delete all Skim history | 12 |
| 04c | Page item | 12 |
| 04c | Tabs item | 12 |
| 04c | Remove from Skim history | 12 |
| 04c | Empty list | 12 |
| 13 | Back | 8 |
| 13 | Search history | 8 |
| 13 | Clear browsing data | 8 |
| 13 | History item | 8 |
| 13 | Remove from history | 8 |
| 13 | Never listed | 8 |
| 14 | Back | 8 |
| 14 | Search bookmarks | 8 |
| 14 | Mobile bookmarks, Imported | 8 |
| 14 | Bookmark | 8 |
| 14 | Bookmark options | 8 |
| 14 | Imported folder | 8 |

## D. Android system UI, not drawn

| Control | Owner |
|---|---|
| Default browser (RoleManager ROLE_BROWSER, Android 10+) | 5 |
| Voice input (RecognizerIntent.ACTION_RECOGNIZE_SPEECH) | 6 |
| Bookmarks file (ACTION_OPEN_DOCUMENT, text/html) | 5 |
| Share (ACTION_SEND chooser with title and URL) | 7 |
| Internet panel (Settings.Panel INTERNET_CONNECTIVITY) | 7 |
| Downloads (DownloadManager with its notification) | 4 |
| Site permissions (Skim prompt, then runtime permission) | 7 |
| Links from other apps (VIEW intent filter for http and https) | 5 |
| Clipboard (system confirmation on Android 13+) | 7 |

## E. Overlay (dialogs.md và board 15)

| Overlay | Loại | Owner |
|---|---|---|
| Add shortcut (và Edit shortcut, cùng dialog) | Dialog | 6 |
| Search engine | Dialog | 8 |
| Theme | Dialog | 8 |
| Clear browsing data | Dialog | 8 |
| Delete all AI data | Dialog | 8 |
| Clear Skim history | Dialog | 12 |
| Site permission | Dialog | 7 |
| Rename group | Dialog | 9 |
| Edit bookmark | Dialog | 7 |
| Move to folder | Dialog | 7 |
| Shortcut menu | Menu | 6 |
| Tab group menu | Menu | 9 |
| Bookmark menu | Menu | 8 |
| Site information sheet | Sheet | 7 |
| Snackbar: Tab closed (Undo) | Snackbar | 9 |
| Snackbar: N tabs closed (Undo) | Snackbar | 9 |
| Snackbar: Bookmark added (Edit) | Snackbar | 7 |
| Snackbar: Removed from history (Undo) trên 13 | Snackbar | 8 |
| Snackbar: Removed from history (Undo) trên 04c | Snackbar | 12 |
| Snackbar: Bookmark deleted (Undo) | Snackbar | 8 |
| Snackbar: Shortcut removed (Undo) | Snackbar | 6 |
| Snackbar: Opened in a new tab (Switch) | Snackbar | 7 |
| Snackbar: Imported N bookmarks (View) | Snackbar | 5 |
| Snackbar: Couldn't read that file… (lỗi import, 4 s, alert) | Snackbar | 5 |
| Snackbar: %s downloaded (Open) | Snackbar | 7 |
| Snackbar: Copied to clipboard (Android ≤ 12) từ 05d Copy link | Snackbar | 7 |
| Snackbar: Copied to clipboard (Android ≤ 12) từ 06 Copy summary (host cục bộ trong sheet) | Snackbar | 11 |
| Snackbar: Copied to clipboard (Android ≤ 12) từ 06b Copy answer (host cục bộ trong sheet) | Snackbar | 12 |
| Sheet 05b More menu | Sheet | 7 |
| Sheet 05d Link & image options | Sheet | 7 |
| Sheet 06 Ask about this page / 06c | Sheet | 11 |
| Sheet 06b Ask Skim | Sheet | 12 |
| Panel 08b Reader text settings | Panel không scrim | 10 |
| Dialog 06d AI permission | Dialog | 11 |
| Banner 09 "Skim grouped your tabs by topic" (Undo) | Banner | 12 |

## F. Thành phần không có row riêng nhưng thiết kế yêu cầu

| Mục | Owner |
|---|---|
| Open-source licenses (AboutLibraries, gồm Google Sans Flex OFL, Material Symbols, Readability.js) | 8 (định nghĩa thư viện ở 1) |
| Sửa lỗi weight của Type.kt + test | 1 |
| 19 mâu thuẫn spec ↔ HTML (scout §3(e)) | 2 |
| Content description của scout §3(b) | 1 |
| Reduced motion: ảnh tĩnh loading indicator, sóng phẳng | 2 |
<!-- Red Team: Popup không có board — 2026-10-21 -->
| Popup `window.open`/`target=_blank` (không có board; quyết định nhỏ của planner, giữ vì OAuth/thanh toán dạng popup) | 4 |
| Trạng thái renderer crash vượt ngân sách (kiểu 11, copy D8) | 7 |
| Undo chạy qua `UndoRegistry` (snackbar action dạng dữ liệu) | 2 |
