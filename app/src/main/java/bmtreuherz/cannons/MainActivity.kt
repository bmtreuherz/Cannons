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
import com.google.ar.core.*
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
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
    lateinit private var state: State
    lateinit private var display: Display

    // Tap Handling
    private var queuedTaps = ArrayBlockingQueue<MotionEvent>(16)

    // Renderers
    private var backgroundRenderer = BackgroundRenderer()


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
            state = State.SEARCHING_FOR_SURFACES
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

    }

    override fun onDrawFrame(p0: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        try {
            // Obtain the current frame from the ARSession. By default, rendering is throttled to
            // the camera framerate.
            var frame = session.update()
            var camera = frame.camera

            // TODO: Handle taps

            // Draw the background
            backgroundRenderer.draw(frame)

            // If not tracking, don't draw 3d objects
            if (camera.trackingState != Trackable.TrackingState.TRACKING){
                return
            }

            // TODO: Draw 3d objects

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



    // Snackbar stuff
    private fun showSnackbarMessage(message: String){
        runOnUiThread {
            snackbar = Snackbar.make(this@MainActivity.findViewById(android.R.id.content), message, Snackbar.LENGTH_INDEFINITE)
            snackbar?.view?.setBackgroundColor(0xbf323232.toInt())
            snackbar?.show()
        }
    }

    private fun hideSnackbarMessage(){
        runOnUiThread {
            snackbar?.dismiss()
            snackbar = null
        }
    }
}


// TODO: Verify graceful exit on unsupported devices session could be null maybe?
// TODO: Verify what happens when camera permissions are denied