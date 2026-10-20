package com.gumby.audiocycle

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gumby.audiocycle.databinding.FragmentFirstBinding
import com.gumby.audiocycle.model.ConversionFormat
import com.gumby.audiocycle.model.ConversionState
import com.gumby.audiocycle.util.FileUtil
import kotlinx.coroutines.launch

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.selectFile(requireContext(), it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUIListeners()
        setupDropdowns()
        observeState()
    }

    private fun setupUIListeners() {
        binding.btnSelectFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("audio/*", "video/*", "*/*"))
        }

        binding.btnStartConversion.setOnClickListener {
            viewModel.startConversion(requireContext())
        }

        binding.btnCancelConversion.setOnClickListener {
            viewModel.cancelConversion()
        }

        binding.btnDismissError.setOnClickListener {
            viewModel.resetState()
        }
    }

    private fun setupDropdowns() {
        val formats = ConversionFormat.values()
        val formatNames = formats.map { it.displayName }
        val formatAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, formatNames)
        binding.dropdownTargetFormat.setAdapter(formatAdapter)
        binding.dropdownTargetFormat.setText(formats[0].displayName, false)

        binding.dropdownTargetFormat.setOnItemClickListener { _, _, position, _ ->
            val selectedFormat = formats[position]
            viewModel.updateTargetFormat(selectedFormat)

            val isLossless = selectedFormat == ConversionFormat.WAV || selectedFormat == ConversionFormat.FLAC
            binding.layoutBitrate.isVisible = !isLossless
        }

        val bitrates = listOf(128, 192, 256, 320)
        val bitrateNames = bitrates.map { "$it kbps" }
        val bitrateAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, bitrateNames)
        binding.dropdownBitrate.setAdapter(bitrateAdapter)
        binding.dropdownBitrate.setText(bitrateNames[1], false)

        binding.dropdownBitrate.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateBitrate(bitrates[position])
        }

        val sampleRates = listOf(44100, 48000, 22050, 96000)
        val sampleRateNames = listOf("44.1 kHz (CD Standard)", "48.0 kHz (Video Standard)", "22.05 kHz", "96.0 kHz (High-Res)")
        val sampleRateAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sampleRateNames)
        binding.dropdownSampleRate.setAdapter(sampleRateAdapter)
        binding.dropdownSampleRate.setText(sampleRateNames[0], false)

        binding.dropdownSampleRate.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateSampleRate(sampleRates[position])
        }

        val channels = listOf(2, 1)
        val channelNames = listOf("Stereo (2 Channels)", "Mono (1 Channel)")
        val channelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, channelNames)
        binding.dropdownChannels.setAdapter(channelAdapter)
        binding.dropdownChannels.setText(channelNames[0], false)

        binding.dropdownChannels.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateChannels(channels[position])
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedOptions.collect { options ->
                        if (options == null) {
                            binding.txtFileName.text = "No file selected"
                            binding.txtFileSize.isVisible = false
                            binding.cardSettings.isVisible = false
                        } else {
                            binding.txtFileName.text = options.sourceFileName
                            binding.txtFileSize.text = FileUtil.formatFileSize(options.sourceFileSize)
                            binding.txtFileSize.isVisible = options.sourceFileSize > 0

                            if (viewModel.conversionState.value is ConversionState.Idle) {
                                binding.cardSettings.isVisible = true
                            }
                        }
                    }
                }

                launch {
                    viewModel.conversionState.collect { state ->
                        updateStateUI(state)
                    }
                }
            }
        }
    }

    private fun updateStateUI(state: ConversionState) {
        when (state) {
            is ConversionState.Idle -> {
                binding.cardProgress.isVisible = false
                binding.cardResult.isVisible = false
                binding.cardError.isVisible = false
                binding.btnSelectFile.isEnabled = true
                binding.btnStartConversion.isEnabled = true
                binding.cardSettings.isVisible = viewModel.selectedOptions.value != null
            }
            is ConversionState.Preparing -> {
                binding.cardProgress.isVisible = true
                binding.cardResult.isVisible = false
                binding.cardError.isVisible = false
                binding.cardSettings.isVisible = false
                binding.btnSelectFile.isEnabled = false

                binding.txtProgressTitle.text = "Preparing media file..."
                binding.progressIndicator.isIndeterminate = true
                binding.txtProgressPercent.text = ""
                binding.txtTimeElapsed.text = "Copying source file to cache..."
                binding.txtLogSnippet.text = ""
            }
            is ConversionState.Converting -> {
                binding.cardProgress.isVisible = true
                binding.cardResult.isVisible = false
                binding.cardError.isVisible = false
                binding.cardSettings.isVisible = false
                binding.btnSelectFile.isEnabled = false

                binding.txtProgressTitle.text = "Converting Audio..."
                binding.progressIndicator.isIndeterminate = false
                binding.progressIndicator.progress = state.progressPercent
                binding.txtProgressPercent.text = "${state.progressPercent}%"

                val minutes = state.timeElapsedSec / 60
                val seconds = state.timeElapsedSec % 60
                binding.txtTimeElapsed.text = String.format("Time elapsed: %02d:%02d", minutes, seconds)
                binding.txtLogSnippet.text = state.logSnippet
            }
            is ConversionState.Success -> {
                binding.cardProgress.isVisible = false
                binding.cardSettings.isVisible = false
                binding.cardError.isVisible = false
                binding.cardResult.isVisible = true
                binding.btnSelectFile.isEnabled = true

                binding.txtResultFileName.text = state.outputFileName

                binding.btnPlayResult.setOnClickListener {
                    playAudioFile(state.outputFileUri, state.outputFormat.mimeType)
                }

                binding.btnShareResult.setOnClickListener {
                    shareAudioFile(state.outputFileUri, state.outputFormat.mimeType)
                }
            }
            is ConversionState.Error -> {
                binding.cardProgress.isVisible = false
                binding.cardSettings.isVisible = false
                binding.cardResult.isVisible = false
                binding.cardError.isVisible = true
                binding.btnSelectFile.isEnabled = true

                binding.txtErrorDetails.text = state.message
            }
        }
    }

    private fun playAudioFile(uri: Uri, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Play Audio with..."))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to open audio player: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareAudioFile(uri: Uri, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Audio File"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
