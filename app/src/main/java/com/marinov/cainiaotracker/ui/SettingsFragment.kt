package com.marinov.cainiaotracker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.marinov.cainiaotracker.R
import com.marinov.cainiaotracker.data.BackgroundService
import androidx.core.content.edit

class SettingsFragment : Fragment() {
    private val PREFS_NAME = "cainiao_settings"
    private val KEY_SYNC_INTERVAL = "sync_interval_minutes"
    private val DEFAULT_INTERVAL = 60L  // 1 hora

    private val intervalMap = mapOf(
        0 to 60L,    // 1 hora
        1 to 120L,   // 2 horas
        2 to 180L,   // 3 horas
        3 to 360L,   // 6 horas
        4 to 720L,   // 12 horas
        5 to 1440L   // 24 horas
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinner = view.findViewById<Spinner>(R.id.spinner_sync_interval)
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedInterval = prefs.getLong(KEY_SYNC_INTERVAL, DEFAULT_INTERVAL)

        val position = intervalMap.entries.find { it.value == savedInterval }?.key ?: 0
        spinner.setSelection(position)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val minutes = intervalMap[position] ?: DEFAULT_INTERVAL
                prefs.edit { putLong(KEY_SYNC_INTERVAL, minutes) }

                val intent = Intent(requireContext(), BackgroundService::class.java).apply {
                    action = BackgroundService.ACTION_UPDATE_INTERVAL
                    putExtra(BackgroundService.EXTRA_INTERVAL, minutes)
                }
                requireContext().startService(intent)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}