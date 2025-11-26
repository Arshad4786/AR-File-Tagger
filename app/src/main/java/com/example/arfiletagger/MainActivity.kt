package com.example.arfiletagger

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion // Import Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity(), Scene.OnUpdateListener, TagInputDialogFragment.TagInputListener {

    private lateinit var arFragment: ArFragment
    private val TAG = "ARFileTagger"
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val placedAnchorNodes = ConcurrentHashMap<String, AnchorNode>()
    private val imagesAwaitingTagInput = ConcurrentHashMap<String, Boolean>()
    private var isImageDatabaseConfigured = false
    private var capturedImageUri: Uri? = null
    private var isAuthSetupDone = false

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) {
            capturedImageUri?.let { uri ->
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    Log.d(TAG, "Image captured successfully, converting to bitmap.")
                    if (isAuthSetupDone && auth.currentUser != null) {
                        addCapturedImageToArSession(bitmap)
                        Toast.makeText(this, "Image captured! Now scan it.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "App is not authenticated. Please wait.", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Attempted to add image before authentication was ready.")
                    }
                } catch (e: IOException) {
                    val errorMessage = "Failed to load captured image: ${e.message}"
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    Log.e(TAG, errorMessage, e)
                }
            } ?: run {
                Toast.makeText(this, "Failed to get image URI.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Captured image URI is null.")
            }
        } else {
            Toast.makeText(this, "Image capture cancelled or failed.", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Image capture cancelled or failed.")
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Camera permission granted.")
                captureImage()
            } else {
                Toast.makeText(this, "Camera permission is required to capture images.", Toast.LENGTH_LONG).show()
                Log.w(TAG, "Camera permission denied.")
            }
        }

    // Reference to your custom Application class
    private lateinit var myApplication: MyApplication

    private val manageTagsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Returned from SavedTagsActivity. Rebuilding AugmentedImageDatabase.")
            // This is crucial: Rebuild the database if any changes (deletions) might have occurred.
            // We force a session reconfiguration to apply changes.
            arFragment.arSceneView.session?.let { currentSession ->
                val config = currentSession.config
                // Rebuild database using the function in MainActivity, passing the session
                config.augmentedImageDatabase = buildAugmentedImageDatabase(currentSession)
                currentSession.configure(config)
                Toast.makeText(this, "AR Database updated.", Toast.LENGTH_SHORT).show()
                // Clear existing placed nodes, as their associated images might have been deleted
                clearPlacedArNodes()
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize MyApplication reference
        myApplication = application as MyApplication // Correctly cast to your custom Application class

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        Log.d(TAG, "Firestore and FirebaseAuth initialized.")

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        Log.d(TAG, "ArFragment found.")

        arFragment.setOnSessionConfigurationListener { session, config ->
            Log.d(TAG, "Session configuration listener triggered.")

            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.focusMode = Config.FocusMode.AUTO

            if (!isAuthSetupDone) {
                auth.signInAnonymously()
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Anonymous sign-in successful. User ID: ${auth.currentUser?.uid}")
                            // Build database using function in MainActivity, passing the session
                            config.augmentedImageDatabase = buildAugmentedImageDatabase(session)
                            session.configure(config)
                            Log.d(TAG, "ARCore session configured with augmented image database from saved files.")
                            isAuthSetupDone = true
                            isImageDatabaseConfigured = true
                            Toast.makeText(this, "Authenticated and ready!", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.e(TAG, "Anonymous sign-in failed.", task.exception)
                            Toast.makeText(this, "Authentication failed. AR features might not work.", Toast.LENGTH_LONG).show()
                        }
                    }
            } else if (auth.currentUser != null) {
                // Build database using function in MainActivity, passing the session
                config.augmentedImageDatabase = buildAugmentedImageDatabase(session)
                session.configure(config)
                Log.d(TAG, "ARCore session re-configured for existing authenticated user with augmented image database.")
                isImageDatabaseConfigured = true
            } else {
                Log.w(TAG, "ARCore session configuration skipped: Auth not done or user not logged in.")
            }
        }

        val captureButton: FloatingActionButton = findViewById(R.id.capture_button)
        captureButton.setOnClickListener {
            checkCameraPermissionAndCapture()
        }

        val manageTagsButton: FloatingActionButton = findViewById(R.id.manage_tags_button)
        manageTagsButton.setOnClickListener {
            if (isAuthSetupDone && auth.currentUser != null) {
                val intent = Intent(this, SavedTagsActivity::class.java)
                manageTagsLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Please wait for authentication to complete.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkCameraPermissionAndCapture() {
        if (!isAuthSetupDone || auth.currentUser == null) {
            Toast.makeText(this, "Please wait, authenticating with Firebase...", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Attempted to capture image before Firebase authentication was complete.")
            return
        }

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Camera permission already granted, proceeding to capture image.")
                captureImage()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(this, "Camera access is needed to capture images for AR tracking.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                Log.d(TAG, "Requesting camera permission.")
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun captureImage() {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "new_ar_image_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ARFileTagger")
        }
        capturedImageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (capturedImageUri != null) {
            takePictureLauncher.launch(capturedImageUri)
            Log.d(TAG, "Launched camera for image capture, URI: $capturedImageUri")
        } else {
            Toast.makeText(this, "Failed to create image file.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Failed to create URI for captured image.")
        }
    }

    private fun addCapturedImageToArSession(bitmap: Bitmap) {
        val session = arFragment.arSceneView.session ?: run {
            Toast.makeText(this, "AR Session not available.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "AR Session is null when trying to add captured image.")
            return
        }

        try {
            val config = session.config
            val currentAugmentedImageDatabase = config.augmentedImageDatabase ?: AugmentedImageDatabase(session).also {
                Log.d(TAG, "AugmentedImageDatabase was null, creating new one during addCapturedImage.")
            }

            val imageName = "dynamic_target_${System.currentTimeMillis()}"
            val imageIndex = currentAugmentedImageDatabase.addImage(imageName, bitmap)

            if (imageIndex != -1) {
                Log.d(TAG, "Captured image '$imageName' added to existing database at index $imageIndex.")

                // Call saveBitmapToInternalStorage from MyApplication
                if (myApplication.saveBitmapToInternalStorage(bitmap, imageName)) {
                    Log.d(TAG, "Bitmap '$imageName' saved to internal storage via MyApplication.")
                } else {
                    Log.e(TAG, "Failed to save bitmap '$imageName' to internal storage via MyApplication.")
                    Toast.makeText(this, "Warning: Could not save image for persistence.", Toast.LENGTH_LONG).show()
                }

                config.augmentedImageDatabase = currentAugmentedImageDatabase
                session.configure(config)
                isImageDatabaseConfigured = true
                Toast.makeText(this, "New image ready for scanning!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to add image to database. Is it unique and feature-rich?", Toast.LENGTH_LONG).show()
                Log.e(TAG, "AugmentedImageDatabase.addImage returned -1 for $imageName.")
            }

        } catch (e: Exception) {
            val errorMessage = "Error adding captured image to AR session: ${e.message}"
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            Log.e(TAG, errorMessage, e)
        }
    }

    /**
     * Builds the AugmentedImageDatabase from previously saved images.
     * This is called when the AR session is configured.
     * Moved back to MainActivity as it requires the Session object.
     */
    private fun buildAugmentedImageDatabase(session: Session): AugmentedImageDatabase {
        val database = AugmentedImageDatabase(session)
        // Access saved image files via MyApplication
        val savedImageFiles = myApplication.listSavedImageFiles()
        if (savedImageFiles.isEmpty()) {
            Log.d(TAG, "No saved images found to build AugmentedImageDatabase.")
            return database // Return an empty database if no images
        }

        var imagesAddedCount = 0
        for (filename in savedImageFiles) {
            // Load bitmap via MyApplication
            val bitmap = myApplication.loadBitmapFromInternalStorage(filename)
            if (bitmap != null) {
                val imageName = filename.substringBeforeLast(".") // Remove .png or .jpg extension
                val index = database.addImage(imageName, bitmap)
                if (index != -1) {
                    Log.d(TAG, "Loaded and added image '$imageName' to AugmentedImageDatabase.")
                    imagesAddedCount++
                } else {
                    Log.w(TAG, "Failed to add loaded bitmap '$imageName' to AugmentedImageDatabase (may not be trackable).")
                }
            } else {
                Log.w(TAG, "Failed to load bitmap from internal storage: $filename. It might have been deleted manually.")
            }
        }
        Log.d(TAG, "Built AugmentedImageDatabase with $imagesAddedCount images.")
        return database
    }


    private fun clearPlacedArNodes() {
        for ((_, anchorNode) in placedAnchorNodes) {
            anchorNode.setParent(null)
        }
        placedAnchorNodes.clear()
        Log.d(TAG, "Cleared all placed AR nodes from the scene.")
    }


    override fun onUpdate(frameTime: FrameTime?) {
        if (!isImageDatabaseConfigured) {
            return
        }

        val frame = arFragment.arSceneView.arFrame ?: return

        val updatedAugmentedImages = frame.getUpdatedTrackables(com.google.ar.core.AugmentedImage::class.java)

        for (image in updatedAugmentedImages) {
            when (image.trackingState) {
                TrackingState.TRACKING -> {
                    if (!placedAnchorNodes.containsKey(image.name)) {
                        if (imagesAwaitingTagInput.putIfAbsent(image.name, true) == null) {
                            Log.d(TAG, "New image '${image.name}' detected. Checking Firestore for existing tag.")
                            loadImageTagFromFirestore(image.name) { loadedTag ->
                                if (loadedTag != null) {
                                    Log.d(TAG, "Tag found for '${image.name}'. Displaying it.")
                                    val anchor = image.createAnchor(image.centerPose)
                                    val anchorNode = AnchorNode(anchor)
                                    anchorNode.setParent(arFragment.arSceneView.scene)
                                    placedAnchorNodes[image.name] = anchorNode
                                    createViewRenderable(anchorNode, loadedTag)
                                    imagesAwaitingTagInput.remove(image.name)
                                } else {
                                    Log.d(TAG, "No tag found for '${image.name}'. Prompting for input.")
                                    showTagInputDialog(image.name)
                                }
                            }
                        }
                    } else {
                        placedAnchorNodes[image.name]?.isEnabled = true
                    }
                }
                TrackingState.STOPPED -> {
                    if (placedAnchorNodes.containsKey(image.name)) {
                        Log.d(TAG, "Image '${image.name}' stopped tracking. Removing node.")
                        placedAnchorNodes[image.name]?.setParent(null)
                        placedAnchorNodes.remove(image.name)
                        imagesAwaitingTagInput.remove(image.name)
                    }
                }
                else -> {
                    placedAnchorNodes[image.name]?.isEnabled = false
                }
            }
        }
    }

    private fun showTagInputDialog(imageId: String) {
        val dialogFragment = TagInputDialogFragment.newInstance(imageId)
        dialogFragment.setTagInputListener(this)
        dialogFragment.isCancelable = false
        dialogFragment.show(supportFragmentManager, "TagInput")
        Log.d(TAG, "Showing tag input dialog for image: $imageId")
    }

    override fun onTagInput(imageTag: ImageTag) {
        imagesAwaitingTagInput.remove(imageTag.imageId)

        saveImageTagToFirestore(imageTag) { success ->
            if (success) {
                Log.d(TAG, "ImageTag saved to Firestore for ${imageTag.imageId}")
                val currentFrame = arFragment.arSceneView.arFrame
                val trackedImages = currentFrame?.getUpdatedTrackables(com.google.ar.core.AugmentedImage::class.java)
                val targetImage = trackedImages?.firstOrNull { it.name == imageTag.imageId && it.trackingState == TrackingState.TRACKING }

                targetImage?.let {
                    Log.d(TAG, "Image ${imageTag.imageId} is still tracking. Creating/updating renderable.")
                    // Remove existing node if present to ensure proper update
                    placedAnchorNodes[imageTag.imageId]?.setParent(null)
                    placedAnchorNodes.remove(imageTag.imageId)

                    val anchor = it.createAnchor(it.centerPose)
                    val anchorNode = AnchorNode(anchor)
                    anchorNode.setParent(arFragment.arSceneView.scene)
                    placedAnchorNodes[imageTag.imageId] = anchorNode
                    createViewRenderable(anchorNode, imageTag)
                } ?: run {
                    Log.w(TAG, "Image ${imageTag.imageId} not tracking after input, cannot place tag immediately.")
                    Toast.makeText(this, "Tag saved! Scan the image again to see the tag.", Toast.LENGTH_LONG).show()
                }

            } else {
                Toast.makeText(this, "Failed to save tag to cloud.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Failed to save ImageTag for ${imageTag.imageId} to Firestore.")
            }
        }
    }

    private fun saveImageTagToFirestore(imageTag: ImageTag, onComplete: (Boolean) -> Unit) {
        if (imageTag.imageId.isBlank()) {
            Log.e(TAG, "Cannot save ImageTag with blank imageId.")
            onComplete(false)
            return
        }
        firestore.collection("imageTags")
            .document(imageTag.imageId)
            .set(imageTag)
            .addOnSuccessListener {
                Log.d(TAG, "ImageTag for ${imageTag.imageId} successfully written!")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error writing ImageTag document", e)
                Toast.makeText(this, "Error saving tag: ${e.message}", Toast.LENGTH_LONG).show()
                onComplete(false)
            }
    }

    private fun loadImageTagFromFirestore(imageId: String, onComplete: (ImageTag?) -> Unit) {
        firestore.collection("imageTags")
            .document(imageId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val imageTag = document.toObject(ImageTag::class.java)
                    Log.d(TAG, "ImageTag for $imageId loaded from Firestore: $imageTag")
                    onComplete(imageTag)
                } else {
                    Log.d(TAG, "No ImageTag found for $imageId in Firestore.")
                    onComplete(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading ImageTag for $imageId from Firestore", e)
                onComplete(null)
            }
    }

    private fun createViewRenderable(anchorNode: AnchorNode, imageTag: ImageTag) {
        Log.d(TAG, "Attempting to create ViewRenderable from R.layout.ar_text_view with data.")
        ViewRenderable.builder()
            .setView(this, R.layout.ar_text_view)
            .build()
            .thenAccept { viewRenderable ->
                Log.d(TAG, "ViewRenderable built successfully.")
                val node = TransformableNode(arFragment.transformationSystem)
                node.setParent(anchorNode)
                node.renderable = viewRenderable

                // Adjust position slightly above the image plane
                node.localPosition = Vector3(0f, 0.005f, 0f) // Reduced Y-offset

                // Combine rotations:
                // 1. Rotate -90 degrees around X to flatten the tag onto the image plane.
                // 2. Then, rotate 90 degrees around the node's local Z-axis to orient the text correctly on that plane.
                // This combination aims to make the text appear horizontally readable on the image.
                val rotationX = Quaternion.axisAngle(Vector3(1f, 0f, 0f), -90f)
                val rotationZ = Quaternion.axisAngle(Vector3(0f, 0f, 1f), 90f) // Rotate text on its plane
                node.localRotation = Quaternion.multiply(rotationX, rotationZ)


                val tvFilename = viewRenderable.view.findViewById<TextView>(R.id.textViewName)
                val tvFilepath = viewRenderable.view.findViewById<TextView>(R.id.textViewUse)
                val tvDeadline = viewRenderable.view.findViewById<TextView>(R.id.textViewTimeSetter)

                tvFilename.text = "Filename: ${imageTag.filename}"
                tvFilepath.text = "Path: ${imageTag.filepath}"
                tvDeadline.text = "Deadline: ${imageTag.deadline}"


                arFragment.transformationSystem.selectNode(node)
                Log.d(TAG, "TransformableNode created and added to scene for anchor with populated data.")
            }
            .exceptionally { throwable ->
                val errorMessage = "Could not load view renderable: ${throwable.message}"
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                Log.e(TAG, errorMessage, throwable)
                null
            }
    }

    override fun onResume() {
        super.onResume()
        try {
            arFragment.arSceneView.resume()
            Log.d(TAG, "ArSceneView resumed.")
            arFragment.arSceneView.scene.addOnUpdateListener(this)
            Log.d(TAG, "OnUpdateListener set on arSceneView.scene in onResume.")
        } catch (e: Exception) {
            val errorMessage = "Error resuming ArSceneView: ${e.message}"
            Log.e(TAG, errorMessage, e)
            Toast.makeText(this, "Error resuming AR experience: $errorMessage", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        arFragment.arSceneView.scene.removeOnUpdateListener(this)
        arFragment.arSceneView.pause()
        Log.d(TAG, "ArSceneView paused.")
    }

    override fun onDestroy() {
        super.onDestroy()
        val fragment = supportFragmentManager.findFragmentByTag("TagInput")
        if (fragment is DialogFragment) {
            fragment.dismissAllowingStateLoss()
        }
        Log.d(TAG, "ArSceneView destroyed.")
    }
}
