package com.dantech.skimbrowser.testing

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File

// Unit tests run with the module directory (app/) as their working directory.
private const val DESIGN_SCREENS = "../design/screens"
private const val BOARD_GOLDENS = "src/test/screenshots/boards"
private const val COMPONENT_GOLDENS = "src/test/screenshots/components"

/**
 * Captures this node as the golden of a component: `app/src/test/screenshots/components/<name>.png`.
 * Component tests lay the component out where its board draws it, so the golden and a crop of the
 * board PNG share coordinates.
 */
fun SemanticsNodeInteraction.captureComponent(name: String) {
    captureRoboImage("$COMPONENT_GOLDENS/$name.png")
}

/**
 * Captures the whole screen, every window included (dialogs and sheets as well as the activity),
 * as the golden of a board: `app/src/test/screenshots/boards/<name>.png`.
 *
 * [name] is the name of the board PNG in `design/screens/` the capture stands for, for example
 * `05-browsing--loading`, so the golden and the design pair up by name.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun captureBoard(name: String) {
    captureScreenRoboImage(boardGoldenPath(name))
}

/** The golden path of board [name]; fails when `design/screens/` has no PNG of that name. */
internal fun boardGoldenPath(name: String): String {
    require(name.isNotBlank() && '/' !in name && '\\' !in name) { "Board name \"$name\" is not a file name." }
    require(File(DESIGN_SCREENS, "$name.png").isFile) {
        "design/screens/$name.png does not exist: name a board capture after the board PNG it stands for."
    }
    return "$BOARD_GOLDENS/$name.png"
}
