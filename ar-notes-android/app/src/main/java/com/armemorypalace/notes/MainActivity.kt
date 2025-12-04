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
import android.net.Uri
import android.provider.MediaStore
import android.graphics.Bitmap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.storage.FirebaseStorage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.media.MediaRecorder
import android.media.MediaPlayer
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.Menu
import android.view.MenuItem

// Data model for notes - Room-based relative positioning
data class Note(
    val id: String = "",
    val userId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val audioUrl: String = "",
    // Room-based positioning (new system)
    val roomId: String = "",
    val localPosX: Float = 0f,
    val localPosY: Float = 0f,
    val localPosZ: Float = 0f,
    val localRotX: Float = 0f,
    val localRotY: Float = 0f,
    val localRotZ: Float = 0f,
    val localRotW: Float = 1f,
    // Legacy fields (for backwards compatibility)
    val cloudAnchorId: String = "",  // Deprecated - not used
    val positionX: Float = 0f,  // Fallback for migration
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
    private var pendingNoteText: String? = null
    
    // Firebase
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private var currentUserId: String? = null
    
    // Google Sign-In (for Firebase user authentication)
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<android.content.Intent>
    
    // Room system for spatial walkthrough
    private var currentRoomId: String = "default-room"
    private var roomOriginAnchor: com.google.ar.core.Anchor? = null
    private var isAlignRoomMode: Boolean = false
    
    // Map to store noteId for each anchor node
    private val anchorNoteMap = mutableMapOf<AnchorNode, String>()

    // Audio recording
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFilePath: String? = null
    private var isRecording = false

    // Tutorial
    private lateinit var tutorialOverlay: View
    private lateinit var tutorialStepIndicator: TextView
    private lateinit var tutorialIcon: TextView
    private lateinit var tutorialTitle: TextView
    private lateinit var tutorialMessage: TextView
    private lateinit var tutorialNextButton: TextView
    private lateinit var tutorialSkipButton: TextView
    private lateinit var tutorialArrow: TextView
    private var currentTutorialStep = 0
    private lateinit var sharedPreferences: SharedPreferences

    // ActivityResultLauncher for picking images
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    companion object {
        private const val CAMERA_PERMISSION_CODE = 0
        private const val AUDIO_PERMISSION_CODE = 1
        private const val PREFS_NAME = "ARMemoryPalacePrefs"
        private const val TUTORIAL_COMPLETED_KEY = "tutorialCompleted"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as? ArFragment

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize tutorial views
        initializeTutorial()

        // Initialize the ActivityResultLauncher
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                pendingAnchor?.let { anchor ->
                    pendingNoteText?.let { text ->
                        placeNote(anchor, text, uri, null)
                    }
                }
            } else {
                // User cancelled image picker, place text-only note
                pendingAnchor?.let { anchor ->
                    pendingNoteText?.let { text ->
                        placeNote(anchor, text, null, null)
                    }
                }
            }
            // Reset pending state
            pendingNoteText = null
            pendingAnchor = null
        }

        // Initialize Google Sign-In launcher
        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                // Fall back to anonymous
                signInAnonymously()
            }
        }

        // Initialize Firebase - wrapped in try-catch to prevent crashes
        try {
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            storage = FirebaseStorage.getInstance()
            
            android.util.Log.d("MainActivity", "Firebase initialized successfully")
            
            // Configure Google Sign-In
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
            
            // Check if already signed in
            val currentUser = auth.currentUser
            if (currentUser != null) {
                currentUserId = currentUser.uid
                android.util.Log.d("MainActivity", "Already signed in: $currentUserId, isAnonymous: ${currentUser.isAnonymous}")
                
                // If anonymous and tutorial is complete, offer to sign in
                if (currentUser.isAnonymous && sharedPreferences.getBoolean(TUTORIAL_COMPLETED_KEY, false)) {
                    showUpgradeAccountPrompt()
                }
                
                loadNotesFromFirestore()
            } else {
                android.util.Log.d("MainActivity", "No current user, signing in anonymously")
                // Start with anonymous, prompt for Google Sign-In after first note
                signInAnonymously()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Firebase error: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("MainActivity", "Firebase initialization failed", e)
        }

        // Setup Sign In button
        setupSignInButton()
        
        // Check ARCore availability
        checkARCoreAvailability()
    }
    
    private fun setupSignInButton() {
        val fabSignIn = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabSignIn)
        
        fabSignIn.setOnClickListener {
            signInWithGoogle()
        }
        
        // Show button if user is anonymous
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            android.util.Log.d("MainActivity", "Auth state changed. Anonymous: ${user?.isAnonymous}, UserId: ${user?.uid}")
            
            if (user?.isAnonymous == true) {
                fabSignIn.visibility = android.view.View.VISIBLE
                android.util.Log.d("MainActivity", "Showing sign-in button")
            } else if (user != null) {
                fabSignIn.visibility = android.view.View.GONE
                android.util.Log.d("MainActivity", "Hiding sign-in button (signed in)")
            }
        }
        
        // Also check immediately
        val currentUser = auth.currentUser
        if (currentUser?.isAnonymous == true) {
            fabSignIn.visibility = android.view.View.VISIBLE
            android.util.Log.d("MainActivity", "Initial check: showing button for anonymous user")
        }
    }
    
    private fun signInAnonymously() {
        auth.signInAnonymously()
            .addOnSuccessListener { authResult ->
                currentUserId = authResult.user?.uid
                Toast.makeText(this, "Signed in anonymously", Toast.LENGTH_SHORT).show()
                android.util.Log.d("MainActivity", "Anonymous auth success: $currentUserId")
                loadNotesFromFirestore()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Auth failed: ${e.message}", Toast.LENGTH_LONG).show()
                android.util.Log.e("MainActivity", "Auth failed", e)
            }
    }
    
    private fun showUpgradeAccountPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Save Notes Permanently")
            .setMessage("Sign in with Google to sync your notes across all your devices")
            .setPositiveButton("Sign In") { _, _ ->
                signInWithGoogle()
            }
            .setNegativeButton("Later", null)
            .show()
    }
    
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }
    
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val previousUserId = currentUserId
        
        auth.currentUser?.linkWithCredential(credential)
            ?.addOnSuccessListener {
                currentUserId = it.user?.uid
                Toast.makeText(this, "Account upgraded! Notes will sync across devices", Toast.LENGTH_LONG).show()
            }
            ?.addOnFailureListener { e ->
                // If linking fails, try regular sign-in
                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        currentUserId = it.user?.uid
                        Toast.makeText(this, "Signed in with Google", Toast.LENGTH_SHORT).show()
                        loadNotesFromFirestore()
                    }
                    .addOnFailureListener { e2 ->
                        Toast.makeText(this, "Sign-in failed: ${e2.message}", Toast.LENGTH_SHORT).show()
                    }
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

            // Create AR session with local anchors only
            session = Session(this).apply {
                val config = Config(this).apply {
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    // Disable light estimation to reduce lag significantly
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    // Cloud Anchors disabled - using local room-relative positioning
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_align_room -> {
                isAlignRoomMode = true
                Toast.makeText(this, "Tap a surface to set room origin for $currentRoomId", Toast.LENGTH_LONG).show()
                true
            }
            R.id.menu_start_walkthrough -> {
                AlertDialog.Builder(this)
                    .setTitle("Start Walkthrough")
                    .setMessage("Select a room from the menu, then tap 'Align Room' and tap a surface to set the room origin. Your notes will appear!")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            R.id.menu_room_kitchen -> {
                switchRoom("Kitchen")
                true
            }
            R.id.menu_room_bathroom -> {
                switchRoom("Bathroom")
                true
            }
            R.id.menu_room_living_room -> {
                switchRoom("Living Room")
                true
            }
            R.id.menu_room_office -> {
                switchRoom("Office")
                true
            }
            R.id.menu_room_bedroom -> {
                switchRoom("Bedroom")
                true
            }
            R.id.menu_replay_tutorial -> {
                replayTutorial()
                true
            }
            R.id.menu_sign_in -> {
                if (auth.currentUser?.isAnonymous == true) {
                    signInWithGoogle()
                } else {
                    Toast.makeText(this, "Already signed in", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.menu_sign_out -> {
                auth.signOut()
                googleSignInClient.signOut()
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                signInAnonymously()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun switchRoom(roomName: String) {
        currentRoomId = roomName
        roomOriginAnchor = null
        
        // Clear existing anchors
        arFragment?.arSceneView?.scene?.children?.filterIsInstance<AnchorNode>()?.forEach { node ->
            node.anchor?.detach()
            node.setParent(null)
        }
        anchorNoteMap.clear()
        
        Toast.makeText(this, "Switched to $roomName. Tap 'Align Room' to load notes.", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.close()
        mediaRecorder?.release()
        mediaPlayer?.release()
    }

    private fun setupTapListener() {
        arFragment?.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, _ ->
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) {
                return@setOnTapArPlaneListener
            }

            // Check if we're in "Align Room" mode
            if (isAlignRoomMode) {
                roomOriginAnchor = session?.createAnchor(hitResult.hitPose)
                isAlignRoomMode = false
                Toast.makeText(this, "Room origin set for $currentRoomId! 📍", Toast.LENGTH_LONG).show()
                
                // Restore notes for this room
                restoreNotesForCurrentRoom()
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
        val options = arrayOf("Text Note", "Image Note", "Audio Note", "Cancel")
        
        AlertDialog.Builder(this)
            .setTitle("Create AR Note")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showTextNoteDialog(anchorNode)
                    1 -> showImageNoteDialog(anchorNode)
                    2 -> showAudioNoteDialog(anchorNode)
                    3 -> {
                        anchorNode.anchor?.detach()
                        anchorNode.setParent(null)
                    }
                }
            }
            .setOnCancelListener {
                anchorNode.anchor?.detach()
                anchorNode.setParent(null)
            }
            .show()
    }
    
    private fun showTextNoteDialog(anchorNode: AnchorNode) {
        val input = EditText(this)
        input.hint = "Enter your note..."
        input.setPadding(50, 30, 50, 30)

        AlertDialog.Builder(this)
            .setTitle("Text Note")
            .setView(input)
            .setPositiveButton("Place") { dialog, _ ->
                val noteText = input.text.toString()
                if (noteText.isNotEmpty()) {
                    placeNote(anchorNode, noteText, null, null)
                } else {
                    Toast.makeText(this, "Please enter text", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                anchorNode.anchor?.detach()
                anchorNode.setParent(null)
            }
            .show()
    }
    
    private fun showImageNoteDialog(anchorNode: AnchorNode) {
        val input = EditText(this)
        input.hint = "Enter note text..."
        input.setPadding(50, 30, 50, 30)

        AlertDialog.Builder(this)
            .setTitle("Image Note")
            .setView(input)
            .setPositiveButton("Pick Image") { dialog, _ ->
                val noteText = input.text.toString()
                pendingNoteText = noteText
                pendingAnchor = anchorNode
                pickImage()
            }
            .setNegativeButton("Cancel") { _, _ ->
                anchorNode.anchor?.detach()
                anchorNode.setParent(null)
            }
            .show()
    }
    
    private fun showAudioNoteDialog(anchorNode: AnchorNode) {
        if (!checkAudioPermission()) {
            requestAudioPermission()
            anchorNode.anchor?.detach()
            anchorNode.setParent(null)
            return
        }
        
        val recordButton = android.widget.Button(this)
        recordButton.text = "Start Recording"
        recordButton.setPadding(50, 30, 50, 30)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Audio Note")
            .setMessage("Record your audio note")
            .setView(recordButton)
            .setPositiveButton("Place Note", null)
            .setNegativeButton("Cancel") { _, _ ->
                stopRecording()
                anchorNode.anchor?.detach()
                anchorNode.setParent(null)
            }
            .create()
        
        dialog.setOnShowListener {
            val placeButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            placeButton.isEnabled = false
            
            recordButton.setOnClickListener {
                if (!isRecording) {
                    startRecording()
                    recordButton.text = "Stop Recording"
                    recordButton.setBackgroundColor(android.graphics.Color.RED)
                } else {
                    stopRecording()
                    recordButton.text = "Recording Complete"
                    recordButton.isEnabled = false
                    placeButton.isEnabled = true
                }
            }
            
            placeButton.setOnClickListener {
                audioFilePath?.let { path ->
                    placeNote(anchorNode, "Audio Note", null, path)
                    dialog.dismiss()
                }
            }
        }
        
        dialog.show()
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == 
            PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            AUDIO_PERMISSION_CODE
        )
    }
    
    private fun startRecording() {
        audioFilePath = "${externalCacheDir?.absolutePath}/audio_${System.currentTimeMillis()}.3gp"
        
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(audioFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            
            try {
                prepare()
                start()
                isRecording = true
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        mediaRecorder = null
        isRecording = false
    }
    
    private fun playAudio(audioPath: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(audioPath)
                prepare()
                start()
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pickImage() {
        imagePickerLauncher.launch("image/*")
    }

    private fun placeNote(anchorNode: AnchorNode, noteText: String, imageUri: Uri?, audioPath: String? = null, isLoadedNote: Boolean = false) {
        // Create a colored cube base
        val colors = listOf(
            android.graphics.Color.parseColor("#4CAF50"),
            android.graphics.Color.parseColor("#2196F3"),
            android.graphics.Color.parseColor("#FF5722"),
            android.graphics.Color.parseColor("#FFC107")
        )
        
        // Get color based on current noteCount (use absolute value to avoid negative index)
        val colorIndex = if (!isLoadedNote) {
            noteCount++
            (noteCount - 1) % colors.size
        } else {
            // For loaded notes, use a random color or cycle through based on position
            kotlin.math.abs(noteText.hashCode()) % colors.size
        }
        val color = colors[colorIndex]

        MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(color))
            .thenAccept { material ->
                // Create a sphere marker (like a pin/location marker)
                val sphere = ShapeFactory.makeSphere(
                    0.08f,  // radius
                    Vector3(0f, 0.08f, 0f),  // center position
                    material
                )
                
                val markerNode = com.google.ar.sceneform.Node()
                markerNode.renderable = sphere
                markerNode.setParent(anchorNode)

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
                
                // Add audio indicator if provided
                if (audioPath != null) {
                    val audioIcon = TextView(this)
                    audioIcon.text = "🔊"
                    audioIcon.textSize = 48f
                    audioIcon.setPadding(0, 10, 0, 10)
                    container.addView(audioIcon)
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
                        labelNode.setParent(markerNode)
                    }

                // Make marker clickable
                markerNode.setOnTapListener { _, _ ->
                    if (audioPath != null) {
                        playAudio(audioPath)
                        Toast.makeText(this, "Playing audio...", Toast.LENGTH_SHORT).show()
                    } else {
                        showNoteDetailsDialog(noteText, imageUri, anchorNode)
                    }
                }

                // Save note to Firestore
                saveNoteToFirestore(noteText, imageUri, audioPath, anchorNode)
                
                Toast.makeText(this, "Note placed!", Toast.LENGTH_SHORT).show()
                
                // Show upgrade prompt after first note if user is anonymous (only for new notes, not loaded ones)
                if (!isLoadedNote && noteCount == 1 && auth.currentUser?.isAnonymous == true) {
                    showUpgradeAccountPrompt()
                }
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
    
    private fun saveNoteToFirestore(noteText: String, imageUri: Uri?, audioPath: String?, anchorNode: AnchorNode) {
        val userId = currentUserId ?: run {
            // Firebase not initialized or user not authenticated
            Toast.makeText(this, "Not signed in - note not saved", Toast.LENGTH_LONG).show()
            android.util.Log.e("MainActivity", "Save failed: currentUserId is null")
            return
        }
        
        Toast.makeText(this, "Saving note to cloud...", Toast.LENGTH_SHORT).show()
        val noteId = UUID.randomUUID().toString()
        
        // Get anchor position
        val position = anchorNode.worldPosition
        
        // Save audio to Firebase Storage if exists
        if (audioPath != null) {
            try {
                val audioFile = File(audioPath)
                if (!audioFile.exists()) {
                    Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show()
                    saveNoteData(noteId, userId, noteText, "", "", position, anchorNode)
                    return
                }
                
                val audioRef = storage.reference.child("users/$userId/audio/$noteId.3gp")
                
                audioRef.putFile(Uri.fromFile(audioFile))
                    .addOnSuccessListener { _ ->
                        audioRef.downloadUrl.addOnSuccessListener { uri ->
                            saveNoteData(noteId, userId, noteText, "", uri.toString(), position, anchorNode)
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to get audio URL: ${e.message}", Toast.LENGTH_SHORT).show()
                            saveNoteData(noteId, userId, noteText, "", "", position, anchorNode)
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to upload audio: ${e.message}", Toast.LENGTH_SHORT).show()
                        saveNoteData(noteId, userId, noteText, "", "", position, anchorNode)
                    }
                return
            } catch (e: Exception) {
                Toast.makeText(this, "Audio upload error: ${e.message}", Toast.LENGTH_SHORT).show()
                saveNoteData(noteId, userId, noteText, "", "", position, anchorNode)
                return
            }
        }
        
        // Save image to Firebase Storage if exists
        if (imageUri != null) {
            val imageRef = storage.reference.child("users/$userId/images/$noteId.jpg")
            
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val data = baos.toByteArray()
                
                imageRef.putBytes(data)
                    .addOnSuccessListener { _ ->
                        imageRef.downloadUrl.addOnSuccessListener { uri ->
                            saveNoteData(noteId, userId, noteText, uri.toString(), "", position, anchorNode)
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                        saveNoteData(noteId, userId, noteText, "", "", position, anchorNode)
                    }
            } catch (e: Exception) {
                saveNoteData(noteId, userId, noteText, "", "", position, anchorNode)
            }
        } else {
            saveNoteData(noteId, userId, noteText, "", "", position, anchorNode)
        }
    }
    
    private fun saveNoteData(noteId: String, userId: String, text: String, imageUrl: String, 
                            audioUrl: String, position: Vector3, anchorNode: AnchorNode) {
        // Store noteId in map for deletion
        anchorNoteMap[anchorNode] = noteId
        
        // Get the anchor from the placed node
        val anchor = anchorNode.anchor
        if (anchor == null) {
            Toast.makeText(this, "Error: No anchor found", Toast.LENGTH_SHORT).show()
            return
        }
        
        // If room origin anchor doesn't exist, set this as the origin
        if (roomOriginAnchor == null) {
            roomOriginAnchor = anchor
            Toast.makeText(this, "Room origin set! 📍", Toast.LENGTH_SHORT).show()
        }
        
        // Compute relative pose from room origin
        val anchorPose = anchor.pose
        val originPose = roomOriginAnchor?.pose
        
        if (originPose != null) {
            // Calculate relative transformation: origin^-1 * anchor
            val relativePose = originPose.inverse().compose(anchorPose)
            
            // Extract translation
            val translation = relativePose.translation
            val localPosX = translation[0]
            val localPosY = translation[1]
            val localPosZ = translation[2]
            
            // Extract rotation quaternion
            val rotation = relativePose.rotationQuaternion
            val localRotX = rotation[0]
            val localRotY = rotation[1]
            val localRotZ = rotation[2]
            val localRotW = rotation[3]
            
            // Save to Firestore with room-relative pose
            saveNoteDataToFirestore(noteId, userId, text, imageUrl, audioUrl, 
                                   currentRoomId, localPosX, localPosY, localPosZ,
                                   localRotX, localRotY, localRotZ, localRotW,
                                   position)
        } else {
            // Fallback to world position if no origin (shouldn't happen)
            saveNoteDataToFirestore(noteId, userId, text, imageUrl, audioUrl, 
                                   currentRoomId, 0f, 0f, 0f, 0f, 0f, 0f, 1f, position)
        }
    }
    
    private fun saveNoteDataToFirestore(noteId: String, userId: String, text: String, imageUrl: String, 
                                       audioUrl: String, roomId: String,
                                       localPosX: Float, localPosY: Float, localPosZ: Float,
                                       localRotX: Float, localRotY: Float, localRotZ: Float, localRotW: Float,
                                       position: Vector3) {
        val note = Note(
            id = noteId,
            userId = userId,
            text = text,
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            roomId = roomId,
            localPosX = localPosX,
            localPosY = localPosY,
            localPosZ = localPosZ,
            localRotX = localRotX,
            localRotY = localRotY,
            localRotZ = localRotZ,
            localRotW = localRotW,
            positionX = position.x,
            positionY = position.y,
            positionZ = position.z
        )
        
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
        // This function is deprecated - use restoreNotesForCurrentRoom() instead
        Toast.makeText(this, "Use 'Align Room' to restore notes for a specific room", Toast.LENGTH_SHORT).show()
    }
    
    private fun restoreNotesForCurrentRoom() {
        val userId = currentUserId ?: return
        val originAnchor = roomOriginAnchor
        
        if (originAnchor == null) {
            Toast.makeText(this, "Please tap 'Align Room' first to set room origin", Toast.LENGTH_SHORT).show()
            return
        }
        
        firestore.collection("notes")
            .whereEqualTo("userId", userId)
            .whereEqualTo("roomId", currentRoomId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "No saved notes found in $currentRoomId", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                
                Toast.makeText(this, "Restoring ${documents.size()} notes in $currentRoomId...", Toast.LENGTH_SHORT).show()
                
                val arSession = session
                if (arSession == null) {
                    Toast.makeText(this, "AR session not ready", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                
                for (document in documents) {
                    try {
                        val note = document.toObject(Note::class.java)
                        
                        // Restore using room-relative positioning
                        if (note.roomId.isNotEmpty()) {
                            // Compose world pose from origin + relative pose
                            val originPose = originAnchor.pose
                            val localTranslation = floatArrayOf(note.localPosX, note.localPosY, note.localPosZ)
                            val localRotation = floatArrayOf(note.localRotX, note.localRotY, note.localRotZ, note.localRotW)
                            val localPose = com.google.ar.core.Pose(localTranslation, localRotation)
                            val worldPose = originPose.compose(localPose)
                            
                            // Create anchor at computed world position
                            val anchor = arSession.createAnchor(worldPose)
                            val anchorNode = AnchorNode(anchor)
                            anchorNode.setParent(arFragment?.arSceneView?.scene)
                            
                            val imageUri = if (note.imageUrl.isNotEmpty()) Uri.parse(note.imageUrl) else null
                            val audioPath = if (note.audioUrl.isNotEmpty()) note.audioUrl else null
                            
                            placeNote(anchorNode, note.text, imageUri, audioPath, isLoadedNote = true)
                            anchorNoteMap[anchorNode] = note.id
                        } else if (note.positionX != 0f || note.positionY != 0f || note.positionZ != 0f) {
                            // Legacy migration: old notes with world positions only
                            val translation = floatArrayOf(note.positionX, note.positionY, note.positionZ)
                            val rotation = floatArrayOf(0f, 0f, 0f, 1f)
                            val pose = com.google.ar.core.Pose(translation, rotation)
                            val anchor = arSession.createAnchor(pose)
                            
                            val anchorNode = AnchorNode(anchor)
                            anchorNode.setParent(arFragment?.arSceneView?.scene)
                            
                            val imageUri = if (note.imageUrl.isNotEmpty()) Uri.parse(note.imageUrl) else null
                            val audioPath = if (note.audioUrl.isNotEmpty()) note.audioUrl else null
                            
                            placeNote(anchorNode, note.text, imageUri, audioPath, isLoadedNote = true)
                            anchorNoteMap[anchorNode] = note.id
                            
                            Toast.makeText(this, "Legacy note loaded - realign to update", Toast.LENGTH_SHORT).show()
                        }
                            
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to load note", e)
                            Toast.makeText(this, "Failed to load note: ${e.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    Toast.makeText(this, "Notes loaded!", Toast.LENGTH_SHORT).show()
                }, 2000) // Wait 2 seconds for AR to initialize
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

    // ============================================================
    // TUTORIAL METHODS
    // ============================================================

    private fun initializeTutorial() {
        tutorialOverlay = findViewById(R.id.tutorialOverlay)
        tutorialStepIndicator = findViewById(R.id.tutorialStepIndicator)
        tutorialIcon = findViewById(R.id.tutorialIcon)
        tutorialTitle = findViewById(R.id.tutorialTitle)
        tutorialMessage = findViewById(R.id.tutorialMessage)
        tutorialNextButton = findViewById(R.id.tutorialNextButton)
        tutorialSkipButton = findViewById(R.id.tutorialSkipButton)
        tutorialArrow = findViewById(R.id.tutorialArrow)

        // Check if tutorial has been completed
        val tutorialCompleted = sharedPreferences.getBoolean(TUTORIAL_COMPLETED_KEY, false)
        
        if (!tutorialCompleted) {
            // Show tutorial on first launch
            showTutorial()
        } else {
            // Hide tutorial overlay
            tutorialOverlay.visibility = View.GONE
        }

        // Set up button listeners
        tutorialNextButton.setOnClickListener {
            nextTutorialStep()
        }

        tutorialSkipButton.setOnClickListener {
            skipTutorial()
        }
    }

    private fun showTutorial() {
        currentTutorialStep = 0
        tutorialOverlay.visibility = View.VISIBLE
        updateTutorialStep()
        
        // Fade in animation
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 300
        tutorialOverlay.startAnimation(fadeIn)
    }

    private fun nextTutorialStep() {
        currentTutorialStep++
        
        if (currentTutorialStep > 2) {
            // Tutorial complete
            completeTutorial()
        } else {
            updateTutorialStep()
        }
    }

    private fun updateTutorialStep() {
        when (currentTutorialStep) {
            0 -> {
                tutorialStepIndicator.text = "STEP 1 OF 3"
                tutorialIcon.text = "📱"
                tutorialTitle.text = "Welcome to AR Memory Palace"
                tutorialMessage.text = "Move your phone slowly to scan your environment and detect surfaces"
                tutorialArrow.visibility = View.GONE
                tutorialNextButton.text = "NEXT"
            }
            1 -> {
                tutorialStepIndicator.text = "STEP 2 OF 3"
                tutorialIcon.text = "👆"
                tutorialTitle.text = "Place Your Notes"
                tutorialMessage.text = "Tap on a detected surface (you'll see white dots) to place a spatial note"
                tutorialArrow.visibility = View.VISIBLE
                animateArrow()
                tutorialNextButton.text = "NEXT"
            }
            2 -> {
                tutorialStepIndicator.text = "STEP 3 OF 3"
                tutorialIcon.text = "🎤📷✍️"
                tutorialTitle.text = "Add Rich Content"
                tutorialMessage.text = "When placing a note, you can choose to add text, images, or audio recordings"
                tutorialArrow.visibility = View.GONE
                tutorialNextButton.text = "GET STARTED"
            }
        }
        
        // Fade transition animation
        val fadeTransition = AlphaAnimation(0.7f, 1f)
        fadeTransition.duration = 200
        tutorialTitle.startAnimation(fadeTransition)
        tutorialMessage.startAnimation(fadeTransition)
    }

    private fun animateArrow() {
        // Pulsing animation for the arrow
        val pulse = AlphaAnimation(0.3f, 1f)
        pulse.duration = 800
        pulse.repeatMode = Animation.REVERSE
        pulse.repeatCount = Animation.INFINITE
        tutorialArrow.startAnimation(pulse)
    }

    private fun skipTutorial() {
        completeTutorial()
    }

    private fun completeTutorial() {
        // Mark tutorial as completed in SharedPreferences
        sharedPreferences.edit()
            .putBoolean(TUTORIAL_COMPLETED_KEY, true)
            .apply()
        
        // Fade out and hide
        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.duration = 300
        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                tutorialOverlay.visibility = View.GONE
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        tutorialOverlay.startAnimation(fadeOut)
    }

    fun replayTutorial() {
        // Public method to replay tutorial (can be called from menu)
        currentTutorialStep = 0
        showTutorial()
    }
}
