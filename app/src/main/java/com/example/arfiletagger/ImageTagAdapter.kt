package com.example.arfiletagger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ImageTagAdapter(
    private val listener: OnTagActionListener
) : RecyclerView.Adapter<ImageTagAdapter.ImageTagViewHolder>() {

    private val tags = mutableListOf<ImageTag>()

    // Interface to communicate actions back to the hosting Activity (SavedTagsActivity)
    interface OnTagActionListener {
        fun onEditTag(imageTag: ImageTag)
        fun onDeleteTag(imageTag: ImageTag)
    }

    fun submitList(newTags: List<ImageTag>) {
        tags.clear()
        tags.addAll(newTags)
        notifyDataSetChanged() // A simple way to update; DiffUtil could be used for better performance
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageTagViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_tag, parent, false)
        return ImageTagViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageTagViewHolder, position: Int) {
        val currentTag = tags[position]
        holder.bind(currentTag)
    }

    override fun getItemCount(): Int = tags.size

    inner class ImageTagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFilename: TextView = itemView.findViewById(R.id.tvFilename)
        private val tvFilepath: TextView = itemView.findViewById(R.id.tvFilepath)
        private val tvDeadline: TextView = itemView.findViewById(R.id.tvDeadline)
        private val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

        fun bind(imageTag: ImageTag) {
            tvFilename.text = "Filename: ${imageTag.filename}"
            tvFilepath.text = "Path: ${imageTag.filepath}"
            tvDeadline.text = "Deadline: ${imageTag.deadline}"

            btnEdit.setOnClickListener {
                listener.onEditTag(imageTag)
            }

            btnDelete.setOnClickListener {
                listener.onDeleteTag(imageTag)
            }
        }
    }
}