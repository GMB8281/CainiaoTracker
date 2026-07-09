package com.marinov.cainiaotracker.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

sealed class FetchResult {
    data class Success(val info: TrackingInfo) : FetchResult()
    data class CaptchaRequired(val webView: WebView) : FetchResult()
    object Error : FetchResult()
}

object GetTrackData {

    private const val CAINIAO_URL_TEMPLATE =
        "https://global.cainiao.com/newDetail.htm?mailNoList=%s&otherMailNoList="

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchTrackingInfo(context: Context, trackingCode: String): FetchResult {
        return withTimeoutOrNull(45.seconds) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<FetchResult> { continuation ->
                    val webView = WebView(context)
                    setupWebViewForDetection(webView, continuation)

                    webView.loadUrl(String.format(CAINIAO_URL_TEMPLATE, trackingCode))

                    continuation.invokeOnCancellation {
                        webView.post {
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }
                }
            }
        } ?: FetchResult.Error
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewForDetection(
        webView: WebView,
        continuation: CancellableContinuation<FetchResult>
    ) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

        // Cookies persistentes
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        var isResumed = false

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onDataExtracted(json: String) {
                if (isResumed) return
                isResumed = true
                try {
                    // Força a gravação dos cookies válidos
                    CookieManager.getInstance().flush()

                    val info = parseTrackingJson(json)
                    if (continuation.isActive) continuation.resume(FetchResult.Success(info))
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (continuation.isActive) continuation.resume(FetchResult.Error)
                } finally {
                    webView.post {
                        webView.stopLoading()
                        webView.destroy()
                    }
                }
            }

            @JavascriptInterface
            fun onCaptchaDetected() {
                if (isResumed) return
                isResumed = true
                if (continuation.isActive) continuation.resume(FetchResult.CaptchaRequired(webView))
            }

