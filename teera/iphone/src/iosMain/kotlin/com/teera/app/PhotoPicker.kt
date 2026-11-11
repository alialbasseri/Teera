package com.teera.app

import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/** The system's photo library, for the profile picture and a report's picture. */
object PhotoPicker {
    // Held while the picker is up: UIKit keeps only a weak reference to its delegate.
    private var delegate: Delegate? = null

    private class Delegate(private val onPicked: (ByteArray) -> Unit) :
        NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

        override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            picker.dismissViewControllerAnimated(true, completion = null)
            delegate = null
            val data = image?.let { UIImageJPEGRepresentation(it, 0.9) } ?: return
            onPicked(data.toByteArray())
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, completion = null)
            delegate = null
        }
    }

    fun pick(onPicked: (ByteArray) -> Unit) {
        val top = topController() ?: return
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        val listener = Delegate(onPicked)
        delegate = listener
        picker.delegate = listener
        top.presentViewController(picker, animated = true, completion = null)
    }
}

/** The screen on top, to show the system's own windows over. */
fun topController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
