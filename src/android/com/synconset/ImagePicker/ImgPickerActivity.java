package com.synconset;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.engine.impl.GlideEngine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImgPickerActivity extends Activity {

    private static int REQUEST_CODE_CHOOSE = 23;

    //Params
    public static String MAX_IMAGES_KEY = "MAX_IMAGES";
    public static String WIDTH_KEY = "WIDTH";
    public static String HEIGHT_KEY = "HEIGHT";
    public static String QUALITY_KEY = "QUALITY";
    public static String OUTPUT_TYPE_KEY = "OUTPUT";

    private int quality;
    private int desiredWidth;
    private int desiredHeight;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int maxImages = getIntent().getIntExtra(MAX_IMAGES_KEY, 200);
        desiredWidth = getIntent().getIntExtra(WIDTH_KEY, 0);
        desiredHeight = getIntent().getIntExtra(HEIGHT_KEY, 0);
        quality = getIntent().getIntExtra(QUALITY_KEY, 0);
        selectImages(maxImages, desiredWidth, desiredHeight, quality);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {


            ArrayList<String> files = new ArrayList<String>();
            List<Uri> fileUris = Matisse.obtainResult(data);

            for(Uri uri:fileUris) {
                files.add(uri.toString());
            }

            Intent res = new Intent();
            res.putStringArrayListExtra("MULTIPLEFILENAMES", files);
            data.putExtras(res);

            setResult(RESULT_OK, data);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private void selectImages(int max, int desiredWidth, int desiredHeight, int quality) {
        Matisse.from(this)
                .choose(MimeType.of(MimeType.JPEG, MimeType.PNG, MimeType.GIF))
                .countable(true)
                .maxSelectable(max)
                .restrictOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                .thumbnailScale(0.85f)
                .imageEngine(new GlideEngine())
                .forResult(REQUEST_CODE_CHOOSE);
    }
}