            @JavascriptInterface
            fun onError(msg: String) {
                if (isResumed) return
                isResumed = true
                if (continuation.isActive) continuation.resume(FetchResult.Error)
                webView.post {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
        }, "AndroidInterface")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Injeta o verificador após a página carregar
                view?.evaluateJavascript(buildDetectionJs(), null)
            }
        }
    }

    fun parseTrackingJson(json: String): TrackingInfo {
        val obj = JSONObject(json)
        val status = obj.optString("status", "Desconhecido")
        val origin = obj.optString("origin", "Desconhecido")
        val destination = obj.optString("destination", "Desconhecido")
        val timelineArray = obj.getJSONArray("timeline")
        val timeline = mutableListOf<TimelineEvent>()
        for (i in 0 until timelineArray.length()) {
            val item = timelineArray.getJSONObject(i)
            timeline.add(
                TimelineEvent(
                    title = item.getString("title"),
                    description = item.optString("description", ""),
                    time = item.getString("time")
                )
            )
        }
        return TrackingInfo(status, origin, destination, timeline)
    }

    // -------------------------------------------------------------------------
    // JS de detecção inicial
    // -------------------------------------------------------------------------
    private fun buildDetectionJs(): String {
        return """
            (function checkState() {
                var warningEl = document.querySelector('.warnning-text');
                var punishEl = document.querySelector('#baxia-punish');
                var noCaptchaEl = document.getElementById('nocaptcha');
                var hasCaptcha = false;

                if (punishEl) hasCaptcha = true;
                if (noCaptchaEl) hasCaptcha = true;

                if (warningEl) {
                    var text = (warningEl.innerText || warningEl.textContent || '').toLowerCase();
                    if (text.indexOf('unusual traffic') !== -1 || text.indexOf('sorry') !== -1) {
                        hasCaptcha = true;
                    }
                }

                if (hasCaptcha) {
                    try { AndroidInterface.onCaptchaDetected(); } catch(e) {}
                    return;
                }

                var el = document.querySelector('.Tracking--cardWrapper--2SqX2e3');
                if (el) {
                    try {
                        ${buildExtractionCode()}
                        AndroidInterface.onDataExtracted(JSON.stringify(result));
                    } catch(e) {
                        AndroidInterface.onError(e.message);
                    }
                } else {
                    setTimeout(checkState, 1000);
                }
            })();
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // JS de monitoramento: Usando setInterval para checar quando as divs sumirem
    // -------------------------------------------------------------------------
    fun buildMonitoringJs(): String {
        return """
            (function monitorCaptchaResolution() {
                // Evita múltiplos intervalos rodando simultaneamente
                if (window.captchaMonitorInterval) {
                    clearInterval(window.captchaMonitorInterval);
                }

                var maxAttempts = 600; // ~10 minutos
                var attempts = 0;

                window.captchaMonitorInterval = setInterval(function() {
                    attempts++;
                    if (attempts > maxAttempts) {
                        clearInterval(window.captchaMonitorInterval);
                        try { AndroidInterfaceCaptcha.onTimeout(); } catch(e) {}
                        return;
                    }

                    var warningEl = document.querySelector('.warnning-text');
                    var punishEl = document.querySelector('#baxia-punish');
                    var noCaptchaEl = document.getElementById('nocaptcha'); // O slider do HTML enviado
                    var hasCaptcha = false;

                    // Verifica a existência e se estão visíveis na tela (offsetWidth > 0)
                    if (punishEl && punishEl.offsetWidth > 0) hasCaptcha = true;
                    if (noCaptchaEl && noCaptchaEl.offsetWidth > 0) hasCaptcha = true;

                    if (warningEl && warningEl.offsetWidth > 0) {
                        var text = (warningEl.innerText || warningEl.textContent || '').toLowerCase();
                        if (text.indexOf('unusual traffic') !== -1 || text.indexOf('sorry') !== -1) {
                            hasCaptcha = true;
                        }
                    }

                    // Se NENHUM dos elementos do captcha for encontrado e visível, ele sumiu!
                    if (!hasCaptcha) {
                        clearInterval(window.captchaMonitorInterval);
                        try { AndroidInterfaceCaptcha.onCaptchaResolved(); } catch(e) {}
                    }
                }, 1000);
            })();
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // Código JS de extração
    // -------------------------------------------------------------------------
    private fun buildExtractionCode(): String {
        return """
            var statusEl = document.querySelector('.Tracking--orderContent--1xRvpXS .Tracking--orderCode--2xCNBKX > span');
            var status = statusEl ? statusEl.innerText.split('(')[0].trim() : 'Desconhecido';
            var origin = 'Desconhecido', destination = 'Desconhecido';
            var infoBlocks = document.querySelectorAll('.Tracking--cpInfo--rTYSWiR');
            infoBlocks.forEach(function(block) {
                var label = block.querySelector('.Tracking--cpInfoDesc--3aUpHxd');
                var country = block.querySelector('.Tracking--countryName--2W4Vmi1');
                if (label && country) {
                    var l = label.innerText.toLowerCase();
                    if (l.indexOf('origin') !== -1) origin = country.innerText;
                    else if (l.indexOf('destination') !== -1) destination = country.innerText;
                }
            });
            var timeline = [];
            var steps = document.querySelectorAll('.TrackingDetail--step--2sEzXUy');
            steps.forEach(function(step) {
                var head = step.querySelector('.TrackingDetail--head--20GpNSP');
                var title = head ? head.innerText : '';
                var timeEl = step.querySelector('.TransitCard--cpTextTime--2wv0N5L') || step.querySelector('.TrackingDetail--timeText--3x08R3x');
                var time = timeEl ? timeEl.innerText : '';
                var descEl = step.querySelector('.TrackingDetail--text--3Odqdxz');
                var desc = descEl ? descEl.innerText : '';
                if (title) {
                    timeline.push({title: title, description: desc, time: time});
                }
            });
            var result = {
                status: status,
                origin: origin,
                destination: destination,
                timeline: timeline
            };
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // Verificação de atualização (BackgroundService)
    // -------------------------------------------------------------------------
    suspend fun isAnyUpdate(context: Context, trackingCode: String): UpdateResult {
        val cachedInfo = TrackingCacheManager.loadTrackingInfo(context, trackingCode)
        val result = fetchTrackingInfo(context, trackingCode)

        val newInfo = when (result) {
            is FetchResult.Success -> result.info
            is FetchResult.CaptchaRequired -> {
                // Se der captcha em background, destruímos o webview e ignoramos silenciosamente
                result.webView.post {
                    result.webView.stopLoading()
                    result.webView.destroy()
                }
                null
            }
            is FetchResult.Error -> null
        }

        if (newInfo == null || newInfo.timeline.isEmpty()) {
            return UpdateResult(false, "")
        }

        var hasUpdate = false
        var latestTitle = ""

        if (cachedInfo == null || cachedInfo.timeline.isEmpty()) {
            hasUpdate = false
        } else {
            val newLatest = newInfo.timeline.firstOrNull()
            val cachedLatest = cachedInfo.timeline.firstOrNull()
            if (newLatest != null && cachedLatest != null) {
                if (newLatest.time != cachedLatest.time || newLatest.title != cachedLatest.title) {
                    hasUpdate = true
                    latestTitle = newLatest.title
                }
            }
        }

        TrackingCacheManager.saveTrackingInfo(context, trackingCode, newInfo)
        return UpdateResult(hasUpdate, latestTitle)
    }
}