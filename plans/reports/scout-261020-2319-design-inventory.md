# Scout: kiểm kê design handoff Skim (board → Compose + strings.xml)

- Ngày: 2026-10-20 (Asia/Singapore). Phạm vi: chỉ đọc `design/`. File duy nhất được ghi: báo cáo này.
- Nguồn đã đọc: `design/README.md`, `design/spec/{navigation-map,components,dialogs,tokens}.md`, `design/spec/tokens.json`, `design/compose/Type.kt`, toàn bộ 33 file `design/html/*.html` (30 board điện thoại + 15/16/17/18). Không cần mở PNG hay `source/*.dc.html`: HTML đủ để giải mọi điểm mơ hồ.
- Lưu ý: task ghi 36 file, thực tế `design/html/` có 33 file (30 board/state + 15 + 16 navigation map + 17 tokens + 18 components). Không thiếu board nào trong danh sách yêu cầu.

## 0. Quy ước đọc báo cáo

| Ký hiệu | Nghĩa |
|---|---|
| `[S]` | SAMPLE DATA: dữ liệu mẫu (tiêu đề tab, shortcut, lịch sử, domain, nội dung web trong 05–06d). Không đưa vào strings.xml. |
| `[AI]` | Nội dung do Skim AI sinh ra (tóm tắt, câu trả lời, tên nhóm tab, câu hỏi gợi ý của 06b). Không phải string. |
| `[SPEC]` | Copy chỉ xuất hiện trong spec/*.md, chưa được vẽ. Vẫn đề xuất tên string, cần xác nhận. |
| `[?]` | Vai trò mơ hồ giữa chrome và dữ liệu, có giải thích. |
| `P:` | Plural. Ghi `other` đúng như bản vẽ; `one` ghi rõ là "vẽ" hoặc "suy ra" (suy ra = cần copywriter xác nhận, không phải copy có trong design). |
| `NT` | `translatable="false"` (tên thương hiệu, mã lỗi, glyph). |
| `cd_` | Tiền tố cho content description (lấy nguyên văn aria-label). |
| Kích thước | dp, lấy từ CSS px của board 412×915 (1 px = 1 dp). Hàng 24 dp trên cùng = status bar → `WindowInsets`. |

Ánh xạ `clip-path: polygon` trong HTML → `MaterialShapes` (xác nhận bằng nhãn trên board 17, đếm số thùy bằng script): Sunny (8 thùy), Cookie4Sided, Cookie9Sided, Cookie12Sided, Clover4Leaf, Clover8Leaf, SoftBurst (10 thùy), Flower, Pentagon. Màu hex được map về role bằng `tokens.json` (light; `dk:` = dark).

Escape khi đưa vào strings.xml: dấu nháy đơn (`You're`, `can't`, `Skim's`…) cần `\'`; `&` → `&amp;`; `%` đứng một mình → `%%`; giữ nguyên ký tự `…` (U+2026) và `›` (U+203A).

## 1. Chrome dùng chung (tham chiếu từ các board bên dưới)

| Thành phần (components.md) | Số đo trong HTML | Ghi chú lệch so với components.md |
|---|---|---|
| Floating toolbar (`role=toolbar`, cd "Browser controls") | Đáy cách 24; toolbar 64 cao, radius 32, `surfaceContainer` (dark: `surfaceContainerHigh`), padding 0×8, gap 4; 4 IconButton 48; FAB cách 8 | Shadow vẽ là mức 3 (`0 1 3 / 0 4 8 3`), tokens.md nói Level 1 → giữ default Compose. |
| Tabs button | Ô 22×22, viền 2 `currentColor`, radius 7, số 12/12 **700** | Spec nói labelMediumEmphasized (600). |
| AI FAB | 56, radius 16, `tertiaryContainer`/`onTertiaryContainer`, `ic_auto_awesome_filled` 24 | Trạng thái tắt (09c, 11): nền `onSurface` 10% (dark 12%), nội dung 38%, **không shadow, icon `ic_auto_awesome` KHÔNG filled**. |
| Address bar | Khung ngoài padding 8×16 trên `surface`; bar 56 radius 28 `surfaceContainerHigh`, padding 0×4, gap 4; Site info IconButton 48 (icon `ic_lock` 20); địa chỉ là TextButton bodyLarge `onSurface` ellipsis; Reader view IconButton 48 (icon 24) | Trên 11: icon `ic_error` là ảnh tĩnh (không phải nút), không có Reader view, padding 0 16 0 4. Dưới scrim (05b, 05d, 06–06d) vẽ bằng span tĩnh. |
| Bottom sheet | Top radius 28, `surfaceContainerLow`, handle 32×4 radius 2 màu `onSurfaceVariant` 40% trong hàng 36, scrim đen 32% | 06 cao 736, 06b 760, 06c 600; 08b không scrim, shadow mạnh hơn. |
| Large title bar | Hàng 64 padding 0×4, Back 48 icon `onSurface`; tiêu đề 32/40 500 padding 0 16 12 | Khớp spec. |
| Section label | 14/20 600 `primary`, padding 12 (đầu) hoặc 20 (sau) × 32 × 8 | Khớp. |
| Segmented list | margin 0×16, gap 2, item `surfaceContainerLowest`, radius 20/4; 1 dòng ≥56 (pad 8×16), 2 dòng ≥72 (pad 12×16); tile 40; trailing IconButton 48 với padding-right 4 | 09c dùng `dk:surfaceContainer` trên `dk:surface`, cao 48 (xem mục 5e). |
| Dialog | 344 rộng, radius 28, `surfaceContainerHigh`, padding 24; tiêu đề 24/32 400; body 14/20 `onSurfaceVariant`; nút text 40 cao, padding 0×12, gap 8, căn phải, cách body 24 | Icon (nếu có) 24 phía trên, tiêu đề căn giữa. |
| Menu | Nhóm `surfaceContainer` radius 16 padding 4, cách nhau 2, rộng 220; item 48 radius 12, gap 12, labelLarge, icon 24 `onSurfaceVariant` | Item phá hủy có nhóm riêng nhưng chữ vẫn `onSurface` (không đỏ). |
| Snackbar | `inverseSurface`, radius 4, ≥48, padding 0 8 0 16; text bodyMedium `inverseOnSurface`; action 36 cao, padding 0×8, `inversePrimary` labelLarge | Snackbar lỗi nhiều dòng: min 68, padding 14×16, `role=alert`. |

Strings và cd của toolbar dùng chung (khai báo một lần):

| Text | Tên đề xuất | Loại |
|---|---|---|
| Browser controls | `cd_browser_controls` | cd (pane/semantics nhóm toolbar) |
| Back | `cd_back` | cd |
| Forward | `cd_forward` | cd |
| Tabs, 7 open | `cd_tabs_open` | cd, P: other "Tabs, %d open"; one suy ra "Tabs, %d open" |
| Incognito tabs, 2 open | `cd_incognito_tabs_open` | cd, P: other "Incognito tabs, %d open" |
| More options | `cd_more_options` | cd (cũng là pane title của 05b) |
| Ask Skim | `ask_skim` | cd FAB trên 04 (dùng chung với tiêu đề 06b, nhãn 04b) |
| Ask Skim about this page | `cd_ask_skim_about_page` | cd FAB trên 05, 05c, 06d, 08 |
| Ask Skim about this page, unavailable until the page loads | `cd_ask_skim_unavailable` | cd FAB tắt trên 11 |
| Ask Skim, off in incognito | `cd_ask_skim_off_incognito` | cd FAB tắt 09c, nút AI tắt 09b |

## 2. Từng board

### 01 Welcome

1. Layout: spacer 24 → khối giữa (căn giữa, gap 32, padding 16 24 0): logo row [Sunny 36 `primary` + `ic_auto_awesome_filled` 20 `onPrimary`] + wordmark "Skim" 24/32 600 → art 300×240 (Cookie9Sided 104 `primaryContainer` + `ic_article` 40; Clover4Leaf 92 `secondaryContainer` + `ic_travel_explore` 36; Sunny 148 `tertiaryContainer` + `ic_auto_awesome_filled` 60) → headline displaySmallEmphasized căn giữa + body bodyLarge `onSurfaceVariant` (max 340) → khối đáy padding 24 24 40, gap 16: Button M filled 56 full width + dòng pháp lý bodySmall căn giữa, 2 link `primary` 500.
2. Strings:

| Text | Tên |
|---|---|
| Skim | `app_name` |
| Browse less. Understand more. | `welcome_headline` |
| Skim reads pages with you. Summaries and answers, built into the browser. | `welcome_body` |
| Get started | `welcome_get_started` |
| By continuing you agree to the %1$s and %2$s. | `welcome_legal` (AnnotatedString, 2 link) |
| Terms of Service | `welcome_terms_of_service` |
| Privacy Policy | `welcome_privacy_policy` |

3. A11y: không có aria-label; art và logo `aria-hidden` → `contentDescription = null`. Hai link cần `LinkAnnotation` có role link.
4. Icons: `ic_article`, `ic_travel_explore`, `ic_auto_awesome_filled`.
5. Lệch: logo Sunny dùng `primary`/`onPrimary` (không phải tertiary) – logo không phải "AI surface". Art dùng Cookie9Sided cho tile article (tokens.md chỉ ghi Clover4Leaf cho Welcome art).
6. Dữ liệu: không (chỉ cần URL ToS/Privacy).
7. State: một trạng thái; chỉ hiện ở lần chạy đầu (`onboardingCompleted`).

### 03 Default browser & import

1. Layout: spacer 24 → nội dung padding 48 16 0, gap 24: khối tiêu đề (padding 0 8, gap 8: h1 headlineLargeEmphasized, body bodyMedium `onSurfaceVariant`) → 2 card (gap 12) radius **28**, `surfaceContainerLow`, padding 20, gap 16: [tile 48 + cột title titleMediumEmphasized / body bodyMedium] + nút tonal S 40 căn phải → footnote (padding 0 8, gap 12): `ic_verified_user` 20 + bodySmall → đáy padding 16 24 40: Button M filled "Start browsing".
2. Strings:

| Text | Tên |
|---|---|
| Almost there | `setup_title` |
| Two optional steps that make Skim feel like home. You can change the default browser later in Settings. | `setup_body` |
| Make Skim your default browser | `setup_default_title` |
| Links from other apps open here, with summaries and answers one tap away. | `setup_default_body` |
| Set as default | `setup_set_as_default` |
| Done | `action_done` `[SPEC]` (nút tắt sau khi Skim thành default) |
| Bring your bookmarks | `setup_import_title` |
| Choose an HTML bookmarks file exported from another browser. | `setup_import_body` |
| Choose file | `setup_choose_file` |
| Skim reads a page only when you ask, and never in incognito tabs. Page text is sent encrypted and deleted after each answer. | `setup_privacy_note` |
| Start browsing | `setup_start_browsing` |

3. A11y: không aria-label; tile và icon footnote decorative.
4. Icons: `ic_public` (tile Cookie4Sided `primaryContainer`), `ic_bookmarks` (tile Clover4Leaf `secondaryContainer`), `ic_verified_user`.
5. Lệch: card radius 28 (components.md nói card thường 20, hero 28).
6. Dữ liệu: `isDefaultBrowser` (RoleManager), kết quả import (số bookmark / lỗi) → snackbar 15.
7. State: Set as default ↔ Done (disabled) `[SPEC]`, chưa vẽ.

### 04 Home / New tab

1. Layout: spacer 24 → nội dung padding 24 16 0, gap 24: [Sunny 64 `tertiaryContainer` + `ic_auto_awesome_filled` 32] → Search bar 56 radius 28 `surfaceContainerHigh` padding 0 4 0 16 (`ic_search` `onSurfaceVariant`, input bodyLarge placeholder, mic IconButton 48) → AI hero card (button) min 88, radius 28, `tertiaryContainer`, padding 16 20 16 16, gap 16: tile 48 Cookie9Sided **nền `onTertiaryContainer`, icon `tertiaryContainer`** + cột (titleMediumEmphasized / bodyMedium) + `ic_arrow_forward` → section "Shortcuts" (titleMediumEmphasized, gap 12): lưới 4 cột gap 16×8, mỗi ô: tile 64 `surfaceContainerHighest` chữ cái 22/28 500 `onSecondaryContainer` + nhãn bodySmall; ô cuối "Add": vòng 64 viền đứt 2 `outlineVariant` + `ic_add` → section "Continue with Skim" (gap 8): header [title + TextButton "See all" 40 `primary`] + danh sách 2 card dạng segmented (radius 20/4, `surfaceContainerLow`, padding 16, gap 8): hàng meta [favicon 24 Cookie4Sided chữ 12/16 600 + domain bodySmall ellipsis + thời gian bodySmall] / tiêu đề titleMedium / snippet bodyMedium 2 dòng → Floating toolbar (Back, Forward disabled) + AI FAB.
2. Strings:

| Text | Tên | Ghi chú |
|---|---|---|
| Search or ask anything | `search_hint` | placeholder + label |
| Summarize my 7 open tabs | `home_summarize_tabs` | P: other "Summarize my %d open tabs"; one suy ra |
| One answer that covers everything you have open | `home_summarize_tabs_body` | |
| Shortcuts | `home_shortcuts` | |
| Add | `home_add_shortcut` | |
| Continue with Skim | `home_continue_with_skim` | |
| See all | `action_see_all` | |
| 2 h ago | `time_hours_ago` | P: other "%d h ago"; one suy ra "%d h ago". Các đơn vị khác (phút, ngày) chưa có copy. |
| Yesterday | `date_yesterday` | |
| W, G, Y, H, R, M, S / Wikipedia, GitHub, YouTube, Hacker News, Reddit, Medium, Stack Overflow | — | `[S]` shortcut |
| D, daylightjournal.com, Why heat pumps…, T, tabi-note.jp, Osaka night buses… | — | `[S]` |
| Snippet "Cold-climate models now hold…", "Three routes run after midnight…" | — | `[AI]` tóm tắt ngắn của chat |

3. A11y: aria "Skim" trên tile Sunny (`role=img`) → dùng `app_name`; "Voice search" → `cd_voice_search`; toolbar: Back (disabled), Forward (disabled), "Tabs, 7 open", "More options"; FAB "Ask Skim". Shortcut và Add dùng text hiển thị (không cần cd riêng); hero card đọc theo text.
4. Icons: `ic_search`, `ic_mic`, `ic_auto_awesome_filled`, `ic_arrow_forward`, `ic_add`, `ic_arrow_back`, `ic_more_vert`.
5. Lệch: shape shortcut theo thứ tự Cookie9Sided, Clover4Leaf, SoftBurst, Cookie4Sided, Flower, Cookie12Sided, Pentagon (tokens.md không liệt kê Cookie9Sided/Cookie4Sided cho shortcut). Nút Add viền dashed (không có trong components.md). Card Continue dùng `surfaceContainerLow` trên `surface`.
6. Dữ liệu: số tab mở (hero + toolbar), danh sách Shortcut (name, url, chữ cái, shape theo vị trí), 2 SkimChat gần nhất loại PAGE (domain, thời gian tương đối, title, snippet), canGoBack/Forward = false.
7. State: một trạng thái; Home lần đầu (empty) chưa thiết kế (README).

### 04b Search & suggestions

1. Layout: nền **`surfaceContainerLow`**; spacer 24 → khung padding 8 16: bar 56 radius 28 `surfaceContainerHigh` padding 0×4 (IconButton Close search `ic_arrow_back` `onSurface`, input bodyLarge, IconButton Clear text `ic_close`) → vùng gợi ý (`role=group` "Suggestions", padding 8 16 0, gap 12 giữa các nhóm segmented):
   - Nhóm 1 (1 item 72, radius 20): tile 40 Sunny `tertiaryContainer` + `ic_auto_awesome_filled` 20; dòng 1 = query bodyLarge, dòng 2 "Ask Skim" 14/20 500 `tertiary`.
   - Nhóm 2 (3 item 56): icon `ic_search` trong ô 40, text bodyLarge, trailing IconButton 48 `ic_north_west`.
   - Nhóm 3 (1 item 72): `ic_history`, title bodyLarge ellipsis, domain bodyMedium.
2. Strings: "Search or ask anything" → `search_hint` (label ẩn); "Ask Skim" → `ask_skim`. `[S]`: heat pump, heat pump vs furnace, heat pump cost, heat pump rebates 2026, Why heat pumps…, daylightjournal.com.
3. A11y: "Close search" → `cd_close_search`; "Clear text" → `cd_clear_text`; "Suggestions" → `cd_suggestions` (nhóm); "Edit search: heat pump vs furnace" → `cd_edit_search` = "Edit search: %s".
4. Icons: `ic_arrow_back`, `ic_close`, `ic_auto_awesome_filled`, `ic_search`, `ic_north_west`, `ic_history`.
5. Lệch: nhãn AI dùng 500 (tokens nói AI labels titleSmallEmphasized 600).
6. Dữ liệu: query hiện tại; gợi ý engine (từ search engine đã chọn); history khớp; bookmark khớp `[SPEC]` (không vẽ). URL ban đầu khi mở từ 05 (chọn sẵn).
7. State: chỉ trạng thái đang gõ. Incognito (dark, không có hàng history và Ask Skim) `[SPEC]` chưa vẽ.

### 04c Skim history

1. Layout: nền `surfaceContainerLow`; Large title bar với 2 trailing (`ic_search`, `ic_delete_sweep`) → h1 "Skim history" → section Today / Yesterday / Earlier this week; item 2 dòng 72: tile 40 (trang: Cookie4Sided `surfaceContainerHighest` chữ 16/24 600 `onSecondaryContainer`; tabs: Sunny `tertiaryContainer` + `ic_auto_awesome_filled` 20), title bodyLarge ellipsis, phụ đề bodyMedium ellipsis, trailing IconButton 48 `ic_close` 20 → footer padding 24 32 0, gap 12: `ic_verified_user` 20 + bodySmall.
2. Strings:

| Text | Tên | Ghi chú |
|---|---|---|
| Skim history | `skim_history_title` | |
| Today / Yesterday / Earlier this week | `date_today` / `date_yesterday` / `date_earlier_this_week` | |
| 1 question about daylightjournal.com / 3 questions about kotlinlang.org | `skim_history_questions_about` | P: one "%1$d question about %2$s" (vẽ), other "%1$d questions about %2$s" (vẽ) |
| 1 answer across 7 tabs | `skim_history_answers_across_tabs` | P theo số answer: one "%1$d answer across %2$d tabs" (vẽ); other suy ra "%1$d answers across %2$d tabs" |
| Summary of tabi-note.jp | `skim_history_summary_of` = "Summary of %s" | |
| Summarize my 7 open tabs | `home_summarize_tabs` | tiêu đề item loại TABS |
| Stored only on this phone. You can turn this off in Settings › AI & privacy. | `skim_history_footer` | |
| Summaries you ask for show up here | `skim_history_empty` | `[SPEC]` |
| Why heat pumps…, Osaka night buses…, Kotlin 2.2 release notes, Heat pump rebates 2026, D/T/K/E | — | `[S]` |

3. A11y: "Back" `cd_back`; "Search Skim history" → `cd_search_skim_history`; "Delete all Skim history" → `cd_delete_all_skim_history`; "Remove from Skim history" (×5) → `cd_remove_from_skim_history`.
4. Icons: `ic_arrow_back`, `ic_search`, `ic_delete_sweep`, `ic_close`, `ic_auto_awesome_filled`, `ic_verified_user`.
5. Lệch: không.
6. Dữ liệu: danh sách SkimChat đã lưu, nhóm theo ngày (Today/Yesterday/Earlier this week), loại (PAGE/TABS), title, domain, số câu hỏi, có summary hay không, tabCount, updatedAt.
7. State: một trạng thái; search mode và empty chưa vẽ (empty copy có trong spec).

### 05 Browsing (+ Loading)

1. Layout: spacer 24 → Address bar (lock, địa chỉ, Reader view) → WebView (`surfaceContainerLowest` nền, chạy dưới toolbar) → Floating toolbar (Back bật, Forward tắt) + AI FAB. Pull-to-refresh (spec) không có nút reload.
2. Strings: không có chrome text hiển thị ngoài địa chỉ (dữ liệu). Toàn bộ trang (Daylight Journal, Energy, tiêu đề, By Marta Lindqvist, PHOTO, caption, 3 đoạn) là `[S]` web content.
3. A11y: "Site information, connection is secure" → `cd_site_info_secure`; "Edit address, daylightjournal.com" → `cd_edit_address` = "Edit address, %s"; "Reader view" → `cd_reader_view`; Loading: progressbar "Loading page" → `cd_loading_page`; toolbar chung. "Photo: a heat pump unit…" là alt của trang `[S]`.
4. Icons: `ic_lock`, `ic_chrome_reader_mode`, `ic_arrow_back`, `ic_arrow_forward`, `ic_more_vert`, `ic_auto_awesome_filled`.
5. Lệch: wavy progress Loading: 380 rộng, đặt ở cạnh dưới bar (left 16, bottom −6), sóng `primary` stroke 4 tới ~58%, track phẳng `secondaryContainer`, chấm dừng r2 `primary` → khớp spec.
6. Dữ liệu: Tab.url (host hiển thị), isSecure, readerAvailable, loadProgress (0..1), canGoBack/canGoForward, tabCount.
7. State: Loaded (mặc định) ↔ Loading (chỉ thêm wavy progress dưới bar; mọi thứ khác giữ nguyên).

### 05b More menu

1. Layout: nền 05 + scrim 32% → sheet (padding đáy 24): handle → header (padding 0 8 16 16, gap 12): favicon 40 Cookie4Sided chữ 16/24 600 + cột (title titleMedium ellipsis / domain bodySmall) + IconButton 48 `ic_star` + IconButton 48 `ic_share` → hàng "Page actions" (margin 0 16 16, gap 12): 2 nút tonal **M 56** (radius 28, titleMedium, icon 24 + gap 8): Find in page, Desktop site (toggle `aria-pressed`) → 3 nhóm segmented 56 (icon 24 `onSurfaceVariant` + bodyLarge): [New tab, New incognito tab], [Bookmarks, History], [Settings] (gap nhóm 12).
2. Strings:

| Text | Tên |
|---|---|
| Find in page | `find_in_page` |
| Desktop site | `menu_desktop_site` |
| New tab | `new_tab` |
| New incognito tab | `new_incognito_tab` |
| Bookmarks | `bookmarks_title` |
| History | `history_title` |
| Settings | `settings_title` |
| Why heat pumps…, daylightjournal.com, D | `[S]` |

3. A11y: pane "More options" → `cd_more_options`; "Add bookmark" → `cd_add_bookmark`; khi đã bookmark (sao filled) → "Edit bookmark" `edit_bookmark_title` `[SPEC]`; "Share page" → `cd_share_page`; nhóm "Page actions" → `cd_page_actions`.
4. Icons: `ic_star` (và `ic_star_filled` khi đã lưu – spec), `ic_share`, `ic_find_in_page`, `ic_desktop_windows`, `ic_tab`, `ic_visibility_off`, `ic_bookmarks`, `ic_history`, `ic_settings`. Nền: `ic_lock`, `ic_chrome_reader_mode`.
5. Lệch: nút page actions là tonal M 56 (components.md chỉ nói M cho Get started).
6. Dữ liệu: title, domain, chữ cái favicon, isBookmarked, desktopSite (per tab).
7. State: chỉ vẽ trạng thái chưa bookmark, Desktop site tắt. Trên 04 "05b without the page actions" chưa vẽ (không rõ header có ẩn không).

### 05c Find in page

1. Layout: spacer 24 → khung padding 8 16 trên `surface`: bar 56 radius 28 `surfaceContainerHigh` padding 0 4 0 20: input bodyLarge (label ẩn "Find in page") + bộ đếm 14/20 `onSurfaceVariant` `aria-live=polite` + IconButton up/down/close 48 → WebView với highlight: match hiện tại `primary`/`onPrimary`, match khác `primaryContainer`, radius 4 → Floating toolbar + AI FAB.
2. Strings: "Find in page" → `find_in_page`; "2/6" → `find_match_count` = "%1$d/%2$d" (NT có thể, nhưng nên để dịch được vì vị trí số); "heat pump" là query `[S]`.
3. A11y: "Previous match" `cd_previous_match`; "Next match" `cd_next_match`; "Close find in page" `cd_close_find_in_page`; toolbar chung. Bộ đếm cần `liveRegion = Polite`.
4. Icons: `ic_keyboard_arrow_up`, `ic_keyboard_arrow_down`, `ic_close`, toolbar.
5. Lệch: thanh Find thay chỗ Address bar (không phải component riêng trong components.md).
6. Dữ liệu: query, activeMatchOrdinal (1-based), numberOfMatches (WebView `FindListener`).
7. State: chỉ có kết quả; không-có-match chưa vẽ.

### 05d Link & image options (Link + Image)

1. Layout Link: nền 05 (link được highlight `primaryContainer`/`onPrimaryContainer` gạch chân radius 4) + scrim → sheet: header (padding 0 24 16 16, gap 16): tile 40 Cookie4Sided `surfaceContainerHighest` + `ic_link` 20 + cột (text link titleMedium ellipsis / URL bodySmall ellipsis) → nhóm segmented 4 item 56.
   Layout Image: ảnh được chọn có vòng 4 `primaryContainer`; header: thumbnail 56 radius 12 + `ic_image` + cột (alt text / URL ảnh) → 3 item 56.
2. Strings:

| Text | Tên |
|---|---|
| Open in new tab | `link_open_in_new_tab` |
| Open in incognito tab | `link_open_in_incognito_tab` |
| Copy link | `link_copy` |
| Share link | `link_share` |
| Open image in new tab | `image_open_in_new_tab` |
| Download image | `image_download` |
| Share image | `image_share` |
| 2026 rebate guide, energysaver.example/rebates-2026, A heat pump unit…, daylightjournal.com/images/heat-pump-snow.jpg | `[S]` (link text / alt / URL lấy từ HitTestResult) |

3. A11y: pane "Link options" → `cd_link_options`; "Image options" → `cd_image_options`.
4. Icons Link: `ic_link`, `ic_tab`, `ic_visibility_off`, `ic_content_copy`, `ic_share`. Image: `ic_image`, `ic_tab`, `ic_download`, `ic_share`. Nền: `ic_lock`, `ic_chrome_reader_mode`.
5. Lệch: thumbnail ảnh radius 12 (medium), không phải tile 40.
6. Dữ liệu: link URL + anchor text; image URL + alt + bitmap preview.
7. State: Link (mặc định) / Image. Image không có "Open in incognito" và "Copy".

### 06 Ask about this page

1. Layout: nền 05 + scrim → sheet cao 736 (half → full): handle → header (padding 0 8 12 16, gap 12): Sunny 44 `tertiaryContainer` + `ic_auto_awesome_filled` 22 + cột (page title titleMedium ellipsis / domain bodySmall) + IconButton Close 48 → nội dung (padding 4 16 0, gap 16): card Summary (`surfaceContainerLowest`, radius 20, padding 16 16 8, gap 12): nhãn [`ic_auto_awesome_filled` 20 + "Summary" 14/20 600 `tertiary`] + bullet list bodyMedium + hàng 3 IconButton **40** (copy, thumb_up, thumb_down, icon 20) → hàng chip XS outlined 32 (radius 16, viền 1 `outlineVariant`, labelLarge `onSurfaceVariant`) → bubble user (max 300, radius 20 20 4 20, `secondaryContainer`, padding 10×16, bodyMedium) → câu trả lời plain text bodyMedium (padding phải 24) + citation tonal XS 32 (`ic_format_quote` 18 + text) → input pill (padding 8 16 16): 56 radius 28 `surfaceContainerHigh`, padding 0 8 0 20, input bodyLarge + Send FilledIconButton 40 `primary` `ic_arrow_upward` 20.
2. Strings:

| Text | Tên | Ghi chú |
|---|---|---|
| Ask about this page | `ai_ask_about_page` | placeholder + pane title |
| Summary | `ai_summary` | |
| Key numbers / Explain simply / The catch? | `ai_chip_key_numbers` / `ai_chip_explain_simply` / `ai_chip_the_catch` | `[?]` có vẻ là chip cố định cho mọi trang (khác 06b nơi chip do AI sinh) |
| From paragraph 6 | `ai_citation_paragraph` = "From paragraph %d" | |
| Bullets tóm tắt, "How much would I save per year?", câu trả lời | — | `[AI]`/`[S]` |

3. A11y: pane "Ask about this page"; "Close" → `cd_close`; "Copy summary" → `cd_copy_summary`; "Good summary" → `cd_good_summary`; "Poor summary" → `cd_poor_summary`; "Send" → `cd_send`. Good/Poor là toggle (feedback).
4. Icons: `ic_auto_awesome_filled`, `ic_close`, `ic_content_copy`, `ic_thumb_up`, `ic_thumb_down`, `ic_format_quote`, `ic_arrow_upward`; nền `ic_lock`, `ic_chrome_reader_mode`.
5. Lệch: nút copy/feedback 40 (<48: cần `minimumInteractiveComponentSize`). Câu trả lời Q&A không có hàng Copy/feedback (components.md "Chat" nói có).
6. Dữ liệu: Tab hiện tại (title, domain), SkimChat của tab: summary (bullets, trạng thái), messages (user/assistant), citation (paragraph index), feedback per message, chip gợi ý.
7. State: xem 06c (đang sinh, lỗi). Sheet half/full.

### 06b Ask Skim from Home & Tabs

1. Layout: nền 04 (rút gọn) + scrim → sheet cao 760: header [Sunny 44 + cột ("Ask Skim" titleMedium / "Reading your 7 open tabs" bodySmall) + Close 48] → bubble user "Summarize my 7 open tabs" → card trả lời (`surfaceContainerLowest`, radius 20, padding 16 16 8, gap 16): câu mở bodyMedium + mỗi nhóm [TextButton link 32 `primary` 14/20 600 + `ic_chevron_right` 18] + đoạn bodyMedium → hàng copy/good/poor 40 → chip XS outlined (câu hỏi gợi ý) → input pill placeholder "Ask anything" + Send.
2. Strings:

| Text | Tên | Ghi chú |
|---|---|---|
| Ask Skim | `ask_skim` | |
| Reading your 7 open tabs | `ai_reading_tabs` | P: other "Reading your %d open tabs"; one suy ra |
| Summarize my 7 open tabs | `home_summarize_tabs` | prompt gửi sẵn từ hero 04 |
| Ask anything | `ai_ask_anything` | |
| Your tabs cover three topics., các đoạn theo nhóm, Which installer looked best?, What is left to book in Osaka? | — | `[AI]` |
| Heat pump research, Osaka trip | — | `[AI]`/`[S]` tên TabGroup |
| Other | `tabs_group_other` | `[?]` nhóm tab không thuộc nhóm nào; có vẻ chrome |

3. A11y: pane "Ask Skim"; Close; "Copy answer" → `cd_copy_answer`; "Good answer" → `cd_good_answer`; "Poor answer" → `cd_poor_answer`; Send.
4. Icons: `ic_auto_awesome_filled`, `ic_close`, `ic_chevron_right`, `ic_content_copy`, `ic_thumb_up`, `ic_thumb_down`, `ic_arrow_upward`, nền `ic_search`.
5. Lệch: link nhóm dùng `primary` (không tertiary) dù nằm trong AI card.
6. Dữ liệu: số tab thường (không incognito), danh sách TabGroup (id, tên) để link sang 09, SkimChat loại TABS (answer có sections theo group, suggested questions).
7. State: một trạng thái; lưu vào Skim history khi đóng.

### 06c AI generating & errors (Generating / Not enough text / No connection)

1. Layout chung: sheet 600 cao, header như 06, nội dung padding 4 16 0, input pill ở đáy.
   - Generating: card `surfaceContainerLowest` radius 20 padding 16 gap 16 (`aria-live=polite`): nhãn Summary (tertiary) → hàng [ContainedLoadingIndicator 48 `tertiaryContainer` + shape Cookie9Sided 30 `onTertiaryContainer`; cột "Writing the summary" titleMedium / "Reading the page…" bodyMedium] → wavy 348: sóng `tertiary`, **track `tertiaryContainer`**, chấm `tertiary`. Nút Send thay bằng Stop (FilledIconButton 40 `primary`, `ic_stop_filled`).
   - Not enough text: card [`ic_info` `onSurfaceVariant` + cột title titleMedium / body bodyMedium]; input bật, Send bật.
   - No connection: card [`ic_wifi_off` + cột] + nút tonal S 40 "Try again" căn phải; input pill tắt (nền `onSurface` 10%, text 38%, Send tắt 10%/38%).
2. Strings:

| Text | Tên |
|---|---|
| Summary | `ai_summary` |
| Writing the summary | `ai_writing_summary` |
| Reading the page… | `ai_reading_page` |
| Nothing to summarize here | `ai_not_enough_text_title` |
| This page has too little text for a summary. You can still ask a question about it below. | `ai_not_enough_text_body` |
| You're offline | `ai_offline_title` |
| Skim needs an internet connection to read this page. | `ai_offline_body` |
| Try again | `action_try_again` |
| Ask about this page | `ai_ask_about_page` |

3. A11y: pane "Ask about this page"; Close; progressbar "Writing summary" → `cd_writing_summary`; "Stop" → `cd_stop`; "Send" (disabled ở No connection).
4. Icons: `ic_auto_awesome_filled`, `ic_close`, `ic_stop_filled`, `ic_info`, `ic_wifi_off`, `ic_arrow_upward`; nền `ic_lock`, `ic_chrome_reader_mode`.
5. Lệch: track wavy `tertiaryContainer` (components.md nói `secondaryContainer`). Card lỗi không có nhãn Summary.
6. Dữ liệu: trạng thái request (Generating / Streaming / NotEnoughText / NoConnection / Done / Stopped), connectivity.
7. State: 3 state như trên; Stop giữ text đã stream.

### 06d AI permission, first time per site

1. Layout: nền 05 đầy đủ (address bar, page, toolbar + FAB) + scrim → AlertDialog 344 radius 28 padding 24: Sunny 56 `tertiaryContainer` + `ic_auto_awesome_filled` 28 căn giữa → tiêu đề headlineSmall căn giữa (margin 16) → 2 đoạn bodyMedium `onSurfaceVariant` (gap 12, margin 16) → nút text "Not now", "Allow" (margin 24).
2. Strings:

| Text | Tên |
|---|---|
| Let Skim read this page? | `ai_permission_title` |
| To summarize and answer your questions, Skim sends the text of %s to its AI. The text is encrypted and deleted after each answer. | `ai_permission_body` |
| You'll only be asked once for this site. Incognito tabs are never sent. | `ai_permission_note` |
| Not now | `action_not_now` |
| Allow | `action_allow` |

3. A11y: `role=alertdialog` với title/body làm label/description (AlertDialog Compose tự xử lý). Toolbar phía sau giữ cd nhưng inactive.
4. Icons: `ic_auto_awesome_filled`, nền: `ic_lock`, `ic_chrome_reader_mode`, toolbar.
5. Lệch: không.
6. Dữ liệu: host của trang; SitePermission.skimAi; setting `askBeforeSending`.
7. State: một trạng thái.

### 08 Reader view

1. Layout: spacer 24 → hàng 64 (padding 0×4, space-between): IconButton Exit `ic_close` `onSurface` + IconButton Text size `ic_format_size` (toggle, chưa chọn) → nội dung padding 8 24 0, gap 16: domain bodySmall → h1 28/36 500 → tác giả bodyMedium → đoạn văn 17/28 `onSurface` → Floating toolbar + AI FAB.
2. Strings: không có chrome text; domain/tiêu đề/tác giả/đoạn văn `[S]` (Readability).
3. A11y: "Exit reader view" → `cd_exit_reader_view`; "Text size" → `cd_text_size` (toggle, `aria-pressed=false`); toolbar chung.
4. Icons: `ic_close`, `ic_format_size`, toolbar.
5. Lệch: không address bar trên 08.
6. Dữ liệu: bài đọc (domain, title, byline, đoạn văn), readerTextScale, readerFont.
7. State: Text size off (08) / on (08b).

### 08b Reader text settings

1. Layout: 08 với Text size chọn (FilledTonalIconToggleButton 40 radius 12 `secondaryContainer`, `ic_format_size_filled`), chữ đọc scale 110% (h1 31/40) → sheet **không scrim**, padding 0 24 32, gap 16: handle → "Text" titleMediumEmphasized → khối Size: hàng ["Size" labelLarge `onSurfaceVariant` / "110%" bodyMedium] + hàng [A nhỏ 14 / slider 44 cao: active 16 cao `primary` radius 8 2 2 8, khe 6, tay cầm 4×44 `primary`, khe 6, inactive `secondaryContainer` với chấm 4 `primary` / A lớn 24/32] → khối Font: nhãn "Font" + connected group 40 (gap 2): "Sans serif" chọn (`primary`, `ic_check` 20, 600), "Serif" (radius 8 20 20 8, `surfaceContainer`, font Serif 15).
2. Strings:

| Text | Tên |
|---|---|
| Text | `reader_text` |
| Size | `reader_size` |
| 110% | `reader_size_percent` = "%d%%" |
| A (×2) | `reader_size_glyph` NT |
| Font | `reader_font` |
| Sans serif | `reader_font_sans_serif` |
| Serif | `reader_font_serif` |

3. A11y: pane "Text settings" → `cd_text_settings`; "Exit reader view", "Text size" (pressed); slider label = "Size", valuetext "110%" → `stateDescription` dùng `reader_size_percent`; group Font label "Font".
4. Icons: `ic_close`, `ic_format_size_filled`, `ic_check`.
5. Lệch: sheet 08b có shadow mạnh (`0 -1 3 / 0 -2 6 2`) thay cho scrim; không có floating toolbar khi sheet mở. Nút Serif hiển thị bằng chính font serif.
6. Dữ liệu: readerTextScalePercent (80..200 bước 10), readerFont.
7. State: một trạng thái.

### 09 Tabs

1. Layout: spacer 24 → hàng 64 (padding 0 4 0 16): h1 "7 tabs" headlineMediumEmphasized + IconButton search → connected group "Tab list" 40 (margin 0 16 16): "Open" chọn (`primary` + `ic_check`, 600) / "Incognito" (radius 8 20 20 8, `surfaceContainer`) → nội dung padding 0 16, gap 16: AI banner (min 56, radius 20, `tertiaryContainer`, padding 8 8 8 16, gap 12: `ic_auto_awesome_filled` 20 + text bodyMedium + TextButton "Undo" 40 600 `onTertiaryContainer`) → mỗi section: header 40 [tên nhóm titleMediumEmphasized + Badge 24 (`secondaryContainer`, 12/16 600) + spacer + IconButton Group options **40** `ic_more_vert`] + lưới 2 cột gap 12 Tab card (radius 20 `surfaceContainerLow`; hàng title 44 padding 0 4 0 12 gap 8: favicon 20 Cookie4Sided chữ 10/12 600, title labelMedium ellipsis, IconButton Close tab **36** `ic_close` 18; preview 92 margin 0 8 8 radius 12 `surfaceContainerLowest`; tab hiện tại: vòng inset 3 `primary`; tab có Skim chat: badge Sunny 24 `tertiaryContainer` góc phải dưới cách 8) → đáy cách 24: toolbar "Tab actions" (AI `ic_auto_awesome_filled` màu `tertiary`, `ic_visibility_off`, `ic_delete`) + FAB "New tab" 56 radius 16 `primaryContainer` `ic_add`.
2. Strings:

| Text | Tên | Ghi chú |
|---|---|---|
| 7 tabs | `tabs_title` | P: other "%d tabs"; one suy ra "%d tab" |
| Open | `tabs_segment_open` | |
| Incognito | `tabs_segment_incognito` | |
| Skim grouped your tabs by topic | `tabs_grouped_banner` | |
| Undo | `action_undo` | |
| 4 / 2 / 1 | — | số đếm Badge (định dạng số, không phải string) |
| Other | `tabs_group_other` | `[?]` không có Group options → nhóm hệ thống cho tab lẻ |
| Heat pump research, Osaka trip | — | `[AI]` tên nhóm |
| 7 tiêu đề tab + chữ cái | — | `[S]` |

3. A11y: "Search tabs" → `cd_search_tabs`; nhóm "Tab list" → `cd_tab_list`; "Group options" → `cd_group_options`; "Close tab" → `cd_close_tab`; toolbar "Tab actions" → `cd_tab_actions`; "Ask Skim about these tabs" → `cd_ask_skim_about_tabs`; "New incognito tab" → `new_incognito_tab`; "Close all tabs" → `cd_close_all_tabs`; "New tab" → `new_tab`.
4. Icons: `ic_search`, `ic_check`, `ic_auto_awesome_filled`, `ic_more_vert`, `ic_close`, `ic_visibility_off`, `ic_delete`, `ic_add`.
5. Lệch: favicon letter 10/12 (không có token); nút Close tab 36 và Group options 40 (<48); preview hiện là skeleton màu xám (`#C9CAE4`, `#DCE2E8`, `#E4E4F2`, `#E8E8F6` – chỉ là placeholder cho bitmap).
6. Dữ liệu: tab thường (title, chữ cái favicon, preview bitmap, groupId, isCurrent, hasSkimChat), TabGroup (tên, số tab, thứ tự), cờ "Skim vừa nhóm" + snapshot thứ tự cũ cho Undo, tổng số tab.
7. State: một trạng thái; search mode, empty, kéo thả chưa vẽ.

### 09b Incognito tabs

1. Layout (dark scheme, nền `dk:surface`): hàng 64: h1 "2 incognito tabs" + search → connected group (Open không chọn radius 20 8 8 20 `dk:surfaceContainer`; Incognito chọn `dk:primary` + `ic_check`) → banner thông tin (radius 20, `dk:surfaceContainer`, padding 16, gap 12: `ic_visibility_off` 20 + bodyMedium) → lưới tab card (`dk:surfaceContainer`, vòng tab hiện tại `dk:primary`) → toolbar `dk:surfaceContainerHigh` [AI tắt (`ic_auto_awesome` 38%), Close all `ic_delete`] + FAB "New incognito tab" `primaryContainer`.
2. Strings:

| Text | Tên |
|---|---|
| 2 incognito tabs | `incognito_tabs_title` P: other "%d incognito tabs"; one suy ra "%d incognito tab" |
| Open / Incognito | `tabs_segment_open` / `tabs_segment_incognito` |
| Skim AI is off in incognito. These pages are never sent to it, and Skim forgets them when you close the tabs. | `incognito_tabs_banner` |
| Anniversary gift ideas, Flights to Lisbon in May, G, F | `[S]` |

3. A11y: "Search incognito tabs" → `cd_search_incognito_tabs`; "Tab list"; "Close tab"; "Tab actions"; "Ask Skim, off in incognito" (disabled); "Close all incognito tabs" → `cd_close_all_incognito_tabs`; "New incognito tab".
4. Icons: `ic_search`, `ic_check`, `ic_visibility_off`, `ic_close`, `ic_auto_awesome` (outline, tắt), `ic_delete`, `ic_add`.
5. Lệch: tab card `dk:surfaceContainer` (spec: `surfaceContainerLow`); toolbar không có nút New incognito tab (FAB đã đảm nhiệm).
6. Dữ liệu: incognito tabs (title, favicon letter, preview, isCurrent), số lượng.
7. State: một trạng thái; FLAG_SECURE.

### 09c Incognito new tab

1. Layout (dark): spacer 24 → khung padding 8 16: bar 56 radius 28 **`dk:surfaceContainerHighest`**, leading `ic_visibility_off` 20 (ô 48), input placeholder "Search or type URL", mic 48 → nội dung padding 32 16 0: hero (padding 0 8): Cookie12Sided 72 `dk:secondaryContainer` + `ic_visibility_off` 36 → h1 28/36 500 (margin 24) → body bodyMedium (margin 8) → section "Skim won't save" (padding 32 16 8, 14/20 600 `dk:primary`) → danh sách 3 item tĩnh min 48 (padding 12 16, `dk:surfaceContainer`, icon `dk:onSurfaceVariant` + bodyLarge) → section "Still visible to" (padding 20 16 8) → 3 item → footnote bodySmall (padding 16 16 0) → toolbar dark (Back/Forward tắt, "Incognito tabs, 2 open", More) + FAB AI tắt.
2. Strings:

| Text | Tên |
|---|---|
| Search or type URL | `incognito_search_hint` |
| You're incognito | `incognito_title` |
| Other people who use this phone won't see what you do here. Skim AI is off, so pages in incognito tabs are never sent to it. | `incognito_body` |
| Skim won't save | `incognito_wont_save` |
| Pages you visit | `incognito_pages_you_visit` |
| Cookies and site data | `cookies_and_site_data` |
| What you type into forms | `incognito_form_input` |
| Still visible to | `incognito_still_visible_to` |
| Websites you visit | `incognito_websites_you_visit` |
| Your employer or school | `incognito_employer_or_school` |
| Your internet provider | `incognito_internet_provider` |
| Bookmarks and downloads you make here are kept. Closing every incognito tab ends the session. | `incognito_footer` |

3. A11y: "Voice search"; toolbar: Back/Forward (disabled), "Incognito tabs, 2 open" → `cd_incognito_tabs_open`, "More options"; "Ask Skim, off in incognito" (disabled).
4. Icons: `ic_visibility_off`, `ic_mic`, `ic_history`, `ic_cookie`, `ic_keyboard`, `ic_public`, `ic_work`, `ic_router`, `ic_arrow_back`, `ic_arrow_forward`, `ic_more_vert`, `ic_auto_awesome` (outline).
5. Lệch: search bar dùng `surfaceContainerHighest` + icon `visibility_off` và placeholder khác 04 (spec nói "same bar with the dark scheme"). List dùng `dk:surfaceContainer` trên `dk:surface`, item 48 không tương tác.
6. Dữ liệu: số incognito tab.
7. State: một trạng thái.

### 10 AI & privacy settings

1. Layout: nền `surfaceContainerLow`, Large title bar (chỉ Back) → h1 → section "Privacy" → nhóm 3 item 72: [tile 40 Circle `secondaryContainer` `ic_cloud_upload` + 2 dòng + Switch 52×32 bật], [tile `ic_history` + 2 dòng + Switch bật], [tile Sunny `tertiaryContainer` `ic_auto_awesome_filled` 20 + 2 dòng, không chevron] → nhóm 1 item 56 (margin 16 16 0): tile 40 `errorContainer`/`onErrorContainer` `ic_delete` + text bodyLarge **500 `error`** → footer (padding 24 32 0, gap 16): `ic_info` 20 + cột (bodyMedium + link 14/20 600 `primary`).
2. Strings:

| Text | Tên |
|---|---|
| AI & privacy | `settings_ai_privacy` |
| Privacy | `settings_section_privacy` |
| Ask before sending a page to the cloud | `ai_settings_ask_before_sending` |
| Shows a prompt the first time you use AI on a site | `ai_settings_ask_before_sending_summary` |
| Keep chat history on this device | `ai_settings_keep_history` |
| Never synced. Delete it anytime below. | `ai_settings_keep_history_summary` |
| Skim history | `skim_history_title` |
| 5 conversations on this phone | `ai_settings_conversations` P: other "%d conversations on this phone"; one suy ra |
| Delete all AI data on this device | `ai_settings_delete_all` |
| To turn Skim AI off for one site, tap the lock in the address bar. Incognito tabs never use it. | `ai_settings_footer` |
| How Skim AI handles your data | `ai_settings_data_link` |

3. A11y: "Back". Switch label qua `aria-labelledby` → dùng `Modifier.toggleable` trên cả hàng (role Switch) với text làm label.
4. Icons: `ic_arrow_back`, `ic_cloud_upload`, `ic_history`, `ic_check` (thumb), `ic_auto_awesome_filled`, `ic_delete`, `ic_info`.
5. Lệch: chữ hàng Delete dùng `error` 500 (khác 12 nơi chữ Clear browsing data là `onSurface`).
6. Dữ liệu: `askBeforeSending`, `keepChatHistory`, số SkimChat đã lưu.
7. State: chỉ vẽ cả 2 switch bật.

### 11 Page failed to load (Unreachable / No internet / Timed out)

1. Layout: spacer 24 → address bar (icon `ic_error` 20 tĩnh, địa chỉ là nút, không Reader view) → khối giữa (padding 0 32 120, gap 32): art 128 (`primaryContainer`/`onPrimaryContainer`, icon 56) → tiêu đề 28/36 500 + body bodyLarge `onSurfaceVariant` căn giữa (gap 12) → cột nút (max 280, gap 12): Button M filled 56 [`ic_refresh` + "Try again"] (+ Button M tonal [`ic_wifi` + "Internet settings"] chỉ ở No internet) → mã lỗi bodySmall → toolbar (Back bật) + FAB tắt.
2. Strings:

| Text | Tên |
|---|---|
| Can't reach this site | `error_unreachable_title` |
| Skim couldn't find %s. Check the address for typos, or try again in a moment. | `error_unreachable_body` |
| No internet connection | `error_no_internet_title` |
| Turn on Wi-Fi or mobile data, then try again. Skim reloads this tab as soon as you're back online. | `error_no_internet_body` |
| This site is taking too long | `error_timed_out_title` |
| %s didn't respond. It may be busy or briefly down. | `error_timed_out_body` |
| Try again | `action_try_again` |
| Internet settings | `error_internet_settings` |
| ERR_NAME_NOT_RESOLVED / ERR_INTERNET_DISCONNECTED / ERR_CONNECTION_TIMED_OUT | `error_code_*` NT (map từ mã WebView) |
| nightbus-kansai.jp/timetable | `[S]` |

3. A11y: icon lỗi `role=img` "Page didn't load" → `cd_page_didnt_load`; "Edit address, nightbus-kansai.jp/timetable" → `cd_edit_address`; FAB "Ask Skim about this page, unavailable until the page loads" (disabled).
4. Icons: `ic_error`, `ic_public_off` (Unreachable), `ic_wifi_off` + `ic_wifi` (No internet), `ic_schedule` (Timed out), `ic_refresh`, toolbar, `ic_auto_awesome` (outline, tắt).
5. Shape art: Unreachable SoftBurst, No internet Cookie12Sided, Timed out Clover8Leaf.
6. Dữ liệu: url, host, loại lỗi (từ WebView error code), tên mã lỗi, connectivity (tự reload khi online).
7. State: 3 state; khác nhau ở shape+icon art, tiêu đề, body, nút Internet settings, mã lỗi.

### 12 Settings

1. Layout: nền `surfaceContainerLow`, Large title bar (Back) → h1 "Settings" → section General: 3 item 72 (tile Circle 40: `ic_search` `secondaryContainer`; `ic_public` **`primaryContainer`**; `ic_palette` `secondaryContainer`) → section Skim AI: 1 item 72 (Sunny) → section Privacy: 1 item 72 (tile `errorContainer` `ic_delete`, chữ `onSurface`) → section About: 4 item 56 (tile Circle `surfaceContainer` `onSurfaceVariant`: `ic_shield`, `ic_description`, `ic_code`, `ic_info`; Version không bấm, giá trị trailing bodyMedium).
2. Strings:

| Text | Tên |
|---|---|
| Settings | `settings_title` |
| General | `settings_section_general` |
| Search engine | `settings_search_engine` |
| Google | `search_engine_google` NT (giá trị hiện tại) |
| Default browser | `settings_default_browser` |
| Skim is your default | `settings_default_browser_summary_is_default` |
| Theme | `settings_theme` |
| System default | `theme_system_default` |
| Skim AI | `skim_ai` |
| AI & privacy | `settings_ai_privacy` |
| Asks before reading new sites | `settings_ai_privacy_summary_ask` |
| Privacy | `settings_section_privacy` |
| Clear browsing data | `clear_browsing_data` |
| History, cookies and cached files | `settings_clear_browsing_data_summary` |
| About | `settings_section_about` |
| Privacy policy | `settings_privacy_policy` |
| Terms of service | `settings_terms_of_service` |
| Open-source licenses | `settings_open_source_licenses` |
| Version | `settings_version` |
| 1.0.0 (100) | `settings_version_value` = "%1$s (%2$d)" NT (BuildConfig) |

3. A11y: "Back" only.
4. Icons: `ic_arrow_back`, `ic_search`, `ic_public`, `ic_palette`, `ic_auto_awesome_filled`, `ic_delete`, `ic_shield`, `ic_description`, `ic_code`, `ic_info`.
5. Lệch: tile Default browser dùng `primaryContainer` (đúng tokens).
6. Dữ liệu: searchEngine, isDefaultBrowser, theme, askBeforeSending (để chọn phụ đề AI & privacy), versionName/versionCode.
7. State: chỉ vẽ: Google, đã là default, System default, ask-before bật. Phụ đề khi chưa default / khi ask-before tắt chưa có copy.

### 13 History

1. Layout: nền `surfaceContainerLow`, Large title bar [Back + search] → h1 "History" → nhóm 1 item 56 (margin 8 16 0): tile `errorContainer` `ic_delete` + "Clear browsing data" → section Today (4 item) / Yesterday (3 item): item 72 [Cookie4Sided 40 chữ `onSecondaryContainer` + title/domain + IconButton 48 `ic_close` 20].
2. Strings: `history_title`, `clear_browsing_data`, `date_today`, `date_yesterday`. `[S]`: 7 tiêu đề + domain (daylightjournal.com, heatcalc.example, localreviews.example, energysaver.example, tabi-note.jp, staysearch.example, kotlinlang.org).
3. A11y: "Back"; "Search history" → `cd_search_history`; "Remove from history" (×7) → `cd_remove_from_history`.
4. Icons: `ic_arrow_back`, `ic_search`, `ic_delete`, `ic_close`.
5. Lệch: không hiển thị giờ truy cập.
6. Dữ liệu: HistoryEntry (title, url/host, visitedAt) nhóm theo ngày; không bao giờ có incognito.
7. State: một trạng thái.

### 14 Bookmarks

1. Layout: nền `surfaceContainerLow`, Large title bar [Back + search] → h1 → section "Folders": 2 item 72 [tile 40 **radius 12** `secondaryContainer` `ic_folder_filled` + title/phụ đề] → section "Recently added": 3 item 72 [Cookie4Sided 40 chữ `onSurfaceVariant` + title/domain + IconButton 48 `ic_more_vert`].
2. Strings:

| Text | Tên |
|---|---|
| Bookmarks | `bookmarks_title` |
| Folders | `bookmarks_section_folders` |
| Mobile bookmarks | `bookmarks_folder_mobile` |
| 3 bookmarks | `bookmarks_count` P: other "%d bookmarks"; one suy ra "%d bookmark" |
| Imported | `bookmarks_folder_imported` (tên thư mục mặc định khi import) |
| 24 bookmarks from bookmarks.html | `bookmarks_imported_count_from` P: other "%1$d bookmarks from %2$s"; one suy ra |
| Recently added | `bookmarks_section_recently_added` |
| 3 bookmark (D/T/K) | `[S]` |

3. A11y: "Back"; "Search bookmarks" → `cd_search_bookmarks`; "Bookmark options" (×3) → `cd_bookmark_options`.
4. Icons: `ic_arrow_back`, `ic_search`, `ic_folder_filled`, `ic_more_vert`.
5. Lệch: tile folder radius 12 (không Circle/Cookie). Chữ favicon `onSurfaceVariant` (13 dùng `onSecondaryContainer`).
6. Dữ liệu: BookmarkFolder (tên, số bookmark, file nguồn), bookmark gần đây (title, host, addedAt).
7. State: một trạng thái; folder đã mở chưa vẽ.

### 15 Dialogs, menus & snackbars

Toàn bộ overlay xem mục 3(d). Strings riêng của 15:

| Text | Tên | Ghi chú |
|---|---|---|
| Add shortcut | `dialog_add_shortcut_title` | |
| Name / URL / Folder / Group name | `field_name` / `field_url` / `field_folder` / `field_group_name` | label OutlinedTextField |
| Cancel / Save / Clear / Delete / Block / Allow | `action_cancel` / `action_save` / `action_clear` / `action_delete` / `action_block` / `action_allow` | |
| Search engine | `settings_search_engine` | tiêu đề dialog |
| Google / Bing / DuckDuckGo / Ecosia | `search_engine_google` / `_bing` / `_duckduckgo` / `_ecosia` | NT |
| Theme | `settings_theme` | |
| System default / Light / Dark | `theme_system_default` / `theme_light` / `theme_dark` | |
| Clear browsing data | `clear_browsing_data` | |
| Removes data saved on this phone, from all time. | `clear_data_body` | |
| Browsing history | `clear_data_browsing_history` | |
| Cookies and site data | `cookies_and_site_data` | |
| Signs you out of most sites | `clear_data_cookies_summary` | |
| Cached images and files | `clear_data_cache` | |
| Skim history is managed in AI & privacy. | `clear_data_footer` | |
| Delete all AI data? | `delete_ai_data_title` | |
| Your Skim history is deleted and every site asks again before Skim reads it. You can't undo this. | `delete_ai_data_body` | |
| Clear Skim history? | `clear_skim_history_title` | |
| Summaries and answers saved on this phone are deleted. Sites you allowed stay allowed. | `clear_skim_history_body` | |
| Share your location with daylightjournal.com? | `site_permission_location_title` = "Share your location with %s?" | camera/mic chưa có copy |
| The site can use it while it's open. Change this anytime from the lock in the address bar. | `site_permission_body` | |
| Rename group | `rename_group` | tiêu đề dialog + item menu |
| Edit bookmark | `edit_bookmark_title` | |
| Mobile bookmarks / Imported | `bookmarks_folder_mobile` / `bookmarks_folder_imported` | |
| Move to folder | `move_to_folder` | tiêu đề + item menu |
| Edit shortcut | `shortcut_menu_edit` | |
| Remove | `action_remove` | |
| Ungroup | `tab_group_ungroup` | |
| Close 4 tabs | `tab_group_close_tabs` | P: other "Close %d tabs"; one suy ra "Close %d tab" |
| Edit / Delete | `action_edit` / `action_delete` | menu bookmark |
| Connection is secure | `site_info_connection_secure` | |
| Permissions | `site_info_permissions` | |
| Skim AI | `skim_ai` | |
| Can read this site when you ask | `site_info_skim_ai_summary` | |
| Location | `site_info_location` | |
| Blocked | `site_permission_blocked` | |
| Stored by this site on your phone | `site_info_cookies_summary` | |
| Tab closed | `snackbar_tab_closed` | |
| 7 tabs closed | `snackbar_tabs_closed` | P: other "%d tabs closed" |
| Bookmark added | `snackbar_bookmark_added` | |
| Removed from history | `snackbar_removed_from_history` | dùng cho cả 13 và 04c |
| Bookmark deleted | `snackbar_bookmark_deleted` | |
| Opened in a new tab | `snackbar_opened_in_new_tab` | |
| Switch / View / Open / Undo / Edit | `action_switch` / `action_view` / `action_open` / `action_undo` / `action_edit` | action snackbar |
| Imported 24 bookmarks | `snackbar_imported_bookmarks` | P: other "Imported %d bookmarks" |
| Couldn't read that file. Choose an HTML bookmarks file exported from a browser. | `snackbar_import_failed` | |
| rebate-guide.pdf downloaded | `snackbar_downloaded` = "%s downloaded" | |
| Copied to clipboard | `snackbar_copied` | chỉ Android ≤ 12 |
| Shortcut removed | `snackbar_shortcut_removed` | `[SPEC]` |
| Daylight Journal, daylightjournal.com, Heat pump research, Why heat pumps…, daylightjournal.com/energy/heat-pumps-cold, W/G/Y/H…, D/T, 4 | — | `[S]` giá trị điền sẵn |
| Tiêu đề/mô tả phụ của từng ô trên board ("Home › Add (04)…", "Close a tab or swipe it away (09)"…) | — | chú thích designer, không phải string |

A11y 15: "Folder, Mobile bookmarks" → `cd_folder_field` = "Folder, %s"; menu "Wikipedia shortcut" → `cd_shortcut_menu` = "%s shortcut"; "Heat pump research group" → `cd_tab_group_menu` = "%s group"; "Bookmark options" (menu) → `cd_bookmark_options`; "Delete cookies and site data for daylightjournal.com" → `cd_delete_site_data` = "Delete cookies and site data for %s". Radio/checkbox/switch dùng text hàng làm label.

Icons 15: `ic_arrow_drop_down`, `ic_check`, `ic_delete`, `ic_location_on`, `ic_edit`, `ic_drive_file_rename_outline`, `ic_layers_clear`, `ic_close`, `ic_drive_file_move`, `ic_lock`, `ic_auto_awesome_filled`, `ic_cookie`, `ic_more_vert`.

## 3. Phần tổng hợp liên màn hình

### 3(a) Danh sách strings đã khử trùng

Strings thường (`<string>`):

| Tên | Text | Board |
|---|---|---|
| `app_name` | Skim | 01, cd logo 04 |
| `action_cancel` | Cancel | 15 |
| `action_save` | Save | 15 |
| `action_allow` | Allow | 06d, 15 |
| `action_not_now` | Not now | 06d |
| `action_block` | Block | 15 |
| `action_try_again` | Try again | 06c, 11 |
| `action_undo` | Undo | 09, 15 |
| `action_edit` | Edit | 15 (snackbar + menu) |
| `action_delete` | Delete | 15 |
| `action_clear` | Clear | 15 |
| `action_remove` | Remove | 15 |
| `action_switch` | Switch | 15 |
| `action_view` | View | 15 |
| `action_open` | Open | 15 (snackbar download) |
| `action_done` | Done | 03 `[SPEC]` |
| `action_see_all` | See all | 04 |
| `welcome_headline` | Browse less. Understand more. | 01 |
| `welcome_body` | Skim reads pages with you. Summaries and answers, built into the browser. | 01 |
| `welcome_get_started` | Get started | 01 |
| `welcome_legal` | By continuing you agree to the %1$s and %2$s. | 01 |
| `welcome_terms_of_service` | Terms of Service | 01 |
| `welcome_privacy_policy` | Privacy Policy | 01 |
| `setup_title` | Almost there | 03 |
| `setup_body` | Two optional steps that make Skim feel like home. You can change the default browser later in Settings. | 03 |
| `setup_default_title` | Make Skim your default browser | 03 |
| `setup_default_body` | Links from other apps open here, with summaries and answers one tap away. | 03 |
| `setup_set_as_default` | Set as default | 03 |
| `setup_import_title` | Bring your bookmarks | 03 |
| `setup_import_body` | Choose an HTML bookmarks file exported from another browser. | 03 |
| `setup_choose_file` | Choose file | 03 |
| `setup_privacy_note` | Skim reads a page only when you ask, and never in incognito tabs. Page text is sent encrypted and deleted after each answer. | 03 |
| `setup_start_browsing` | Start browsing | 03 |
| `search_hint` | Search or ask anything | 04, 04b |
| `home_summarize_tabs_body` | One answer that covers everything you have open | 04 |
| `home_shortcuts` | Shortcuts | 04 |
| `home_add_shortcut` | Add | 04 |
| `home_continue_with_skim` | Continue with Skim | 04 |
| `ask_skim` | Ask Skim | 04 (cd FAB), 04b, 06b |
| `date_today` | Today | 04c, 13 |
| `date_yesterday` | Yesterday | 04, 04c, 13 |
| `date_earlier_this_week` | Earlier this week | 04c |
| `skim_history_title` | Skim history | 04c, 10 |
| `skim_history_summary_of` | Summary of %s | 04c |
| `skim_history_footer` | Stored only on this phone. You can turn this off in Settings › AI & privacy. | 04c |
| `skim_history_empty` | Summaries you ask for show up here | 04c `[SPEC]` |
| `find_in_page` | Find in page | 05b, 05c |
| `find_match_count` | %1$d/%2$d | 05c |
| `menu_desktop_site` | Desktop site | 05b |
| `new_tab` | New tab | 05b, cd 09 |
| `new_incognito_tab` | New incognito tab | 05b, cd 09, 09b |
| `bookmarks_title` | Bookmarks | 05b, 14 |
| `history_title` | History | 05b, 13 |
| `settings_title` | Settings | 05b, 12 |
| `link_open_in_new_tab` | Open in new tab | 05d |
| `link_open_in_incognito_tab` | Open in incognito tab | 05d |
| `link_copy` | Copy link | 05d |
| `link_share` | Share link | 05d |
| `image_open_in_new_tab` | Open image in new tab | 05d |
| `image_download` | Download image | 05d |
| `image_share` | Share image | 05d |
| `ai_ask_about_page` | Ask about this page | 06, 06c |
| `ai_summary` | Summary | 06, 06c |
| `ai_chip_key_numbers` | Key numbers | 06 `[?]` |
| `ai_chip_explain_simply` | Explain simply | 06 `[?]` |
| `ai_chip_the_catch` | The catch? | 06 `[?]` |
| `ai_citation_paragraph` | From paragraph %d | 06 |
| `ai_ask_anything` | Ask anything | 06b |
| `ai_writing_summary` | Writing the summary | 06c |
| `ai_reading_page` | Reading the page… | 06c |
| `ai_not_enough_text_title` | Nothing to summarize here | 06c |
| `ai_not_enough_text_body` | This page has too little text for a summary. You can still ask a question about it below. | 06c |
| `ai_offline_title` | You're offline | 06c |
| `ai_offline_body` | Skim needs an internet connection to read this page. | 06c |
| `ai_permission_title` | Let Skim read this page? | 06d |
| `ai_permission_body` | To summarize and answer your questions, Skim sends the text of %s to its AI. The text is encrypted and deleted after each answer. | 06d |
| `ai_permission_note` | You'll only be asked once for this site. Incognito tabs are never sent. | 06d |
| `reader_text` | Text | 08b |
| `reader_size` | Size | 08b |
| `reader_size_percent` | %d%% | 08b |
| `reader_size_glyph` | A | 08b NT |
| `reader_font` | Font | 08b |
| `reader_font_sans_serif` | Sans serif | 08b |
| `reader_font_serif` | Serif | 08b |
| `tabs_segment_open` | Open | 09, 09b |
| `tabs_segment_incognito` | Incognito | 09, 09b |
| `tabs_grouped_banner` | Skim grouped your tabs by topic | 09 |
| `tabs_group_other` | Other | 09, 06b `[?]` |
| `incognito_tabs_banner` | Skim AI is off in incognito. These pages are never sent to it, and Skim forgets them when you close the tabs. | 09b |
| `incognito_search_hint` | Search or type URL | 09c |
| `incognito_title` | You're incognito | 09c |
| `incognito_body` | Other people who use this phone won't see what you do here. Skim AI is off, so pages in incognito tabs are never sent to it. | 09c |
| `incognito_wont_save` | Skim won't save | 09c |
| `incognito_pages_you_visit` | Pages you visit | 09c |
| `cookies_and_site_data` | Cookies and site data | 09c, 15 (×2) |
| `incognito_form_input` | What you type into forms | 09c |
| `incognito_still_visible_to` | Still visible to | 09c |
| `incognito_websites_you_visit` | Websites you visit | 09c |
| `incognito_employer_or_school` | Your employer or school | 09c |
| `incognito_internet_provider` | Your internet provider | 09c |
| `incognito_footer` | Bookmarks and downloads you make here are kept. Closing every incognito tab ends the session. | 09c |
| `settings_ai_privacy` | AI & privacy | 10, 12 |
| `settings_section_privacy` | Privacy | 10, 12 |
| `ai_settings_ask_before_sending` | Ask before sending a page to the cloud | 10 |
| `ai_settings_ask_before_sending_summary` | Shows a prompt the first time you use AI on a site | 10 |
| `ai_settings_keep_history` | Keep chat history on this device | 10 |
| `ai_settings_keep_history_summary` | Never synced. Delete it anytime below. | 10 |
| `ai_settings_delete_all` | Delete all AI data on this device | 10 |
| `ai_settings_footer` | To turn Skim AI off for one site, tap the lock in the address bar. Incognito tabs never use it. | 10 |
| `ai_settings_data_link` | How Skim AI handles your data | 10 |
| `error_unreachable_title` | Can't reach this site | 11 |
| `error_unreachable_body` | Skim couldn't find %s. Check the address for typos, or try again in a moment. | 11 |
| `error_no_internet_title` | No internet connection | 11 |
| `error_no_internet_body` | Turn on Wi-Fi or mobile data, then try again. Skim reloads this tab as soon as you're back online. | 11 |
| `error_timed_out_title` | This site is taking too long | 11 |
| `error_timed_out_body` | %s didn't respond. It may be busy or briefly down. | 11 |
| `error_internet_settings` | Internet settings | 11 |
| `error_code_name_not_resolved` / `_internet_disconnected` / `_connection_timed_out` | ERR_NAME_NOT_RESOLVED / ERR_INTERNET_DISCONNECTED / ERR_CONNECTION_TIMED_OUT | 11 NT |
| `settings_section_general` | General | 12 |
| `settings_search_engine` | Search engine | 12, 15 |
| `search_engine_google` / `_bing` / `_duckduckgo` / `_ecosia` | Google / Bing / DuckDuckGo / Ecosia | 12, 15 NT |
| `settings_default_browser` | Default browser | 12 |
| `settings_default_browser_summary_is_default` | Skim is your default | 12 |
| `settings_theme` | Theme | 12, 15 |
| `theme_system_default` | System default | 12, 15 |
| `theme_light` | Light | 15 |
| `theme_dark` | Dark | 15 |
| `skim_ai` | Skim AI | 12, 15 |
| `settings_ai_privacy_summary_ask` | Asks before reading new sites | 12 |
| `clear_browsing_data` | Clear browsing data | 12, 13, 15 |
| `settings_clear_browsing_data_summary` | History, cookies and cached files | 12 |
| `settings_section_about` | About | 12 |
| `settings_privacy_policy` | Privacy policy | 12 |
| `settings_terms_of_service` | Terms of service | 12 |
| `settings_open_source_licenses` | Open-source licenses | 12 |
| `settings_version` | Version | 12 |
| `settings_version_value` | %1$s (%2$d) | 12 NT |
| `bookmarks_section_folders` | Folders | 14 |
| `bookmarks_folder_mobile` | Mobile bookmarks | 14, 15 |
| `bookmarks_folder_imported` | Imported | 14, 15 |
| `bookmarks_section_recently_added` | Recently added | 14 |
| `dialog_add_shortcut_title` | Add shortcut | 15 |
| `field_name` | Name | 15 |
| `field_url` | URL | 15 |
| `field_folder` | Folder | 15 |
| `field_group_name` | Group name | 15 |
| `clear_data_body` | Removes data saved on this phone, from all time. | 15 |
| `clear_data_browsing_history` | Browsing history | 15 |
| `clear_data_cookies_summary` | Signs you out of most sites | 15 |
| `clear_data_cache` | Cached images and files | 15 |
| `clear_data_footer` | Skim history is managed in AI & privacy. | 15 |
| `delete_ai_data_title` | Delete all AI data? | 15 |
| `delete_ai_data_body` | Your Skim history is deleted and every site asks again before Skim reads it. You can't undo this. | 15 |
| `clear_skim_history_title` | Clear Skim history? | 15 |
| `clear_skim_history_body` | Summaries and answers saved on this phone are deleted. Sites you allowed stay allowed. | 15 |
| `site_permission_location_title` | Share your location with %s? | 15 |
| `site_permission_body` | The site can use it while it's open. Change this anytime from the lock in the address bar. | 15 |
| `rename_group` | Rename group | 15 |
| `edit_bookmark_title` | Edit bookmark | 15, cd sao filled 05b `[SPEC]` |
| `move_to_folder` | Move to folder | 15 |
| `shortcut_menu_edit` | Edit shortcut | 15 |
| `tab_group_ungroup` | Ungroup | 15 |
| `site_info_connection_secure` | Connection is secure | 15 |
| `site_info_permissions` | Permissions | 15 |
| `site_info_skim_ai_summary` | Can read this site when you ask | 15 |
| `site_info_location` | Location | 15 |
| `site_permission_blocked` | Blocked | 15 |
| `site_info_cookies_summary` | Stored by this site on your phone | 15 |
| `snackbar_tab_closed` | Tab closed | 15 |
| `snackbar_bookmark_added` | Bookmark added | 15 |
| `snackbar_removed_from_history` | Removed from history | 15 |
| `snackbar_bookmark_deleted` | Bookmark deleted | 15 |
| `snackbar_opened_in_new_tab` | Opened in a new tab | 15 |
| `snackbar_import_failed` | Couldn't read that file. Choose an HTML bookmarks file exported from a browser. | 15 |
| `snackbar_downloaded` | %s downloaded | 15 |
| `snackbar_copied` | Copied to clipboard | 15 |
| `snackbar_shortcut_removed` | Shortcut removed | 15 `[SPEC]` |

Plurals (`<plurals>`):

| Tên | other (vẽ) | one | Board |
|---|---|---|---|
| `home_summarize_tabs` | Summarize my %d open tabs | suy ra: Summarize my %d open tab | 04, 06b, 04c |
| `time_hours_ago` | %d h ago | suy ra: %d h ago | 04 |
| `skim_history_questions_about` | %1$d questions about %2$s | vẽ: %1$d question about %2$s | 04c |
| `skim_history_answers_across_tabs` | suy ra: %1$d answers across %2$d tabs | vẽ: %1$d answer across %2$d tabs | 04c (2 số đếm, xem câu hỏi mở) |
| `ai_reading_tabs` | Reading your %d open tabs | suy ra | 06b |
| `tabs_title` | %d tabs | suy ra: %d tab | 09 |
| `incognito_tabs_title` | %d incognito tabs | suy ra: %d incognito tab | 09b |
| `cd_tabs_open` | Tabs, %d open | suy ra: Tabs, %d open | toolbar |
| `cd_incognito_tabs_open` | Incognito tabs, %d open | suy ra | 09c |
| `ai_settings_conversations` | %d conversations on this phone | suy ra: %d conversation on this phone | 10 |
| `bookmarks_count` | %d bookmarks | suy ra: %d bookmark | 14 |
| `bookmarks_imported_count_from` | %1$d bookmarks from %2$s | suy ra: %1$d bookmark from %2$s | 14 |
| `tab_group_close_tabs` | Close %d tabs | suy ra: Close %d tab | 15 |
| `snackbar_tabs_closed` | %d tabs closed | suy ra (đóng 1 tab dùng `snackbar_tab_closed`) | 15 |
| `snackbar_imported_bookmarks` | Imported %d bookmarks | suy ra: Imported %d bookmark | 15 |

### 3(b) Content description đã khử trùng

| Tên | Text (nguyên văn aria-label) | Control / board |
|---|---|---|
| `cd_browser_controls` | Browser controls | toolbar 04, 05, 05c, 06d, 08, 09c, 11 |
| `cd_back` | Back | toolbar; Back của title bar 04c, 10, 12, 13, 14 |
| `cd_forward` | Forward | toolbar |
| `cd_tabs_open` (plural) | Tabs, %d open | toolbar |
| `cd_incognito_tabs_open` (plural) | Incognito tabs, %d open | toolbar 09c |
| `cd_more_options` | More options | toolbar; pane 05b |
| `ask_skim` | Ask Skim | FAB 04; pane 06b |
| `cd_ask_skim_about_page` | Ask Skim about this page | FAB 05, 05c, 06d, 08 |
| `cd_ask_skim_unavailable` | Ask Skim about this page, unavailable until the page loads | FAB tắt 11 |
| `cd_ask_skim_off_incognito` | Ask Skim, off in incognito | FAB tắt 09c, nút AI 09b |
| `cd_ask_skim_about_tabs` | Ask Skim about these tabs | toolbar 09 |
| `app_name` | Skim | tile Sunny 04 (`role=img`) |
| `cd_voice_search` | Voice search | mic 04, 09c |
| `cd_close_search` | Close search | 04b |
| `cd_clear_text` | Clear text | 04b |
| `cd_suggestions` | Suggestions | nhóm 04b |
| `cd_edit_search` | Edit search: %s | mũi tên north_west 04b |
| `cd_search_skim_history` | Search Skim history | 04c |
| `cd_delete_all_skim_history` | Delete all Skim history | 04c |
| `cd_remove_from_skim_history` | Remove from Skim history | 04c |
| `cd_site_info_secure` | Site information, connection is secure | lock 05 |
| `cd_edit_address` | Edit address, %s | địa chỉ 05, 11 |
| `cd_reader_view` | Reader view | 05 |
| `cd_loading_page` | Loading page | wavy progress 05 |
| `cd_add_bookmark` | Add bookmark | sao 05b |
| `edit_bookmark_title` | Edit bookmark | sao filled 05b `[SPEC]` |
| `cd_share_page` | Share page | 05b |
| `cd_page_actions` | Page actions | nhóm 05b |
| `cd_previous_match` | Previous match | 05c |
| `cd_next_match` | Next match | 05c |
| `cd_close_find_in_page` | Close find in page | 05c |
| `cd_link_options` | Link options | pane 05d |
| `cd_image_options` | Image options | pane 05d |
| `ai_ask_about_page` | Ask about this page | pane 06, 06c |
| `cd_close` | Close | sheet 06, 06b, 06c |
| `cd_copy_summary` | Copy summary | 06 |
| `cd_good_summary` | Good summary | 06 |
| `cd_poor_summary` | Poor summary | 06 |
| `cd_copy_answer` | Copy answer | 06b |
| `cd_good_answer` | Good answer | 06b |
| `cd_poor_answer` | Poor answer | 06b |
| `cd_send` | Send | 06, 06b, 06c |
| `cd_stop` | Stop | 06c |
| `cd_writing_summary` | Writing summary | progress 06c |
| `cd_exit_reader_view` | Exit reader view | 08, 08b |
| `cd_text_size` | Text size | 08, 08b (toggle) |
| `cd_text_settings` | Text settings | pane 08b |
| `cd_search_tabs` | Search tabs | 09 |
| `cd_tab_list` | Tab list | connected group 09, 09b |
| `cd_group_options` | Group options | 09 |
| `cd_close_tab` | Close tab | 09, 09b |
| `cd_tab_actions` | Tab actions | toolbar 09, 09b |
| `new_incognito_tab` | New incognito tab | 09 toolbar, FAB 09b |
| `cd_close_all_tabs` | Close all tabs | 09 |
| `new_tab` | New tab | FAB 09 |
| `cd_search_incognito_tabs` | Search incognito tabs | 09b |
| `cd_close_all_incognito_tabs` | Close all incognito tabs | 09b |
| `cd_page_didnt_load` | Page didn't load | icon lỗi 11 |
| `cd_search_history` | Search history | 13 |
| `cd_remove_from_history` | Remove from history | 13 |
| `cd_search_bookmarks` | Search bookmarks | 14 |
| `cd_bookmark_options` | Bookmark options | 14, menu 15 |
| `cd_folder_field` | Folder, %s | Edit bookmark 15 |
| `cd_shortcut_menu` | %s shortcut | menu 15 |
| `cd_tab_group_menu` | %s group | menu 15 |
| `cd_delete_site_data` | Delete cookies and site data for %s | site info 15 |

Không phải cd của app: "Photo: a heat pump unit mounted outside a timber house in snow" (alt của trang web mẫu). Control không có aria-label nhưng có text hiển thị (không cần cd): shortcut, Add, hero card, segmented items, nút text.

### 3(c) Thực thể dữ liệu

| Entity | Trường (suy từ board) | Nguồn |
|---|---|---|
| `Tab` | id, url, title, host, faviconLetter (suy từ title/host), favicon?, isIncognito, groupId? (null → "Other"), orderInGroup, isCurrent (vòng primary), previewBitmap, hasSkimChat (badge Sunny), skimChatId?, loadState {Loading(progress) / Loaded / Error(type, errorCodeName)}, isSecure, readerAvailable, inReader, canGoBack, canGoForward, isNewTabPage, desktopSite, isBookmarked, openedByExternalApp (Back đóng tab), scrollPosition, savedWebViewState, lastAccessedAt | 04, 05, 05b, 08, 09, 09b, 11, navigation-map |
| `TabGroup` | id, title (AI đặt hoặc Rename), tabIds theo thứ tự, createdBySkim; `GroupingSnapshot` (thứ tự trước khi nhóm, cho Undo trên banner) | 09, 06b, 15 |
| `SkimChat` | id, kind {PAGE, TABS}, tabId?, url?, host?, title (tiêu đề trang hoặc prompt), tabCount (TABS), snippet (tóm tắt ngắn cho thẻ Continue), createdAt, updatedAt (→ "2 h ago", Today/Yesterday/Earlier this week), saved (theo keepChatHistory) | 04, 04c, 06, 06b, 10 |
| `SkimMessage` | id, chatId, role {USER, ASSISTANT}, kind {SUMMARY (bullets), ANSWER, TABS_OVERVIEW (sections: groupId, groupTitle, text)}, text, citations [paragraphIndex], feedback {NONE, GOOD, POOR}, status {STREAMING, COMPLETE, STOPPED, ERROR}, suggestedQuestions [String] (06b), createdAt | 06, 06b, 06c |
| Trạng thái AI (UI) | Generating, NotEnoughText, NoConnection, Ready; derived questionCount, hasSummary cho phụ đề 04c | 06c, 04c |
| `HistoryEntry` | id, url, title, host, visitedAt (nhóm theo ngày); không ghi incognito | 13, 04b |
| `Bookmark` | id, title, url, host, folderId, addedAt, favicon? | 14, 15, 05b |
| `BookmarkFolder` | id, name, parentId (giữ subfolder khi import), kind {MOBILE (mặc định), IMPORTED, USER?}, sourceFileName ("bookmarks.html"), bookmarkCount (derived) | 14, 15, 03 |
| `Shortcut` | id, name, url, position; letter + shape suy từ position (vòng 7 shape) | 04, 15 |
| `SitePermission` / `SiteSettings` | origin, skimAiAllowed (06d Allow lưu; switch trong site info), location {ASK, ALLOWED, BLOCKED}, camera, microphone (chưa vẽ), hasStoredData | 06d, 15, 10 |
| `SearchSuggestion` | type {ASK_SKIM, ENGINE, HISTORY, BOOKMARK}, text, url?, title? | 04b |
| `FindInPageState` | query, activeMatchOrdinal, numberOfMatches | 05c |
| `PageLoadError` | type {UNREACHABLE, NO_INTERNET, TIMED_OUT}, url, host, errorCodeName | 11 |
| `DownloadResult` | fileName, uri, mimeType | 15 |
| `ImportResult` | Success(count, folderId) / Failure | 03, 15 |
| `LinkContext` / `ImageContext` | link: url, anchorText; image: url, altText, previewBitmap | 05d |
| `ReaderArticle` | host, title, byline, paragraphs | 08 |
| `Settings` | searchEngine {GOOGLE, BING, DUCKDUCKGO, ECOSIA} (vẽ: Google); theme {SYSTEM, LIGHT, DARK} (vẽ: System default); askBeforeSending (vẽ: on); keepChatHistory (vẽ: on); readerTextScalePercent 80..200 bước 10 (vẽ: 110); readerFont {SANS_SERIF, SERIF} (vẽ: Sans serif); onboardingCompleted; isDefaultBrowser (derived từ RoleManager); lựa chọn Clear browsing data (vẽ: history ✓, cookies ✓, cache ✗) | 01, 03, 08b, 10, 12, 15 |
| Bộ đếm toolbar | openTabCount, incognitoTabCount | 04, 05, 09c |

### 3(d) Overlay trên board 15

| Overlay | Loại | Mở từ | Hành động | Kết quả |
|---|---|---|---|---|
| Add shortcut | Dialog, 2 OutlinedTextField (Name, URL; URL đang focus viền 2 `primary`) | Home › Add (04); Edit shortcut (15) mở cùng dialog đã điền | Cancel, Save | Lưu shortcut |
| Search engine | Dialog radio (4 item 48) | Settings › Search engine (12) | Chọn = lưu + đóng; Cancel | Cập nhật engine |
| Theme | Dialog radio (3) | Settings › Theme (12) | Chọn áp dụng ngay; Cancel | Cập nhật theme |
| Clear browsing data | Dialog checkbox (3) + footnote | Settings (12), History (13) | Cancel, Clear (tắt khi không chọn gì) | Xóa dữ liệu đã chọn |
| Delete all AI data? | AlertDialog có icon `ic_delete` `error`, tiêu đề căn giữa | AI & privacy (10) | Cancel, Delete (`error`) | Xóa Skim history + mọi quyền AI của site |
| Clear Skim history? | AlertDialog không icon | Skim history › Delete all (04c) | Cancel, Clear | Xóa Skim history, giữ quyền site |
| Site permission | AlertDialog icon `ic_location_on` `primary` | Trang web xin location/camera/mic | Block, Allow | Allow → prompt runtime của Android nếu thiếu |
| Rename group | Dialog 1 field (focus) | Group options › Rename group (09) | Cancel, Save (tắt khi rỗng) | Đổi tên TabGroup |
| Edit bookmark | Dialog 2 field + dropdown Folder (`ic_arrow_drop_down`) | Bookmark options › Edit (14); action Edit của snackbar Bookmark added; Edit bookmark trong 05b | Cancel, Save; Folder → Move to folder | Cập nhật bookmark |
| Move to folder | Dialog radio (Mobile bookmarks, Imported) | Bookmark options › Move to folder (14); field Folder | Chọn; Cancel | Đổi folder |
| Shortcut menu | Menu 2 nhóm, neo dưới tile (tile chuyển `secondaryContainer`) | Long-press shortcut (04) | Edit shortcut; Remove (nhóm riêng) | Remove → snackbar "Shortcut removed" + Undo `[SPEC]` |
| Tab group menu | Menu 2 nhóm, neo ở nút Group options (nút có nền pressed `onSurfaceVariant` 12%) | Group options (09) | Rename group; Ungroup; Close 4 tabs (nhóm riêng) | Close → snackbar "N tabs closed" + Undo |
| Bookmark menu | Menu 2 nhóm | Bookmark options (14) | Edit; Move to folder; Delete (nhóm riêng) | Delete → snackbar "Bookmark deleted" + Undo |
| Site information sheet | Bottom sheet: header (favicon 40, host, `ic_lock` 16 + "Connection is secure"), section Permissions, 3 item 72 (Skim AI + Switch on; Location + Switch off; Cookies + nút xóa 48) | Lock trên address bar (05) | Toggle Skim AI, toggle Location, xóa cookie | Chỉ hiện quyền site đã xin |
| Snackbar Tab closed | Undo, 10 s | Close tab / vuốt (09, 09b) | Undo | Khôi phục tab |
| Snackbar N tabs closed | Undo, 10 s | Close all tabs, đóng nhóm (09) | Undo | Khôi phục |
| Snackbar Bookmark added | Edit, 10 s | Add bookmark (05b) | Edit → Edit bookmark | |
| Snackbar Removed from history | Undo, 10 s | Remove ở 13 hoặc 04c | Undo | |
| Snackbar Bookmark deleted | Undo, 10 s | Bookmark options › Delete (14) | Undo | |
| Snackbar Opened in a new tab | Switch, 10 s | Open (image) in new tab (05d) | Switch → chuyển sang tab mới | |
| Snackbar Imported N bookmarks | View, 10 s | Chọn file ở 03 | View → 14 (folder Imported?) | |
| Snackbar import lỗi | Không action, 4 s, `role=alert`, 2 dòng | File không phải bookmarks export | — | |
| Snackbar %s downloaded | Open, 10 s | DownloadManager xong | Open file | |
| Snackbar Copied to clipboard | Không action, 4 s | Copy link (05d), copy summary/answer (06, 06b), chỉ Android ≤ 12 | — | |

Overlay ngoài board 15: 05b More menu, 05d Link/Image options, 06, 06b, 06c (sheet), 06d (dialog), 08b (sheet không scrim).

### 3(e) Mâu thuẫn spec ↔ HTML (bên thắng theo README: bản vẽ thắng default component)

| # | Mâu thuẫn | Bên thắng |
|---|---|---|
| 1 | AI FAB tắt (09c, 11) và nút AI tắt (09b) vẽ `ic_auto_awesome` outline; components.md chỉ nói "filled auto_awesome" cho FAB | HTML: tắt = outline |
| 2 | Wavy progress 06c: track `tertiaryContainer`; components.md: track `secondaryContainer` | HTML cho 06c; 05 giữ `secondaryContainer` |
| 3 | 09c search bar: `surfaceContainerHighest` (dark), leading `ic_visibility_off`, placeholder "Search or type URL"; components.md: "same bar with the dark scheme" (search icon, `surfaceContainerHigh`) | HTML |
| 4 | Tab card dark (09b) `surfaceContainer`; components.md: `surfaceContainerLow` | HTML |
| 5 | 09c list: item `dk:surfaceContainer` trên `dk:surface`, cao 48, không tương tác; components.md segmented: `surfaceContainerLowest` trên `surfaceContainerLow`, 56 | HTML |
| 6 | components.md "Destructive confirm buttons use error", nhưng "Clear" (Clear browsing data, Clear Skim history) vẽ `primary`; chỉ "Delete" (Delete all AI data) là `error`. Menu item Remove/Delete/Close 4 tabs vẽ `onSurface` | HTML (chỉ Delete đỏ) |
| 7 | Address bar trên 11: icon lỗi không phải nút, không có Reader view; components.md: "Site information (lock, or error on 11)" ngụ ý là nút | HTML: ảnh tĩnh `cd_page_didnt_load` |
| 8 | components.md "Chat: answers … then Copy and feedback"; 06 chỉ có Copy/feedback trên card Summary, câu trả lời Q&A không có | HTML (cần xác nhận, xem câu hỏi) |
| 9 | Floating toolbar: components.md liệt kê 04, 05, 05c, 08, 09c, 11; navigation-map thêm 06d; HTML 06d có toolbar dưới scrim | navigation-map + HTML |
| 10 | README "Not designed yet: … import failed snackbars", nhưng 15 có snackbar "Couldn't read that file…" cho file không phải bookmarks export | HTML (README có thể lỗi thời hoặc "import failed" chỉ lỗi khác như I/O) |
| 11 | Tokens: tab count labelMediumEmphasized 600; HTML 12/12 700 | Ưu tiên token (khác biệt nhỏ); ghi nhận |
| 12 | Tokens: AI labels titleSmallEmphasized (600); 04b "Ask Skim" 14/20 500 `tertiary` | HTML |
| 13 | Tokens: shortcut shapes Cookie12Sided, Clover4Leaf, SoftBurst, Flower, Pentagon; HTML 04 còn dùng Cookie9Sided (Wikipedia) và Cookie4Sided (Hacker News). Welcome art dùng Cookie9Sided ngoài Clover4Leaf | HTML |
| 14 | Elevation: tokens nói floating toolbar Level 1; HTML vẽ shadow mức 3 cho toolbar, FAB, dialog, snackbar | tokens.md bảo giữ default Compose → theo tokens |
| 15 | Casing: 01 "Terms of Service", "Privacy Policy"; 12 "Terms of service", "Privacy policy" | Giữ đúng từng board (2 cặp string riêng) |
| 16 | README "Touch targets are 48 dp or more"; HTML vẽ Close tab 36, Group options 40, copy/feedback 40, snackbar action 36 cao | Giữ kích thước hình ảnh, đảm bảo vùng chạm 48 bằng `minimumInteractiveComponentSize` |
| 17 | components.md Cards: radius 20 thường, 28 cho hero; 03 dùng 28 cho card thường | HTML |
| 18 | Logo 01 Sunny dùng `primary`; README "Skim AI is tertiary … Sunny shape, and nothing else does" | HTML (logo thương hiệu ≠ AI surface) |
| 19 | dialogs.md mô tả Site permission cho "location, camera or mic", chỉ có copy location | Thiếu copy camera/mic |

## 4. Kiểm tra icon

| Kết quả | Chi tiết |
|---|---|
| Số file | 68 file `ic_*.xml` = 63 tên + 5 biến thể filled (auto_awesome, folder, format_size, star, stop); khớp tokens.json |
| Icon dùng trong board 01–15 nhưng thiếu file | Không có |
| File không được dùng ở board nào | `ic_folder` (outline), `ic_star_filled` (spec: sao filled khi đã bookmark ở 05b → vẫn cần), `ic_stop` (outline; 06c dùng `ic_stop_filled`) |
| Icon chỉ có trên board tài liệu 16–18 (không có file, không cần cho app) | accessibility_new, android, animation, category, dark_mode, deployed_code, dock_to_bottom, fullscreen, interests, more_horiz, restore, style, swipe_down, text_fields, text_snippet, touch_app, tune, undo, view_agenda, web |
| Biến thể đặc biệt | `ic_auto_awesome` outline chỉ dùng cho trạng thái AI tắt (09b, 09c, 11); `ic_format_size` → `ic_format_size_filled` khi toggle bật (08 → 08b) |

Icon theo board (tóm tắt): 01 article, auto_awesome_filled, travel_explore · 03 public, bookmarks, verified_user · 04 search, mic, auto_awesome_filled, arrow_forward, add, arrow_back, more_vert · 04b arrow_back, close, auto_awesome_filled, search, north_west, history · 04c arrow_back, search, delete_sweep, close, auto_awesome_filled, verified_user · 05 lock, chrome_reader_mode, arrow_back, arrow_forward, more_vert, auto_awesome_filled · 05b star, share, find_in_page, desktop_windows, tab, visibility_off, bookmarks, history, settings (+ lock, chrome_reader_mode) · 05c keyboard_arrow_up, keyboard_arrow_down, close + toolbar · 05d link, tab, visibility_off, content_copy, share / image, download · 06 auto_awesome_filled, close, content_copy, thumb_up, thumb_down, format_quote, arrow_upward · 06b + chevron_right, search · 06c stop_filled, info, wifi_off · 06d auto_awesome_filled + toolbar · 08 close, format_size + toolbar · 08b close, format_size_filled, check · 09 search, check, auto_awesome_filled, more_vert, close, visibility_off, delete, add · 09b search, check, visibility_off, close, auto_awesome, delete, add · 09c visibility_off, mic, history, cookie, keyboard, public, work, router, auto_awesome + toolbar · 10 arrow_back, cloud_upload, history, check, auto_awesome_filled, delete, info · 11 error, public_off / wifi_off + wifi / schedule, refresh, auto_awesome + toolbar · 12 arrow_back, search, public, palette, auto_awesome_filled, delete, shield, description, code, info · 13 arrow_back, search, delete, close · 14 arrow_back, search, folder_filled, more_vert · 15 arrow_drop_down, check, delete, location_on, edit, drive_file_rename_outline, layers_clear, close, drive_file_move, lock, auto_awesome_filled, cookie, more_vert.

## 5. Câu hỏi chưa giải quyết

1. Chip "Key numbers", "Explain simply", "The catch?" (06) là chip cố định (string) hay do AI sinh như chip 06b?
2. "Other" (nhóm tab lẻ ở 09/06b) có phải string chrome cố định không?
3. `one` của mọi plural chưa được vẽ (trừ `skim_history_questions_about` và `skim_history_answers_across_tabs` one): cần copywriter duyệt. "1 answer across 7 tabs" có 2 số đếm: chấp nhận plural theo số answer, số tab luôn ≥ 2?
4. Thời gian tương đối: chỉ có "2 h ago" và "Yesterday"; phút / ngày / ngày cụ thể chưa có copy.
5. Câu trả lời Q&A trong 06 có cần Copy/Good/Poor như components.md nói không?
6. Tiêu đề dialog khi sửa shortcut ("Edit shortcut" filled in): dùng lại "Add shortcut" hay "Edit shortcut"?
7. Phụ đề 12 khi Skim chưa là default browser, và phụ đề AI & privacy khi tắt "Ask before…" chưa có copy.
8. Copy Site permission cho camera và mic chưa có; trạng thái "Allowed" của Location trong site info chưa có copy (chỉ "Blocked").
9. 05b mở từ Home "without the page actions": ẩn cả header (title, sao, share) hay chỉ hàng Find/Desktop site?
10. Switch Skim AI tắt trong site info: site bị chặn vĩnh viễn hay 06d sẽ hỏi lại?
11. Mặc định reader text size (board vẽ 110%) và lựa chọn mặc định Clear browsing data (vẽ: history + cookies) có phải giá trị mặc định thật không?
12. Snackbar "Imported 24 bookmarks" › View mở 14 gốc hay folder Imported (chưa thiết kế folder đã mở)?
