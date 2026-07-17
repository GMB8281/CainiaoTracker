package com.marinov.cainiaotracker.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.marinov.cainiaotracker.R
import com.marinov.cainiaotracker.data.Package
import com.marinov.cainiaotracker.data.PackageRepository
import com.marinov.cainiaotracker.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: PackageRepository
    private lateinit var adapter: PackageAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
            onEditClick = { pkg ->
                showPackageDialog(pkg)
            },
            isArchiveMode = true
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.fabAdd.setOnClickListener { showPackageDialog(null) }
        refreshList()
    }

    private fun refreshList() {
        val packages = repository.getAllPackages().filter { !it.isArchived }
        adapter.submitList(packages)

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

    private fun showPackageDialog(packageToEdit: Package? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_package, null)
        val tilName = dialogView.findViewById<TextInputLayout>(R.id.til_name)
        val tilCode = dialogView.findViewById<TextInputLayout>(R.id.til_tracking_code)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etCode = dialogView.findViewById<EditText>(R.id.et_tracking_code)

        val isEditMode = packageToEdit != null
        val titleRes = if (isEditMode) R.string.dialog_title_edit else R.string.dialog_title

        if (isEditMode) {
            etName.setText(packageToEdit!!.name)
            etCode.setText(packageToEdit.trackingCode)
            etCode.isEnabled = false // Não permite editar o código na tela de edição
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setView(dialogView)
            .setPositiveButton(R.string.action_ok, null) // Null listener evita que o diálogo feche automaticamente
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                var name = etName.text.toString().trim()
                val code = etCode.text.toString().trim()

                var isValid = true
                tilName.error = null
                tilCode.error = null

                // Auto-preencher nome com código se estiver vazio na criação
                if (!isEditMode && name.isEmpty() && code.isNotEmpty()) {
                    etName.setText(code)
                    name = code
                }

                if (name.isEmpty()) {
                    tilName.error = getString(R.string.error_name_empty)
                    isValid = false
                }
                if (!isEditMode && code.isEmpty()) {
                    tilCode.error = getString(R.string.error_code_empty)
                    isValid = false
                }

                if (isValid) {
                    if (isEditMode) {
                        val updatedPackage = packageToEdit!!.copy(name = name)
                        repository.updatePackage(updatedPackage)
                    } else {
                        repository.addPackage(Package(name = name, trackingCode = code))
                    }
                    refreshList()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
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