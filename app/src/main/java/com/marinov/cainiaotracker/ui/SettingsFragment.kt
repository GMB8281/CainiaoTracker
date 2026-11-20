package com.marinov.cainiaotracker.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.marinov.cainiaotracker.R
import com.marinov.cainiaotracker.data.SyncScheduler

class SettingsFragment : Fragment() {

    private val PREFS_NAME = "cainiao_settings"
    private val KEY_SYNC_INTERVAL = "sync_interval_minutes"

    private val intervalMap = mapOf(
        0 to 60L,    // 1 hora
        1 to 120L,   // 2 horas
        2 to 180L,   // 3 horas
        3 to 360L,   // 6 horas
        4 to 720L,   // 12 horas
        5 to 1440L   // 24 horas
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val spinner = view.findViewById<Spinner>(R.id.spinner_sync_interval)
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedInterval = prefs.getLong(KEY_SYNC_INTERVAL, SyncScheduler.DEFAULT_INTERVAL)
        val position = intervalMap.entries.find { it.value == savedInterval }?.key ?: 0
        spinner.setSelection(position)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val minutes = intervalMap[position] ?: SyncScheduler.DEFAULT_INTERVAL
                // Só reagenda se o valor mudou de verdade (evita resetar o ciclo
                // no disparo inicial do spinner ao abrir a tela)
                val current = prefs.getLong(KEY_SYNC_INTERVAL, SyncScheduler.DEFAULT_INTERVAL)
                if (minutes != current) {
                    SyncScheduler.updateInterval(requireContext(), minutes)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}