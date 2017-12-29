package bmtreuherz.cannons

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.util.Log
import android.view.*
import android.widget.Toast
import bmtreuherz.cannons.rendering.BackgroundRenderer
import bmtreuherz.cannons.rendering.ObjectRenderer
import bmtreuherz.cannons.rendering.PlaneRenderer
import com.google.ar.core.*
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer{

    // Tag used for logging
    companion object {
        const val TAG = "MainActivity"
    }

    // UI Components
    lateinit private var surfaceView: GLSurfaceView
    private var snackbar: Snackbar? = null

    // AR Session and state
    lateinit private var session: Session
    lateinit private var display: Display

    // Tap Handling
    private var queuedTaps = ArrayBlockingQueue<MotionEvent>(16)

    // Renderers
    private var backgroundRenderer = BackgroundRenderer()
    private var planeRenderer = PlaneRenderer()
    private var objectRenderer = ObjectRenderer()

    // TODO: REMOVE
    var anchor: Anchor? = null
    var anchor1: Anchor? = null
    var anchor2: Anchor? = null
    var playerOneTurn: Boolean = true

    // Activity Methods
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set everything up
        if (!setupARSession()){
            finish()
            return
        }
        setupSurfaceView()
    }

    override fun onResume() {
        super.onResume()

        // Check if we have camera permissions
        if (CameraPermissionHelper.hasCameraPermission(this)){
            // Resume the session and setup state
            showSnackbarMessage("Searching for surfaces...")
            session.resume()
            surfaceView.onResume()
        } else{
            // Ask for permissions
            CameraPermissionHelper.requestCameraPermission(this)
        }
    }

    override fun onPause() {
        super.onPause()

        hideSnackbarMessage()
        surfaceView.onPause()
        session.pause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!CameraPermissionHelper.hasCameraPermission(this)){
            Toast.makeText(this, "Camera permission is needed to run this app", Toast.LENGTH_LONG).show()

            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // User checked "Do not ask again" so launch settings
                CameraPermissionHelper.launchPermissionSetting(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Requred for full-screen functionality
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Setup Methods
    private fun setupARSession(): Boolean{

        var exception: Exception? = null
        var message: String? = null

        // Try to create the session
        try{
            session = Session(this)
        } catch (e: UnavailableArcoreNotInstalledException) {
            message = "Please install ARCore"
            exception = e
        } catch (e: UnavailableApkTooOldException ) {
            message = "Please update ARCore"
            exception = e
        } catch (e: UnavailableSdkTooOldException) {
            message = "Please update this app"
            exception = e
        } catch (e: Exception) {
            message = "This device does not support AR"
            exception = e
        }

        // Handle any errors
        if (message != null){
            Log.e(TAG, "Exception creating session", exception)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return false
        }

        // Create the default config
        var config = Config(session)
        if (!session.isSupported(config)){
            Toast.makeText(this, "This device does not support AR", Toast.LENGTH_LONG).show()
            return false
        }

        session.configure(config)

        return true
    }

    private fun setupSurfaceView(){
        display = getSystemService(WindowManager::class.java).defaultDisplay
        surfaceView = findViewById(R.id.surfaceView)

        // Setup the tap listener
        var gestureDetector = GestureDetector(this, object: GestureDetector.SimpleOnGestureListener(){
            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                queuedTaps.offer(e)
                return true
            }

            override fun onDown(e: MotionEvent?): Boolean = true
        })
        surfaceView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        // Setup renderer
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    // GLSurfaceView.Renderer Methods

    override fun onSurfaceCreated(p0: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.1f)

        // Setup the background renderer and set the camera texture
        backgroundRenderer.createOnGlThread(this)
        session.setCameraTextureName(backgroundRenderer.textureId)

        // Setup the plane renderer
        try{
            planeRenderer.createOnGlThread(this, "trigrid.png")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read plan texture")
        }

        // Setup the object renderer
        try {
            objectRenderer.createOnGlThread(this, "andy.obj", "andy.png")
            objectRenderer.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read obj file")
        }

    }

    override fun onDrawFrame(p0: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        try {
            // Obtain the current frame from the ARSession. By default, rendering is throttled to
            // the camera framerate.
            var frame = session.update()
            var camera = frame.camera

            // Determine the projection maatrix
            val projM = FloatArray(16)
            camera.getProjectionMatrix(projM, 0, 0.1f, 100.0f)

            // Handle taps
            handleTaps(frame)

            // Draw the background
            drawBackground(frame)

            // If not tracking, don't draw 3d objects
            if (camera.trackingState != Trackable.TrackingState.TRACKING){
                return
            }

            // Check if we have detected planes
            checkPlanes()

            // Draw planes
            drawPlanes(camera, projM)

            // Draw objects
            drawObjects(frame, camera, projM)

        } catch(t: Throwable) {
            Log.e(TAG, "Exception on the OpenGL thread", t)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        // Notify the session that the view has changed so the perspective matrix and background
        // can be adjusted properly
        session.setDisplayGeometry(display.rotation, width, height)
    }


    // Drawing helper methods
    private fun drawPlanes(camera: Camera, projM: FloatArray){
        var planes = session.getAllTrackables(Plane::class.java)
        planeRenderer.drawPlanes(planes, camera.displayOrientedPose, projM)
    }

    // When detecting the first plane, change the state.
    private fun checkPlanes(){
        if (snackbar != null){
            for (plane: Plane in session.getAllTrackables(Plane::class.java)){
                if (plane.type == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING
                        && plane.trackingState == Trackable.TrackingState.TRACKING) {
                    when {
                        anchor1 == null -> showSnackbarMessage("Player one place your cannon.")
                        anchor2 == null -> showSnackbarMessage("Player two place your cannon.")
                        else -> when (playerOneTurn) {
                            true -> showSnackbarMessage("Player one's turn.")
                            false -> showSnackbarMessage("Player two's turn.")
                        }
                    }
                }
            }
        }
    }

    private fun drawBackground(frame: Frame){
        backgroundRenderer.draw(frame)
    }

    private fun handleTaps(frame: Frame){
        // TODO: Implement
    }

    private fun drawObjects(frame: Frame, camera: Camera, projM: FloatArray){
        // TODO: Do this a real way
        if (anchor == null) {
            anchor = session.createAnchor(camera.pose)
        }

        val objLocation = FloatArray(16)
        anchor?.pose?.toMatrix(objLocation, 0)
        objectRenderer.updateModelMatrix(objLocation, 0.4f)

        // TODO: Clean this up
        val viewM = FloatArray(16)
        camera.getViewMatrix(viewM, 0)

        val lightIntensity = frame.lightEstimate.pixelIntensity

        objectRenderer.draw(viewM, projM, lightIntensity)
    }


    // Snackbar stuff
    private fun showSnackbarMessage(message: String){
        runOnUiThread {
            if (snackbar == null){
                snackbar = Snackbar.make(this@MainActivity.findViewById(android.R.id.content), message, Snackbar.LENGTH_INDEFINITE)
                snackbar?.view?.setBackgroundColor(0xbf323232.toInt())
                snackbar?.show()
            } else {
                snackbar?.setText(message)
            }
        }
    }

    private fun hideSnackbarMessage(){
        runOnUiThread {
            snackbar?.dismiss()
            snackbar = null
        }
    }
}


// TODO: Draw shadows to improve realism