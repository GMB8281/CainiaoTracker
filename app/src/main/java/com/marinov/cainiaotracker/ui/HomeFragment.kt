package com.marinov.cainiaotracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.marinov.cainiaotracker.R
import com.marinov.cainiaotracker.data.Package
import com.marinov.cainiaotracker.data.PackageRepository
import com.marinov.cainiaotracker.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: PackageRepository
    private lateinit var adapter: PackageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = PackageRepository(requireContext())

        adapter = PackageAdapter(
            onItemClick = { pkg ->
                val intent = Intent(requireContext(), PackageActivity::class.java).apply {
                    putExtra("trackingCode", pkg.trackingCode)
                }
                startActivity(intent)
            },
            onArchiveClick = { pkg ->
                pkg.isArchived = true
                repository.updatePackage(pkg)
                refreshList()
                showSnackbar(getString(R.string.snackbar_archived, pkg.name)) {
                    pkg.isArchived = false
                    repository.updatePackage(pkg)
                    refreshList()
                }
            },
            onDeleteClick = { pkg ->
                repository.deletePackage(pkg)
                refreshList()
                showSnackbar(getString(R.string.snackbar_deleted, pkg.name)) {
                    repository.addPackage(pkg)
                    refreshList()
                }
            },
            isArchiveMode = true
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddDialog() }

        refreshList()
    }

    private fun refreshList() {
        val packages = repository.getAllPackages().filter { !it.isArchived }
        adapter.submitList(packages)

        // GARANTIA ABSOLUTA: O botão sempre visível e trazido para a frente de qualquer view
        binding.fabAdd.visibility = View.VISIBLE
        binding.fabAdd.bringToFront()

        if (packages.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_package, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etCode = dialogView.findViewById<EditText>(R.id.et_tracking_code)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val name = etName.text.toString().trim()
                val code = etCode.text.toString().trim()
                if (name.isNotEmpty() && code.isNotEmpty()) {
                    repository.addPackage(Package(name = name, trackingCode = code))
                    refreshList()
                } else {
                    Toast.makeText(requireContext(), R.string.error_empty_fields, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showSnackbar(message: String, onUndo: () -> Unit) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_undo) { onUndo() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}