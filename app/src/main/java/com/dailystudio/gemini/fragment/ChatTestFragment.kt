package com.dailystudio.gemini.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.dailystudio.gemini.R
import com.dailystudio.gemini.core.AppSettingsPrefs
import com.dailystudio.gemini.core.R as coreR
import com.dailystudio.gemini.core.model.AIEngine
import com.dailystudio.gemini.core.model.ResumeViewModel
import com.dailystudio.gemini.core.model.UiStatus
import com.dailystudio.gemini.utils.CustomFontTagHandler
import com.dailystudio.gemini.utils.DotsLoader
import com.dailystudio.gemini.utils.TimeStats
import com.dailystudio.gemini.utils.UiHelper
import com.dailystudio.gemini.utils.registerActionBar
import com.dailystudio.devbricksx.development.Logger
import com.dailystudio.devbricksx.fragment.AbsPermissionsFragment
import com.dailystudio.devbricksx.utils.ResourcesCompatUtils
import com.dailystudio.gemini.core.getAIEngine
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class ChatTestFragment: AbsPermissionsFragment() {

    private var userInput: TextView? = null
    private var resultsView: TextView? = null
    private var scrollView: ScrollView? = null
    private var sendButton: View? = null
    private var pickButton: View? = null
    private var fileIndicator: View? = null
    private var timeStatsView: TextView? = null
    private var charStatsView: TextView? = null
    private var tokenStatsView: TextView? = null

    private var pickedUri: Uri? = null
    private var pickedMimiType: String? = null

    private lateinit var resumeViewModel: ResumeViewModel

    private var oldResults: String = ""

    private val dotsLoader = DotsLoader(lifecycleScope)
    private val timeStats =  TimeStats(lifecycleScope)

    private lateinit var markdown: Markwon
    private var colorHuman: String = "#fff"
    private var colorAI: String = "#000"
    private var colorError: String = "#ff0000"

    private var engine: AIEngine = AIEngine.GEMINI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        markdown = Markwon.builder(requireContext())
            .usePlugin(SoftBreakAddsNewLinePlugin())
            .usePlugin(TablePlugin.create(requireContext()))
            .usePlugin(HtmlPlugin.create { plugin ->
                plugin.addHandler(CustomFontTagHandler())
            })
            .build();

        resumeViewModel = ViewModelProvider(requireActivity())[ResumeViewModel::class.java]
        resumeViewModel.resetState()

        engine = AppSettingsPrefs.instance.getAIEngine()
        Logger.debug("chat with engine: $engine")

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                AppSettingsPrefs.instance.prefsChanges.collectLatest {
                    syncStatsViews()
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                resumeViewModel.prepareRepos(arrayOf(engine))
                resumeViewModel.readyOfRepos[engine]?.collect { ready ->
                    checkSendAvailability()
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                resumeViewModel.uiState.collect { uiState ->
                    Logger.debug("[$engine]: resp text = ${uiState.fullResp}")

                    checkSendAvailability()

                    if (uiState.fullResp.isNotEmpty()
                        || uiState.status == UiStatus.Error) {
                        dotsLoader.stopLoadingDots()
                    }

                    appendResults(uiState.fullResp)

                    if (uiState.status == UiStatus.Done
                        || uiState.status == UiStatus.Error) {
                        oldResults += buildString {
                            if (uiState.status == UiStatus.Error) {
                                Logger.debug("error: ${uiState.errorMessage}")
                                append("<font color='${colorError}'>")
                                append(uiState.errorMessage?.trim())
                                append("</font>")
                            } else {
                                append(uiState.fullResp)
                            }
                            append("</font>")
                            append("\n")
                            append("\n")
                        }

                        appendResults(null)

                        stopStats()
                    }

                    charStatsView?.text = getString(
                        coreR.string.char_stats,
                        uiState.countOfChar.toString()
                    )

                    tokenStatsView?.text = getString(
                        coreR.string.token_stats,
                        (uiState.countOfChar/4).toString()
                    )
                }
            }
        }

        colorHuman = CustomFontTagHandler.colorIntToHex(
            ResourcesCompatUtils.getColor(requireContext(),
                coreR.color.human))
        colorAI = CustomFontTagHandler.colorIntToHex(
            ResourcesCompatUtils.getColor(requireContext(),
                coreR.color.ai))
        colorError = CustomFontTagHandler.colorIntToHex(
            ResourcesCompatUtils.getColor(requireContext(),
                coreR.color.error))

        setHasOptionsMenu(true)

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat_test, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userInput = view.findViewById(R.id.user_input)
        userInput?.addTextChangedListener {
            checkSendAvailability()
        }

        resultsView = view.findViewById(R.id.results)
        scrollView = view.findViewById(R.id.scrollView)

        sendButton = view.findViewById(R.id.send)
        sendButton?.setOnClickListener {
            val roleSelf = getString(coreR.string.label_myself)

            oldResults += buildString {
                append("<font color='${colorHuman}'><b>${roleSelf}:</b> ")
                append("${userInput?.text.toString()} </font>")
                append("\n")
                append("<font color='${colorAI}'><b>${engine}:</b> ")
                append("\n")
            }
            appendResults(null)

            if (AppSettingsPrefs.instance.asyncGeneration) {
                resumeViewModel.generateAsync(
                    userInput?.text.toString(),
                    pickedUri?.toString(),
                    pickedMimiType,
                    engine = engine)
            } else {
                resumeViewModel.generate(
                    userInput?.text.toString(),
                    pickedUri?.toString(),
                    pickedMimiType,
                    engine = engine)
            }

            startStats()

            userInput?.text = null

            dotsLoader.startLoadingDots {
                appendResults(it)
            }
        }

        pickButton = view.findViewById(R.id.pick)
        pickButton?.setOnClickListener {
            checkOrGrantPermissions()
        }

        fileIndicator = view.findViewById(R.id.file_indicator)

        timeStatsView = view.findViewById(R.id.time_stats)
        timeStatsView?.text = getString(
            coreR.string.time_stats,
            "%.1f".format(0.0)
        )

        tokenStatsView = view.findViewById(R.id.token_stats)
        charStatsView = view.findViewById(R.id.char_stats)
        syncStatsViews()

        syncLayout(false)

        activity?.registerActionBar(view, R.id.topAppBar)
    }

    private fun startStats() {
        timeStats.startStats {
            timeStatsView?.text = getString(
                coreR.string.time_stats,
                "%.1f".format(it/1000f)
            )
        }
    }

    private fun stopStats() {
        timeStats.stopStats()
    }

    private fun syncStatsViews() {
        Logger.debug("[STATS]: debug = ${AppSettingsPrefs.instance.debugEnabled}")
        if (AppSettingsPrefs.instance.debugEnabled) {
            timeStatsView?.visibility = View.VISIBLE
            charStatsView?.visibility = View.VISIBLE
            tokenStatsView?.visibility = View.VISIBLE
        } else {
            timeStatsView?.visibility = View.INVISIBLE
            charStatsView?.visibility = View.INVISIBLE
            tokenStatsView?.visibility = View.INVISIBLE
        }
    }

    private fun appendResults(text: String?) {
        Logger.debug("[APPEND] text = $text")
        val textView = resultsView?: return
        val scrollView = scrollView?: return

        val newText = buildString {
            append(oldResults)
            append(text ?: "")
        }

        markdown.setMarkdown(textView, newText)

        UiHelper.scrollToTextBottom(scrollView, textView)
    }

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                Logger.debug("[FILE] selected uri: $uri")

                pickedUri = uri

                pickedUri?.let {
                    pickedMimiType = requireContext().contentResolver.getType(it) ?: "*/*"
                    Logger.debug("[FILE] mineType: $pickedMimiType")
                }

                syncLayout(pickedUri != null)
            }
        }

    private fun checkSendAvailability() {
        val hasContent = !userInput?.text.isNullOrEmpty()
        val modelReady = resumeViewModel.readyOfRepos[engine]?.value ?: false
        val notRunning = resumeViewModel.uiState.value.status != UiStatus.InProgress

        sendButton?.isEnabled = hasContent && modelReady && notRunning
    }

    private fun syncLayout(fileAttached: Boolean) {
        fileIndicator?.visibility = if (fileAttached) {
            View.VISIBLE
        } else {
            View.GONE
        }

        val inputPaddingEnd = resources.getDimensionPixelSize(
            if (fileAttached) {
                coreR.dimen.chat_test_input_padding_end_with_file
            } else {
                coreR.dimen.chat_test_input_padding_end
            }
        )

        userInput?.setPadding(
            userInput?.paddingLeft ?: 0,
            userInput?.paddingTop ?: 0,
            inputPaddingEnd,
            userInput?.paddingBottom ?: 0
        )
    }

    override val autoCheckPermissions: Boolean
        get() = false

    override fun getPermissionsPromptViewId(): Int {
        return -1
    }

    override fun getRequiredPermissions(): Array<String> {
        return arrayOf()
    }

    override fun onPermissionsDenied() {
    }

    override fun onPermissionsGranted(newlyGranted: Boolean) {
        pickFile()
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*" // 接受任意类型的文件
        val mimeTypes = arrayOf("application/pdf", "image/*")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        pickFileLauncher.launch(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_about -> {
                val nextDir =
                    ChatTestFragmentDirections.actionChatTestFragmentToAboutFragment()
                findNavController().navigate(nextDir)
                return true
            }

            R.id.action_settings -> {
                val nextDir =
                    ChatTestFragmentDirections.actionChatTestFragmentToSettingsFragment()
                findNavController().navigate(nextDir)
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }


}