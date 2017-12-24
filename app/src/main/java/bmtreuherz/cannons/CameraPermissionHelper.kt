package bmtreuherz.cannons

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import java.util.jar.Manifest

/**
 * Created by Bradley on 12/23/17.
 */
object CameraPermissionHelper {
    private val CAMERA_PERMISSION = android.Manifest.permission.CAMERA
    private val REQUEST_CODE = 0

    // Check to see if we have camera permissions
    fun hasCameraPermission(activity: Activity): Boolean =
            ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED

    // Request camera permission
    fun requestCameraPermission(activity: Activity){
        ActivityCompat.requestPermissions(activity, arrayOf(CAMERA_PERMISSION), REQUEST_CODE)
    }

    // Check to see if we need to show the rationale for this permission
    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean =
            ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION)

    // Launch application setting to grant permission
    fun launchPermissionSetting(activity: Activity){
        var intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(intent)
    }
}