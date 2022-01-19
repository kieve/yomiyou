package ca.kieve.yomiyou

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ca.kieve.yomiyou.data.AppContainer
import ca.kieve.yomiyou.ui.theme.YomiyouTheme
import ca.kieve.yomiyou.util.getTag

class YomiActivity : AppCompatActivity() {
    companion object {
        private val TAG = getTag()
    }

    private val viewModel by viewModels<YomiViewModel>()
    private lateinit var container: AppContainer

    private val debugFileLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.d(TAG, "Failed to register debug file directory")
            return@registerForActivityResult
        }

        result.data?.data.also { uri ->
            uri ?: return@also
            container.files.registerDebugFiles(uri)
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        container = (application as YomiApplication).container
        container.activity = this

        val persistedPerms = contentResolver.persistedUriPermissions
        if (persistedPerms.isEmpty()) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            debugFileLauncher.launch(intent)
        } else {
            container.files.registerDebugFiles(persistedPerms[0].uri)
        }

        setContent {
            MyApp {
                YomiNavigation(appContainer = container)
            }
        }
    }
}

@Composable
fun MyApp(content: @Composable () -> Unit) {
    YomiyouTheme {
        Surface(
            color = MaterialTheme.colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight())
        {
            content()
        }
    }
}
