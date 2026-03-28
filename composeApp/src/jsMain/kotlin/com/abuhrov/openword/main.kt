import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.abuhrov.openword.App
import kotlinx.browser.document
import org.jetbrains.skiko.wasm.onWasmReady

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    onWasmReady {
        val body = document.body ?: return@onWasmReady
        ComposeViewport(body) {
            App()
        }
        val loader = document.getElementById("loading-screen")
        if (loader != null) {
            loader.asDynamic().style.opacity = "0"
            loader.asDynamic().style.pointerEvents = "none"
            kotlinx.browser.window.setTimeout({ loader.remove() }, 500)
        }
    }
}