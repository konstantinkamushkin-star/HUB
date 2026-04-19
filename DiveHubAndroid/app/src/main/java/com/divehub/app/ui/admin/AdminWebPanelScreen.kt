package com.divehub.app.ui.admin

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Base64
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.divehub.app.AppGraph
import com.divehub.app.R
import com.divehub.app.data.remote.dto.UserDto
import com.divehub.app.util.mediaOriginBaseUrl
import org.json.JSONObject
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminWebPanelRoute(
    graph: AppGraph,
    innerNav: NavController,
    user: UserDto?,
) {
    var reloadTick by remember { mutableIntStateOf(0) }
    var lastError by remember { mutableStateOf<String?>(null) }

    val accessToken by produceState<String?>(initialValue = null, reloadTick) {
        value = graph.tokenStore.getAccessToken()
    }
    val refreshToken by produceState<String?>(initialValue = null, reloadTick) {
        value = graph.tokenStore.getRefreshToken()
    }
    val apiBase by produceState<String?>(initialValue = null, reloadTick) {
        value = graph.tokenStore.getRootBaseUrl()
    }

    val adminWebBase = remember(apiBase) {
        deriveAdminWebBaseUrl(apiBase.orEmpty())
    }
    val dashboardUrl = remember(adminWebBase) { "$adminWebBase/dashboard" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_web_panel_title)) },
                navigationIcon = {
                    IconButton(onClick = { innerNav.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { reloadTick += 1 }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
                    }
                },
            )
        },
    ) { padding ->
        val token = accessToken?.trim().orEmpty()
        if (token.isBlank() || user == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.admin_web_panel_no_session),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.admin_web_panel_no_session_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        val payloadB64 = remember(token, refreshToken, user, dashboardUrl) {
            sessionBridgePayloadBase64(
                accessToken = token,
                refreshToken = refreshToken?.trim().orEmpty().takeIf { it.isNotBlank() },
                user = user,
                dashboardUrl = dashboardUrl,
            )
        }
        if (payloadB64 == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.common_error),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AdminWebView(
                adminWebBase = adminWebBase,
                payloadBase64 = payloadB64,
                reloadTick = reloadTick,
                onError = { lastError = it },
            )
            if (!lastError.isNullOrBlank()) {
                Text(
                    text = lastError ?: "",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AdminWebView(
    adminWebBase: String,
    payloadBase64: String,
    reloadTick: Int,
    onError: (String?) -> Unit,
) {
    val loadingHtml = remember(payloadBase64) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1"/>
        </head>
        <body style="margin:0;font-family:sans-serif;background:#09090b;color:#a1a1aa;padding:20px;">
          Loading admin panel…
        </body>
        <script>
        (function(){
          try {
            var p = JSON.parse(atob('$payloadBase64'));
            localStorage.setItem('divehub_admin_token', p.accessToken);
            if (p.refreshToken) { localStorage.setItem('divehub_admin_refresh', p.refreshToken); }
            else { localStorage.removeItem('divehub_admin_refresh'); }
            localStorage.setItem('divehub_admin_user', JSON.stringify(p.user));
            window.location.replace(p.redirect);
          } catch (e) {
            document.body.innerHTML = '<pre style="color:#f87171;white-space:pre-wrap">' + String(e) + '</pre>';
          }
        })();
        </script>
        </html>
        """.trimIndent()
    }
    val loadSignature = remember(payloadBase64, adminWebBase, reloadTick) {
        "$adminWebBase|$reloadTick|$payloadBase64"
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.parseColor("#09090b"))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            onError(error?.description?.toString())
                        }
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != loadSignature) {
                webView.tag = loadSignature
                onError(null)
                webView.loadDataWithBaseURL(adminWebBase, loadingHtml, "text/html", "utf-8", null)
            }
        },
    )
}

private fun sessionBridgePayloadBase64(
    accessToken: String,
    refreshToken: String?,
    user: UserDto,
    dashboardUrl: String,
): String? = try {
    val userObj = JSONObject().apply {
        put("id", user.id)
        put("email", user.email)
        put("role", user.role.orEmpty())
        if (!user.firstName.isNullOrBlank()) put("firstName", user.firstName)
        if (!user.lastName.isNullOrBlank()) put("lastName", user.lastName)
    }
    val root = JSONObject().apply {
        put("accessToken", accessToken)
        if (!refreshToken.isNullOrBlank()) put("refreshToken", refreshToken)
        put("user", userObj)
        put("redirect", dashboardUrl)
    }
    Base64.encodeToString(root.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
} catch (_: Exception) {
    null
}

private fun deriveAdminWebBaseUrl(apiBase: String): String {
    val normalized = mediaOriginBaseUrl(apiBase).trim().trimEnd('/')
    if (normalized.isBlank()) return "https://dive-hub.ru"
    val lower = normalized.lowercase(Locale.ROOT)
    return when {
        "://api." in lower -> normalized.replace("://api.", "://", ignoreCase = true)
        "127.0.0.1" in lower || "localhost" in lower || "10.0.2.2" in lower -> {
            if (normalized.endsWith(":3000")) normalized.dropLast(5) + ":3001" else "http://10.0.2.2:3001"
        }
        else -> "https://dive-hub.ru"
    }
}
