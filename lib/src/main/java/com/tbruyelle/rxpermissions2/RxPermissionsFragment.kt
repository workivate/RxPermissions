package com.tbruyelle.rxpermissions2

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.canDrawOverlays
import android.support.v4.app.Fragment
import android.util.Log
import io.reactivex.subjects.PublishSubject
import java.util.*

class RxPermissionsFragment : Fragment() {
    
    companion object {

        private const val RUNTIME_PERMISSIONS_REQUEST_CODE = 42
    }
    
    // Contains all the current permission requests.
    // Once granted or denied, they are removed from it.
    private val mSubjects = HashMap<String, PublishSubject<Permission>>()
    private var mLogging: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun requestPermissions(permissions: Array<String>) {
        requestPermissions(permissions, RUNTIME_PERMISSIONS_REQUEST_CODE)
    }

    fun requestSystemPermission(permission: SystemPermission) {
        val intent = Intent(permission.action, Uri.parse("package:" + requireContext().packageName))
        startActivityForResult(intent, permission.requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        SystemPermission.values().find { it.requestCode == requestCode }?.let {
            val subject = mSubjects[it.permissionName]
            if (subject == null) {
                // No subject found
                log("RxPermissions.onActivityResult invoked but didn't find the corresponding permission request.")
                return
            }
            mSubjects.remove(it.permissionName)
            val granted = it.enabledChecker.invoke(requireContext())
            subject.onNext(Permission(it.permissionName, granted, false))
            subject.onComplete()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != RUNTIME_PERMISSIONS_REQUEST_CODE) return

        val shouldShowRequestPermissionRationale = BooleanArray(permissions.size)

        for (i in permissions.indices) {
            shouldShowRequestPermissionRationale[i] = shouldShowRequestPermissionRationale(permissions[i])
        }

        onRequestPermissionsResult(permissions, grantResults, shouldShowRequestPermissionRationale)
    }

    fun onRequestPermissionsResult(permissions: Array<String>, grantResults: IntArray, shouldShowRequestPermissionRationale: BooleanArray) {
        permissions.forEachIndexed { i, permission ->
            log("onRequestPermissionsResult  $permission")
            // Find the corresponding subject
            val subject = mSubjects[permission]
            if (subject == null) {
                // No subject found
                log("RxPermissions.onRequestPermissionsResult invoked but didn't find the corresponding permission request.")
                return
            }
            mSubjects.remove(permission)
            val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
            subject.onNext(Permission(permission, granted, shouldShowRequestPermissionRationale[i]))
            subject.onComplete()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun isRuntimePermissionGranted(permission: String): Boolean {
        val fragmentActivity = activity
                ?: throw IllegalStateException("This fragment must be attached to an activity.")
        return fragmentActivity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun isRuntimePermissionRevoked(permission: String): Boolean {
        val fragmentActivity = activity
                ?: throw IllegalStateException("This fragment must be attached to an activity.")
        return fragmentActivity.packageManager.isPermissionRevokedByPolicy(permission, activity!!.packageName)
    }

    fun setLogging(logging: Boolean) {
        mLogging = logging
    }

    fun getSubjectByPermission(permission: String): PublishSubject<Permission>? {
        return mSubjects[permission]
    }

    fun containsByPermission(permission: String): Boolean {
        return mSubjects.containsKey(permission)
    }

    fun setSubjectForPermission(permission: String, subject: PublishSubject<Permission>) {
        mSubjects[permission] = subject
    }

    fun log(message: String) {
        if (mLogging) {
            Log.d(RxPermissions.TAG, message)
        }
    }
}
