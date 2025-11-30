package com.armemorypalace.notes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.math.Vector3
import android.app.AlertDialog
import android.widget.EditText
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.View
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.graphics.Bitmap
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.util.UUID

// Data model for notes
data class Note(
    val id: String = "",
    val userId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val positionZ: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : AppCompatActivity() {

    private var arFragment: ArFragment? = null
    private var session: Session? = null
    private var installRequested = false
    private var noteCount = 0
    private var pendingAnchor: AnchorNode? = null
    private var selectedImageUri: Uri? = null
    private var pendingNoteText: String? = null
    
    // Firebase
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private var currentUserId: String? = null
    
    // Map to store noteId for each anchor node
    private val anchorNoteMap = mutableMapOf<AnchorNode, String>()

    companion object {
        private const val PICK_IMAGE_REQUEST = 1001
        private const val CAMERA_PERMISSION_CODE = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as? ArFragment

        // Initialize Firebase - wrapped in try-catch to prevent crashes
        try {
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            storage = FirebaseStorage.getInstance()
            
            // Sign in anonymously to get a user ID
            signInAnonymously()
        } catch (e: Exception) {
            Toast.makeText(this, "Firebase not configured - notes won't be saved", Toast.LENGTH_LONG).show()
            // App will work without Firebase, just won't save notes
        }

        // Check ARCore availability
        checkARCoreAvailability()
    }
    
    private fun signInAnonymously() {
        auth.signInAnonymously()
            .addOnSuccessListener { authResult ->
                currentUserId = authResult.user?.uid
                Toast.makeText(this, "Signed in - notes will be saved", Toast.LENGTH_SHORT).show()
                // Load existing notes for this user
                loadNotesFromFirestore()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Auth failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkARCoreAvailability() {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        
        if (availability.isTransient) {
            // Re-query at 5Hz while we check compatibility
            window.decorView.postDelayed({ checkARCoreAvailability() }, 200)
        }
        
        if (availability.isSupported) {
            requestCameraPermission()
        } else {
            Toast.makeText(this, "ARCore is not supported on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            setupARSession()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupARSession()
            } else {
                Toast.makeText(this, "Camera permission is required for AR", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupARSession() {
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    // ARCore is installed, continue
                }
            }

            // Create AR session
            session = Session(this).apply {
                val config = Config(this).apply {
                    // Use BLOCKING mode for better performance and stability
                    updateMode = Config.UpdateMode.BLOCKING
                    focusMode = Config.FocusMode.AUTO
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    // Disable light estimation to reduce lag significantly
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                }
                configure(config)
            }
            
            // Ensure ARFragment uses this session
            arFragment?.arSceneView?.setupSession(session)

            Toast.makeText(this, "AR Session ready! Tap to place notes", Toast.LENGTH_SHORT).show()
            
            // Set up tap listener
            setupTapListener()
            
        } catch (e: UnavailableArcoreNotInstalledException) {
            Toast.makeText(this, "Please install ARCore", Toast.LENGTH_LONG).show()
        } catch (e: UnavailableApkTooOldException) {
            Toast.makeText(this, "Please update ARCore", Toast.LENGTH_LONG).show()
        } catch (e: UnavailableSdkTooOldException) {
            Toast.makeText(this, "Please update this app", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "AR Session failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        session?.resume()
    }

    override fun onPause() {
        super.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.close()
    }

    private fun setupTapListener() {
        arFragment?.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, motionEvent ->
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) {
                return@setOnTapArPlaneListener
            }

            // Create anchor directly from session for better stability
            val anchor = session?.createAnchor(hitResult.hitPose)
            if (anchor != null) {
                val anchorNode = AnchorNode(anchor)
                anchorNode.setParent(arFragment?.arSceneView?.scene)
                
                pendingAnchor = anchorNode

                // Show dialog to enter note text
                showNoteInputDialog(anchorNode)
            } else {
                Toast.makeText(this, "Failed to create anchor", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showNoteInputDialog(anchorNode: AnchorNode) {
        val input = EditText(this)
        input.hint = "Enter your note..."
        input.setPadding(50, 30, 50, 30)

        AlertDialog.Builder(this)
            .setTitle("Create AR Note")
            .setMessage("Enter text for your note, then optionally add an image")
            .setView(input)
            .setPositiveButton("Add Image") { dialog, _ ->
                val noteText = input.text.toString()
                if (noteText.isNotEmpty()) {
                    pendingNoteText = noteText
                    pendingAnchor = anchorNode
                    pickImage()
                } else {
                    Toast.makeText(this, "Please enter note text first", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNeutralButton("Text Only") { dialog, _ ->
                val noteText = input.text.toString()
                if (noteText.isNotEmpty()) {
                    placeNote(anchorNode, noteText, null)
                } else {
                    anchorNode.anchor?.detach()
                    anchorNode.setParent(null)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                anchorNode.anchor?.detach()
                anchorNode.setParent(null)
                dialog.dismiss()
            }
            .show()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.data
            pendingAnchor?.let { anchor ->
                pendingNoteText?.let { text ->
                    placeNote(anchor, text, selectedImageUri)
                }
            }
            // Reset pending state
            selectedImageUri = null
            pendingNoteText = null
            pendingAnchor = null
        } else if (requestCode == PICK_IMAGE_REQUEST) {
            // User cancelled image picker
            pendingAnchor?.let { anchor ->
                pendingNoteText?.let { text ->
                    placeNote(anchor, text, null)
                }
            }
            pendingNoteText = null
            pendingAnchor = null
        }
    }

    private fun placeNote(anchorNode: AnchorNode, noteText: String, imageUri: Uri?) {
        noteCount++
        
        // Create a colored cube base
        val colors = listOf(
            android.graphics.Color.parseColor("#4CAF50"),
            android.graphics.Color.parseColor("#2196F3"),
            android.graphics.Color.parseColor("#FF5722"),
            android.graphics.Color.parseColor("#FFC107")
        )
        val color = colors[(noteCount - 1) % colors.size]

        MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(color))
            .thenAccept { material ->
                val cube = ShapeFactory.makeCube(
                    Vector3(0.15f, 0.15f, 0.15f),
                    Vector3(0f, 0.075f, 0f),
                    material
                )
                
                val cubeNode = com.google.ar.sceneform.Node()
                cubeNode.renderable = cube
                cubeNode.setParent(anchorNode)

                // Create container with text and optionally image
                val container = LinearLayout(this)
                container.orientation = LinearLayout.VERTICAL
                container.setBackgroundColor(android.graphics.Color.parseColor("#DD000000"))
                container.setPadding(20, 15, 20, 15)

                // Add image if provided
                if (imageUri != null) {
                    val imageView = ImageView(this)
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                        // Smaller image size for better performance
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                        imageView.setImageBitmap(scaledBitmap)
                        imageView.layoutParams = LinearLayout.LayoutParams(200, 200)
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        container.addView(imageView)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }

                // Add text
                val textView = TextView(this)
                textView.text = noteText
                textView.setTextColor(android.graphics.Color.WHITE)
                textView.setPadding(0, 15, 0, 0)
                textView.textSize = 14f
                container.addView(textView)

                ViewRenderable.builder()
                    .setView(this, container)
                    .build()
                    .thenAccept { renderable ->
                        val labelNode = com.google.ar.sceneform.Node()
                        labelNode.renderable = renderable
                        labelNode.localPosition = Vector3(0f, 0.25f, 0f)
                        labelNode.setParent(cubeNode)
                    }

                // Make cube clickable
                cubeNode.setOnTapListener { _, _ ->
                    showNoteDetailsDialog(noteText, imageUri, anchorNode)
                }

                // Save note to Firestore
                saveNoteToFirestore(noteText, imageUri, anchorNode)
                
                Toast.makeText(this, "Note placed!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showNoteDetailsDialog(noteText: String, imageUri: Uri?, anchorNode: AnchorNode) {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(40, 20, 40, 20)

        // Add image if exists
        if (imageUri != null) {
            val imageView = ImageView(this)
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                imageView.setImageBitmap(bitmap)
                imageView.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    400
                )
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                container.addView(imageView)
            } catch (e: Exception) {
                // Image couldn't be loaded
            }
        }

        // Add text
        val textView = TextView(this)
        textView.text = noteText
        textView.setPadding(0, 20, 0, 0)
        textView.textSize = 16f
        container.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("AR Note")
            .setView(container)
            .setPositiveButton("OK", null)
            .setNegativeButton("Delete") { dialog, _ ->
                // Delete from Firestore
                val noteId = anchorNoteMap[anchorNode]
                if (noteId != null) {
                    deleteNoteFromFirestore(noteId)
                    anchorNoteMap.remove(anchorNode)
                }
                
                anchorNode.anchor?.detach()
                anchorNode.setParent(null)
                Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }
    
    private fun saveNoteToFirestore(noteText: String, imageUri: Uri?, anchorNode: AnchorNode) {
        val userId = currentUserId ?: run {
            // Firebase not initialized, skip saving
            return
        }
        val noteId = UUID.randomUUID().toString()
        
        // Get anchor position
        val position = anchorNode.worldPosition
        
        // Save image to Firebase Storage if exists
        if (imageUri != null) {
            val imageRef = storage.reference.child("users/$userId/notes/$noteId.jpg")
            
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val data = baos.toByteArray()
                
                imageRef.putBytes(data)
                    .addOnSuccessListener { taskSnapshot ->
                        imageRef.downloadUrl.addOnSuccessListener { uri ->
                            saveNoteData(noteId, userId, noteText, uri.toString(), position, anchorNode)
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                        saveNoteData(noteId, userId, noteText, "", position, anchorNode)
                    }
            } catch (e: Exception) {
                saveNoteData(noteId, userId, noteText, "", position, anchorNode)
            }
        } else {
            saveNoteData(noteId, userId, noteText, "", position, anchorNode)
        }
    }
    
    private fun saveNoteData(noteId: String, userId: String, text: String, imageUrl: String, 
                            position: Vector3, anchorNode: AnchorNode) {
        val note = Note(
            id = noteId,
            userId = userId,
            text = text,
            imageUrl = imageUrl,
            positionX = position.x,
            positionY = position.y,
            positionZ = position.z
        )
        
        // Store noteId in map for deletion
        anchorNoteMap[anchorNode] = noteId
        
        firestore.collection("notes")
            .document(noteId)
            .set(note)
            .addOnSuccessListener {
                Toast.makeText(this, "Note saved to cloud!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun loadNotesFromFirestore() {
        val userId = currentUserId ?: return
        
        firestore.collection("notes")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                Toast.makeText(this, "Loading ${documents.size()} saved notes...", Toast.LENGTH_SHORT).show()
                
                for (document in documents) {
                    val note = document.toObject(Note::class.java)
                    // Note: We can't perfectly relocalize without Cloud Anchors
                    // For now, notes will be recreated at saved positions relative to device start
                    // TODO: Add Cloud Anchors for proper world-locked persistence
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load notes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun deleteNoteFromFirestore(noteId: String) {
        firestore.collection("notes")
            .document(noteId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Note deleted from cloud", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
