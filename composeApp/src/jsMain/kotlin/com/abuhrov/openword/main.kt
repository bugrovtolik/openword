import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.abuhrov.openword.App
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.jetbrains.skiko.wasm.onWasmReady

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Read persisted scroll position early, before Compose initializes
    val savedScrollPos = try {
        localStorage.getItem("scroll_position")?.toIntOrNull() ?: 0
    } catch (_: Throwable) { 0 }

    onWasmReady {
        val body = document.body ?: return@onWasmReady
        ComposeViewport(body) {
            App(initialScrollIndex = savedScrollPos)
        }
        // Fade out and remove the app shell
        val shell = document.getElementById("app-shell")
        if (shell != null) {
            shell.asDynamic().style.opacity = "0"
            shell.asDynamic().style.pointerEvents = "none"
            window.setTimeout({ shell.remove() }, 400)
        }
    }
}