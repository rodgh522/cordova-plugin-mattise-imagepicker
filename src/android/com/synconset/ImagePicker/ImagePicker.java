/**
 * An Image Picker Plugin for Cordova/PhoneGap.
 */
package com.synconset;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class ImagePicker extends CordovaPlugin {

    private static final String ACTION_GET_PICTURES = "getPictures";
    private static final String ACTION_HAS_READ_PERMISSION = "hasReadPermission";
    private static final String ACTION_REQUEST_READ_PERMISSION = "requestReadPermission";

    private static final int PERMISSION_REQUEST_CODE = 100;

    private int max = 20;
    private int desiredWidth = 0;
    private int desiredHeight = 0;
    private int quality = 100;
    private int outputType = 0;

    private CallbackContext callbackContext;

    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        if (ACTION_HAS_READ_PERMISSION.equals(action)) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, hasReadPermission()));
            return true;

        } else if (ACTION_REQUEST_READ_PERMISSION.equals(action)) {
            requestReadPermission();
            return true;

        } else if (ACTION_GET_PICTURES.equals(action)) {
            final JSONObject params = args.getJSONObject(0);
            final Intent imagePickerIntent = new Intent(cordova.getActivity(), ImgPickerActivity.class);
            max = 20;
            desiredWidth = 0;
            desiredHeight = 0;
            quality = 100;
            outputType = 0;

            if (params.has("maximumImagesCount")) {
                max = params.getInt("maximumImagesCount");
            }
            if (params.has("width")) {
                desiredWidth = params.getInt("width");
            }
            if (params.has("height")) {
                desiredHeight = params.getInt("height");
            }
            if (params.has("quality")) {
                quality = params.getInt("quality");
            }
            if (params.has("outputType")) {
                outputType = params.getInt("outputType");
            }

            imagePickerIntent.putExtra("MAX_IMAGES", max);
            imagePickerIntent.putExtra("WIDTH", desiredWidth);
            imagePickerIntent.putExtra("HEIGHT", desiredHeight);
            imagePickerIntent.putExtra("QUALITY", quality);
            imagePickerIntent.putExtra("OUTPUT", outputType);

            // some day, when everybody uses a cordova version supporting 'hasPermission', enable this:
            /*
            if (cordova != null) {
                 if (cordova.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    cordova.startActivityForResult(this, imagePickerIntent, 0);
                 } else {
                     cordova.requestPermission(
                             this,
                             PERMISSION_REQUEST_CODE,
                             Manifest.permission.READ_EXTERNAL_STORAGE
                     );
                 }
             }
             */
            // .. until then use:
            if (hasReadPermission()) {
                cordova.startActivityForResult(this, imagePickerIntent, 0);
            } else {
                requestReadPermission();
                // The downside is the user needs to re-invoke this picker method.
                // The best thing to do for the dev is check 'hasReadPermission' manually and
                // run 'requestReadPermission' or 'getPictures' based on the outcome.
            }
            return true;
        }
        return false;
    }

    @SuppressLint("InlinedApi")
    private boolean hasReadPermission() {
        return Build.VERSION.SDK_INT < 23 ||
                PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this.cordova.getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    @SuppressLint("InlinedApi")
    private void requestReadPermission() {
        if (!hasReadPermission()) {
            ActivityCompat.requestPermissions(
                    this.cordova.getActivity(),
                    new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
        // This method executes async and we seem to have no known way to receive the result
        // (that's why these methods were later added to Cordova), so simply returning ok now.
        callbackContext.success();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> names = data.getStringArrayListExtra("MULTIPLEFILENAMES");
            ArrayList<String> result = new ArrayList<>();
            JSONArray res = null;
            if(outputType == 0){
                res = new JSONArray(names);
            }else if(outputType == 1) {
                for(String uri: names) {
                    result.add(getBase64OfImage(uri));
                }
                res = new JSONArray(result);
            }

            this.callbackContext.success(res);
        }else {
            callbackContext.error("No images selected");
        }
    }

    private String getBase64OfImage(String content) {

        Bitmap bmp;
        try {

            Uri uri = Uri.parse(content);
            File file = new File(getPath(uri));
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            int width = options.outWidth;
            int height = options.outHeight;
            float scale = calculateScale(width, height);
            int finalWidth = (int)(width * scale);
            int finalHeight = (int)(height * scale);
            int inSampleSize = calculateInSampleSize(options, finalWidth, finalHeight);
            options = new BitmapFactory.Options();
            options.inSampleSize = inSampleSize;

            try {
                bmp = this.tryToGetBitmap(file, null, 0, false);
            } catch(OutOfMemoryError e) {
                options = new BitmapFactory.Options();
                options.inSampleSize = 2;

                try {
                    bmp = this.tryToGetBitmap(file, options, 0, false);
                } catch (OutOfMemoryError e2) {
                    options = new BitmapFactory.Options();
                    options.inSampleSize = 4;

                    try {
                        bmp = this.tryToGetBitmap(file, options, 0, false);
                    } catch (OutOfMemoryError e3) {
                        throw new IOException("Unable to load image into memory.");
                    }
                }
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();

            return Base64.encodeToString(byteArray, Base64.NO_WRAP);
        } catch (IOException e) {
            return "";
        }
    }


    /**
     * Choosing a picture launches another Activity, so we need to implement the
     * save/restore APIs to handle the case where the CordovaActivity is killed by the OS
     * before we get the launched Activity's result.
     *
     * @see http://cordova.apache.org/docs/en/dev/guide/platforms/android/plugin.html#launching-other-activities
     */
    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
    }


    enum OutputType {

        FILE_URI(0), BASE64_STRING(1);

        int value;

        OutputType(int value) {
            this.value = value;
        }

        public static OutputType fromValue(int value) {
            for (OutputType type : OutputType.values()) {
                if (type.value == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid enum value specified");
        }
    }

    public byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    private Bitmap tryToGetBitmap(File file,
                                  BitmapFactory.Options options,
                                  int rotate,
                                  boolean shouldScale) throws IOException, OutOfMemoryError {
        Bitmap bmp;
        if (options == null) {
            bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
        } else {
            bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }

        if (bmp == null) {
            throw new IOException("The image file could not be opened.");
        }

        if (options != null && shouldScale) {
            float scale = calculateScale(options.outWidth, options.outHeight);
            bmp = this.getResizedBitmap(bmp, scale);
        }

        if (rotate != 0) {
            Matrix matrix = new Matrix();
            matrix.setRotate(rotate);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        }

        return bmp;
    }

    private float calculateScale(int width, int height) {
        float widthScale = 1.0f;
        float heightScale = 1.0f;
        float scale = 1.0f;
        if (desiredWidth > 0 || desiredHeight > 0) {
            if (desiredHeight == 0 && desiredWidth < width) {
                scale = (float)desiredWidth/width;

            } else if (desiredWidth == 0 && desiredHeight < height) {
                scale = (float)desiredHeight/height;

            } else {
                if (desiredWidth > 0 && desiredWidth < width) {
                    widthScale = (float)desiredWidth/width;
                }

                if (desiredHeight > 0 && desiredHeight < height) {
                    heightScale = (float)desiredHeight/height;
                }

                if (widthScale < heightScale) {
                    scale = widthScale;
                } else {
                    scale = heightScale;
                }
            }
        }

        return scale;
    }

    private Bitmap getResizedBitmap(Bitmap bm, float factor) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        // create a matrix for the manipulation
        Matrix matrix = new Matrix();
        // resize the bit map
        matrix.postScale(factor, factor);
        // recreate the new Bitmap
        return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private int calculateNextSampleSize(int sampleSize) {
        double logBaseTwo = (int)(Math.log(sampleSize) / Math.log(2));
        return (int)Math.pow(logBaseTwo + 1, 2);
    }

    private String getPath(Uri contentUri) {

        String[] proj = {MediaStore.Audio.Media.DATA};
        Cursor cursor = cordova.getActivity().getContentResolver().query(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
}
