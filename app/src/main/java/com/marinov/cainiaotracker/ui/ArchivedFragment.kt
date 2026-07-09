package com.marinov.cainiaotracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.marinov.cainiaotracker.R
import com.marinov.cainiaotracker.data.PackageRepository
import com.marinov.cainiaotracker.databinding.FragmentArchivedBinding

class ArchivedFragment : Fragment() {
    private var _binding: FragmentArchivedBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: PackageRepository
    private lateinit var adapter: PackageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArchivedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = PackageRepository(requireContext())

        adapter = PackageAdapter(
            onItemClick = { _ ->
                // Mostra toast em vez de abrir o PackageActivity
                Toast.makeText(requireContext(), R.string.toast_unarchive, Toast.LENGTH_SHORT).show()
            },
            onArchiveClick = { pkg ->
                pkg.isArchived = false
                repository.updatePackage(pkg)
                refreshList()
            },
            onDeleteClick = { pkg ->
                repository.deletePackage(pkg)
                refreshList()
                showSnackbar(getString(R.string.snackbar_deleted, pkg.name)) {
                    repository.addPackage(pkg)
                    refreshList()
                }
            },
            isArchiveMode = false
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        refreshList()
    }

    private fun refreshList() {
        val packages = repository.getAllPackages().filter { it.isArchived }
        adapter.submitList(packages)

        if (packages.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
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