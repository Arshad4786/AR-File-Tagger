package com.example.arfiletagger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import android.widget.Toast
import com.example.arfiletagger.databinding.FragmentTagInputDialogBinding

class TagInputDialogFragment : DialogFragment() {

    interface TagInputListener {
        fun onTagInput(imageTag: ImageTag)
    }

    private var tagInputListener: TagInputListener? = null
    private var imageId: String? = null

    // Store initial values for pre-filling
    private var initialFilename: String? = null
    private var initialFilepath: String? = null
    private var initialDeadline: String? = null

    private var _binding: FragmentTagInputDialogBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_IMAGE_ID = "image_id"
        private const val ARG_FILENAME = "initial_filename"
        private const val ARG_FILEPATH = "initial_filepath"
        private const val ARG_DEADLINE = "initial_deadline"

        /**
         * Factory method to create a new instance of TagInputDialogFragment.
         * Can be used for both new tag creation (only imageId) or editing (with initial values).
         */
        fun newInstance(
            imageId: String,
            initialFilename: String? = null,
            initialFilepath: String? = null,
            initialDeadline: String? = null
        ): TagInputDialogFragment {
            val fragment = TagInputDialogFragment()
            val args = Bundle()
            args.putString(ARG_IMAGE_ID, imageId)
            args.putString(ARG_FILENAME, initialFilename)
            args.putString(ARG_FILEPATH, initialFilepath)
            args.putString(ARG_DEADLINE, initialDeadline)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            imageId = it.getString(ARG_IMAGE_ID)
            initialFilename = it.getString(ARG_FILENAME)
            initialFilepath = it.getString(ARG_FILEPATH)
            initialDeadline = it.getString(ARG_DEADLINE)
        }
        setStyle(STYLE_NORMAL, R.style.Theme_ARFIleTagger_Dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTagInputDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pre-fill fields if initial values are provided (i.e., in edit mode)
        initialFilename?.let { binding.editTextFilename.setText(it) }
        initialFilepath?.let { binding.editTextFilepath.setText(it) }
        initialDeadline?.let { binding.editTextDeadline.setText(it) }

        binding.saveButton.setOnClickListener {
            val filename = binding.editTextFilename.text.toString().trim()
            val filepath = binding.editTextFilepath.text.toString().trim()
            val deadline = binding.editTextDeadline.text.toString().trim()

            if (filename.isNotEmpty() && filepath.isNotEmpty() && deadline.isNotEmpty() && imageId != null) {
                val imageTag = ImageTag(filename, filepath, deadline, imageId!!)
                tagInputListener?.onTagInput(imageTag)
                dismiss()
            } else {
                Toast.makeText(context, "Please fill all fields.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setTagInputListener(listener: TagInputListener) {
        this.tagInputListener = listener
    }
}