package com.marinov.cainiaotracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.marinov.cainiaotracker.data.TimelineEvent
import com.marinov.cainiaotracker.databinding.ItemTimelineBinding

class TimelineAdapter : ListAdapter<TimelineEvent, TimelineAdapter.ViewHolder>(DiffCallback()) {
    inner class ViewHolder(val binding: ItemTimelineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: TimelineEvent) {
            binding.tvTitle.text = event.title
            binding.tvTime.text = event.time
            if (event.description.isNotEmpty()) {
                binding.tvDescription.text = event.description
                binding.tvDescription.visibility = View.VISIBLE
            } else {
                binding.tvDescription.visibility = View.GONE
            }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTimelineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))
    class DiffCallback : DiffUtil.ItemCallback<TimelineEvent>() {
        override fun areItemsTheSame(old: TimelineEvent, new: TimelineEvent) = old.time == new.time && old.title == new.title
        override fun areContentsTheSame(old: TimelineEvent, new: TimelineEvent) = old == new
    }
}