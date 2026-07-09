package com.marinov.cainiaotracker.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.marinov.cainiaotracker.R
import com.marinov.cainiaotracker.data.FetchResult
import com.marinov.cainiaotracker.data.GetTrackData
import com.marinov.cainiaotracker.data.TrackingCacheManager
import com.marinov.cainiaotracker.data.TrackingInfo
import com.marinov.cainiaotracker.databinding.ActivityPackageBinding
import com.marinov.cainiaotracker.util.ActiveViewingManager
import com.marinov.cainiaotracker.util.NetworkUtils
import kotlinx.coroutines.launch

class PackageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPackageBinding
    private lateinit var adapter: TimelineAdapter
    private var trackingCode: String = ""
    private var captchaWebView: WebView? = null

    private enum class ViewState {
        LOADING, ERROR, OFFLINE, SUCCESS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPackageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.package_details_title)
        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        trackingCode = intent.getStringExtra("trackingCode") ?: run {
            finish()
            return
        }

        ActiveViewingManager.setViewing(trackingCode)

        adapter = TimelineAdapter()
        binding.recyclerViewTimeline.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewTimeline.adapter = adapter

        binding.btnRetryError.setOnClickListener { fetchTracking() }
        binding.btnRetryOffline.setOnClickListener { fetchTracking() }

        fetchTracking()
    }

    private fun fetchTracking() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showState(ViewState.OFFLINE)
            return
        }

        showState(ViewState.LOADING)

        lifecycleScope.launch {
            val result = GetTrackData.fetchTrackingInfo(this@PackageActivity, trackingCode)
            when (result) {
                is FetchResult.Success -> {
                    TrackingCacheManager.saveTrackingInfo(this@PackageActivity, trackingCode, result.info)
                    displayTrackingInfo(result.info)
                    showState(ViewState.SUCCESS)
                }
                is FetchResult.CaptchaRequired -> {
                    // Exibe o WebView APENAS se a PackageActivity estiver aberta.
                    showCaptchaOverlay(result.webView)
                }
                is FetchResult.Error -> {
                    showState(ViewState.ERROR)
                }
            }
        }
    }

    private fun displayTrackingInfo(info: TrackingInfo) {
        binding.tvStatusValue.text = info.status
        binding.tvOriginValue.text = info.origin
        binding.tvDestinationValue.text = info.destination
        adapter.submitList(info.timeline)
    }

    private fun showCaptchaOverlay(webView: WebView) {
        captchaWebView = webView

        (webView.parent as? ViewGroup)?.removeView(webView)

        binding.layoutCaptcha.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        binding.layoutCaptcha.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutOffline.visibility = View.GONE
        binding.layoutContent.visibility = View.GONE

        // Interface JS para receber o callback quando os elementos do captcha sumirem
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onCaptchaResolved() {
                runOnUiThread {
                    // 1. Força salvamento dos cookies do captcha agora resolvido
                    CookieManager.getInstance().flush()

                    // 2. Some com o WebView imediatamente
                    hideCaptchaOverlay()

                    // 3. Faz uma nova requisição para pegar os dados com o cookie já autenticado
                    fetchTracking()
                }
            }

            @JavascriptInterface
            fun onTimeout() {
                runOnUiThread {
                    hideCaptchaOverlay()
                    showState(ViewState.ERROR)
                }
            }
        }, "AndroidInterfaceCaptcha")

        // NOVO: Adiciona um WebViewClient dedicado ao monitoramento de captcha.
        // Se o captcha recarregar a página pós-sucesso (redirecionamento), reinjetamos o script.
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(GetTrackData.buildMonitoringJs(), null)
            }
        }

        // Injeta o script de monitoramento contínuo na página atual de imediato
        webView.evaluateJavascript(GetTrackData.buildMonitoringJs(), null)
    }

    private fun hideCaptchaOverlay() {
        binding.layoutCaptcha.visibility = View.GONE

        captchaWebView?.let { webView ->
            // Remove the WebViewClient to prevent memory leaks or unwanted calls
            webView.webViewClient = android.webkit.WebViewClient()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
        captchaWebView = null
    }

    private fun showState(state: ViewState) {
        binding.progressBar.visibility = if (state == ViewState.LOADING) View.VISIBLE else View.GONE
        binding.layoutError.visibility = if (state == ViewState.ERROR) View.VISIBLE else View.GONE
        binding.layoutOffline.visibility = if (state == ViewState.OFFLINE) View.VISIBLE else View.GONE
        binding.layoutContent.visibility = if (state == ViewState.SUCCESS) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        ActiveViewingManager.clearViewing()
        hideCaptchaOverlay()
    }
}