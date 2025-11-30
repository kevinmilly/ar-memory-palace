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
import android.view.View

class MainActivity : AppCompatActivity() {

    private var arFragment: ArFragment? = null
    private var session: Session? = null
    private var installRequested = false
    private var noteCount = 0
    private var pendingAnchor: AnchorNode? = null

    companion object {
        private const val CAMERA_PERMISSION_CODE = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as? ArFragment

        // Check ARCore availability
        checkARCoreAvailability()
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
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                }
                configure(config)
            }

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

            // Create anchor at tap location
            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment?.arSceneView?.scene)
            
            pendingAnchor = anchorNode

            // Show dialog to enter note text
            showNoteInputDialog(anchorNode)
        }
    }

    private fun showNoteInputDialog(anchorNode: AnchorNode) {
        val input = EditText(this)
        input.hint = "Enter your note..."
        input.setPadding(50, 30, 50, 30)

        AlertDialog.Builder(this)
            .setTitle("Create AR Note")
            .setView(input)
            .setPositiveButton("Place") { dialog, _ ->
                val noteText = input.text.toString()
                if (noteText.isNotEmpty()) {
                    placeNote(anchorNode, noteText)
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

    private fun placeNote(anchorNode: AnchorNode, noteText: String) {
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

                // Create text label above cube
                val textView = TextView(this)
                textView.text = noteText
                textView.setBackgroundColor(android.graphics.Color.parseColor("#DD000000"))
                textView.setTextColor(android.graphics.Color.WHITE)
                textView.setPadding(20, 15, 20, 15)
                textView.textSize = 14f

                ViewRenderable.builder()
                    .setView(this, textView)
                    .build()
                    .thenAccept { renderable ->
                        val textNode = com.google.ar.sceneform.Node()
                        textNode.renderable = renderable
                        textNode.localPosition = Vector3(0f, 0.25f, 0f)
                        textNode.setParent(cubeNode)
                    }

                // Make cube clickable
                cubeNode.setOnTapListener { _, _ ->
                    showNoteDetailsDialog(noteText, anchorNode)
                }

                Toast.makeText(this, "Note placed!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showNoteDetailsDialog(noteText: String, anchorNode: AnchorNode) {
        AlertDialog.Builder(this)
            .setTitle("AR Note")
            .setMessage(noteText)
            .setPositiveButton("OK", null)
            .setNegativeButton("Delete") { dialog, _ ->
                anchorNode.anchor?.detach()
                anchorNode.setParent(null)
                Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }
}
