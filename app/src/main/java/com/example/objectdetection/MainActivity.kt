package com.example.objectdetection


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.IOException

class MainActivity : AppCompatActivity(), ObjectDetectorHelper.DetectorListener  {
    private val RESULT_LOAD_IMAGE = 123
    val IMAGE_CAPTURE_CODE = 654
    private val PERMISSION_CODE = 321
    var innerImage: ImageView? = null
    private var image_uri: Uri? = null
    private lateinit var objectDetectorHelper: ObjectDetectorHelper

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        innerImage = findViewById(R.id.imageView2)
        innerImage?.setOnClickListener(View.OnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, RESULT_LOAD_IMAGE)
        })

        innerImage?.setOnLongClickListener(OnLongClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED) {
                    val permission = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    requestPermissions(permission, PERMISSION_CODE)
                } else {
                    openCamera()
                }
            } else {
                openCamera()
            }
            false
        })

        //TODO ask for permission of camera upon first launch of application
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            val permission = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            requestPermissions(permission, PERMISSION_CODE)
        }

        //TODO intialize object detector
        objectDetectorHelper =
            ObjectDetectorHelper(threshold = 0.5f, context = applicationContext, maxResults = ObjectDetectorHelper.MAX_RESULTS_DEFAULT, currentDelegate = ObjectDetectorHelper.DELEGATE_CPU, modelName = "js_model.tflite", runningMode = RunningMode.IMAGE)



    }

    private fun openCamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Picture")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")
        image_uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri)
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            image_uri = data.data
            innerImage!!.setImageURI(image_uri)
            doInference()
        }
        if (requestCode == IMAGE_CAPTURE_CODE && resultCode == Activity.RESULT_OK) {
            innerImage!!.setImageURI(image_uri)
            doInference()
        }
    }

    //TODO pass image to the model and shows the results on screen
    private fun doInference() {
        //TODO convert image into bitmap and show image
        val bitmap = uriToBitmap(image_uri!!)
        val rotatedBmp = rotateBitmap(bitmap!!)
        innerImage!!.setImageBitmap(rotatedBmp)
        if (rotatedBmp != null) {
            val mutableBmp = rotatedBmp!!.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBmp)

            val p = Paint()
            p.color = Color.RED
            p.style = Paint.Style.STROKE
            p.strokeWidth = (mutableBmp.width / 95).toFloat()

            val paintText = Paint()
            paintText.color = Color.BLUE
            paintText.textSize = (mutableBmp.width / 10).toFloat()
            paintText.isFakeBoldText = true


            var resultBundle = objectDetectorHelper.detectImage(rotatedBmp)
            if(resultBundle != null){
                var resultsList = resultBundle.results
                for(singleResult in resultsList){
                    var detections = singleResult.detections()
                    for(singleDetection in detections){
                        singleDetection.boundingBox()
                        var categorieslist = singleDetection.categories()
                        var objectName = ""
                        var objectScore = 0f
                        for(singleCategory in categorieslist){
                            Log.d("tryRess",singleCategory.categoryName()+"   "+singleDetection.boundingBox().toString())
                            if(singleCategory.score()>objectScore){
                                objectScore = singleCategory.score()
                                objectName = singleCategory.categoryName()
                            }
                        }
                        canvas.drawRect(singleDetection.boundingBox(),p)
                        canvas.drawText(
                            objectName,
                            singleDetection.boundingBox().left,
                            singleDetection.boundingBox().top,
                            paintText
                        )
                    }
                }
                innerImage!!.setImageBitmap(mutableBmp)
            }
        }
    }


    //TODO takes URI of the image and returns bitmap
    private fun uriToBitmap(selectedFileUri: Uri): Bitmap? {
        try {
            val parcelFileDescriptor =
                contentResolver.openFileDescriptor(selectedFileUri, "r")
            val fileDescriptor = parcelFileDescriptor!!.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()
            return image
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    //TODO rotate image if image captured on samsung devices
    //Most phone cameras are landscape, meaning if you take the photo in portrait, the resulting photos will be rotated 90 degrees.
    @SuppressLint("Range")
    fun rotateBitmap(input: Bitmap): Bitmap? {
        val orientationColumn =
            arrayOf(MediaStore.Images.Media.ORIENTATION)
        val cur =
            contentResolver.query(image_uri!!, orientationColumn, null, null, null)
        var orientation = -1
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]))
        }
        Log.d("tryOrientation", orientation.toString() + "")
        val rotationMatrix = Matrix()
        rotationMatrix.setRotate(orientation.toFloat())
        return Bitmap.createBitmap(
            input,
            0,
            0,
            input.width,
            input.height,
            rotationMatrix,
            true
        )
    }


    override fun onDestroy() {
        super.onDestroy()
    }

    //If user gives permission then launch camera
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        }
    }

    override fun onError(error: String, errorCode: Int) {
        TODO("Not yet implemented")
    }

    override fun onResults(resultBundle: ObjectDetectorHelper.ResultBundle) {
        TODO("Not yet implemented")
    }

}