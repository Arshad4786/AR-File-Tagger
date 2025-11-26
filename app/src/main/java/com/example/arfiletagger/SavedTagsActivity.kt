package com.example.arfiletagger

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class SavedTagsActivity : AppCompatActivity(), ImageTagAdapter.OnTagActionListener, TagInputDialogFragment.TagInputListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyListMessage: TextView
    private lateinit var adapter: ImageTagAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var myApplication: MyApplication // Reference to your custom Application class

    private var currentlyEditedImageTag: ImageTag? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_tags)

        firestore = FirebaseFirestore.getInstance()
        myApplication = application as MyApplication // Correctly cast to your custom Application class

        recyclerView = findViewById(R.id.recyclerViewSavedTags)
        emptyListMessage = findViewById(R.id.textViewEmptyList)

        adapter = ImageTagAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadTagsFromFirestore()
    }

    private fun loadTagsFromFirestore() {
        firestore.collection("imageTags")
            .get()
            .addOnSuccessListener { result ->
                val tags = result.documents.mapNotNull { it.toObject(ImageTag::class.java) }
                adapter.submitList(tags)
                updateEmptyListMessage(tags.isEmpty())
                Log.d("SavedTagsActivity", "Loaded ${tags.size} tags from Firestore.")
            }
            .addOnFailureListener { exception ->
                Log.e("SavedTagsActivity", "Error loading tags: ", exception)
                Toast.makeText(this, "Failed to load tags: ${exception.message}", Toast.LENGTH_LONG).show()
                updateEmptyListMessage(true)
            }
    }

    private fun updateEmptyListMessage(isEmpty: Boolean) {
        if (isEmpty) {
            emptyListMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyListMessage.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onEditTag(imageTag: ImageTag) {
        currentlyEditedImageTag = imageTag
        val dialogFragment = TagInputDialogFragment.newInstance(
            imageTag.imageId,
            imageTag.filename,
            imageTag.filepath,
            imageTag.deadline
        )
        dialogFragment.setTagInputListener(this)
        dialogFragment.isCancelable = false
        dialogFragment.show(supportFragmentManager, "EditTagInput")
        Log.d("SavedTagsActivity", "Attempting to edit tag for imageId: ${imageTag.imageId}")
    }

    override fun onDeleteTag(imageTag: ImageTag) {
        AlertDialog.Builder(this)
            .setTitle("Delete Tag")
            .setMessage("Are you sure you want to delete the tag for '${imageTag.filename}'? This will also remove the image from AR tracking.")
            .setPositiveButton("Delete") { dialog, which ->
                performDelete(imageTag)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDelete(imageTag: ImageTag) {
        // 1. Delete from Firestore
        firestore.collection("imageTags")
            .document(imageTag.imageId)
            .delete()
            .addOnSuccessListener {
                Log.d("SavedTagsActivity", "Tag for ${imageTag.imageId} deleted from Firestore.")

                // 2. Delete associated image from internal storage via MyApplication
                if (myApplication.deleteBitmapFromInternalStorage(imageTag.imageId)) {
                    Log.d("SavedTagsActivity", "Image file for ${imageTag.imageId} deleted from internal storage via MyApplication.")
                } else {
                    Log.w("SavedTagsActivity", "Failed to delete image file for ${imageTag.imageId} via MyApplication.")
                }

                Toast.makeText(this, "Tag deleted successfully.", Toast.LENGTH_SHORT).show()
                loadTagsFromFirestore() // Refresh the list
                setResult(RESULT_OK) // Notify MainActivity of changes
            }
            .addOnFailureListener { e ->
                Log.e("SavedTagsActivity", "Error deleting tag from Firestore: ", e)
                Toast.makeText(this, "Error deleting tag: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onTagInput(imageTag: ImageTag) {
        if (currentlyEditedImageTag != null) {
            val updatedTag = imageTag.copy(imageId = currentlyEditedImageTag!!.imageId)
            firestore.collection("imageTags")
                .document(updatedTag.imageId)
                .set(updatedTag)
                .addOnSuccessListener {
                    Log.d("SavedTagsActivity", "Tag for ${updatedTag.imageId} updated in Firestore.")
                    Toast.makeText(this, "Tag updated successfully.", Toast.LENGTH_SHORT).show()
                    loadTagsFromFirestore()
                    setResult(RESULT_OK)
                }
                .addOnFailureListener { e ->
                    Log.e("SavedTagsActivity", "Error updating tag in Firestore: ", e)
                    Toast.makeText(this, "Error updating tag: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            Log.w("SavedTagsActivity", "onTagInput called without a currently edited tag. This shouldn't happen here.")
        }
        currentlyEditedImageTag = null
    }
}
