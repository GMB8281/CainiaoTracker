package com.marinov.cainiaotracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.marinov.cainiaotracker.R
import com.marinov.cainiaotracker.data.Package
import com.marinov.cainiaotracker.databinding.ItemPackageBinding

class PackageAdapter(
    private val onItemClick: (Package) -> Unit,
    private val onArchiveClick: (Package) -> Unit,
    private val onDeleteClick: (Package) -> Unit,
    private val onEditClick: ((Package) -> Unit)? = null,
    private val isArchiveMode: Boolean = true // True = Mostra ic_action_archive, False = Mostra ic_action_unarchive
) : ListAdapter<Package, PackageAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(val binding: ItemPackageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(pkg: Package) {
            binding.tvName.text = pkg.name
            binding.tvCode.text = pkg.trackingCode
            binding.root.setOnClickListener { onItemClick(pkg) }
            binding.btnArchive.setOnClickListener { onArchiveClick(pkg) }
            binding.btnDelete.setOnClickListener { onDeleteClick(pkg) }

            binding.btnEdit.setOnClickListener { onEditClick?.invoke(pkg) }
            // Usamos INVISIBLE em vez de GONE para não quebrar as regras do RelativeLayout quando o botão some
            binding.btnEdit.visibility = if (isArchiveMode) View.VISIBLE else View.INVISIBLE

            if (!isArchiveMode) {
                binding.btnArchive.setImageResource(R.drawable.ic_action_unarchive)
                binding.btnArchive.contentDescription = "Desarquivar"
            } else {
                binding.btnArchive.setImageResource(R.drawable.ic_action_archive)
                binding.btnArchive.contentDescription = "Arquivar"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPackageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Package>() {
        override fun areItemsTheSame(oldItem: Package, newItem: Package) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Package, newItem: Package) = oldItem == newItem
    }
}