package com.example.androidscreenrecorderedmt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.VideoView;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 1000;
    private static final int REQUEST_PERMISSION = 1001;
    private static final SparseIntArray ORIENTATION = new SparseIntArray();

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaProjectionCallBack mediaprojectionCallBack;
    private MediaRecorder mediaRecorder;


    private int mScreenDensity;
    private static final int DISPLAY_WIDTH = 720;
    private static final int DISPLAY_HEIGHT=1200;

    static {
        ORIENTATION.append(Surface.ROTATION_0,90);
        ORIENTATION.append(Surface.ROTATION_0,0);
        ORIENTATION.append(Surface.ROTATION_0,270);
        ORIENTATION.append(Surface.ROTATION_0,180);
    }

    //View
    private RelativeLayout rootLayout;
    private ToggleButton toggleButton;
    private VideoView videoView;
    private String videoUri;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        mediaRecorder = new MediaRecorder();
        mediaProjectionManager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // View
        videoView = (VideoView)findViewById(R.id.videoView);
        toggleButton = (ToggleButton)findViewById(R.id.toggleButton);
        rootLayout = (RelativeLayout)findViewById(R.id.rootLayout);

        // Event
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                + ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
                {
                    if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    || ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,Manifest.permission.RECORD_AUDIO))
                    {
                        toggleButton.setChecked(false);
                        Snackbar.make(rootLayout, "Permissions", Snackbar.LENGTH_INDEFINITE)
                                .setAction("ENABLE", new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {

                                        ActivityCompat.requestPermissions(MainActivity.this,
                                                new String[]{

                                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                        Manifest.permission.RECORD_AUDIO

                                                },REQUEST_PERMISSION);
                                    }
                                }).show();
                    }
                    else
                    {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{

                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        Manifest.permission.RECORD_AUDIO

                                },REQUEST_PERMISSION);
                    }
                }
                else
                {
                    toggleScreenShare(v);
                }
            }
        });
    }

    private void toggleScreenShare(View v) {
        if(((ToggleButton)v).isChecked())
        {
            initRecorder();
            recordScreen();
        }
        else
        {
            mediaRecorder.stop();
            mediaRecorder.reset();
            stopRecordScreen();

            // play in video view
            videoView.setVisibility(View.VISIBLE);
            videoView.setVideoURI(Uri.parse(videoUri));
            videoView.start();
        }
    }

    private void recordScreen() {
        if(mediaProjection == null)
        {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),REQUEST_CODE);
            return;
        }
        virtualDisplay = createVirtualDisplay();
        mediaRecorder.start();
    }

    private VirtualDisplay createVirtualDisplay() {
        return mediaProjection.createVirtualDisplay("MainActivity",DISPLAY_WIDTH,DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(), null, null);
    }

    private void initRecorder() {
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

            videoUri = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    +new StringBuilder("/EDMTRecord").append(new SimpleDateFormat("dd-MM-yyyy-hh_mm_ss")
                    .format(new Date())).append(".mp4").toString();

            mediaRecorder.setOutputFile(videoUri);
            mediaRecorder.setVideoSize(DISPLAY_WIDTH,DISPLAY_HEIGHT);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setVideoEncodingBitRate(512*1000);
            mediaRecorder.setVideoFrameRate(30);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATION.get(rotation+90);
            mediaRecorder.setOrientationHint(orientation);
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode != REQUEST_CODE)
        {
            Toast.makeText(this,"Unk error", Toast.LENGTH_SHORT).show();
            return;
        }

        if(resultCode != RESULT_OK) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            toggleButton.setChecked(false);
            return;
        }

        mediaprojectionCallBack = new MediaProjectionCallBack();
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode,data);
        mediaProjection.registerCallback(mediaprojectionCallBack, null);
        virtualDisplay = createVirtualDisplay();
        mediaRecorder.start();
    }

    private class MediaProjectionCallBack extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if(toggleButton.isChecked())
            {
                toggleButton.setChecked(false);
                mediaRecorder.stop();
                mediaRecorder.reset();
            }
            mediaProjection = null;
            stopRecordScreen();
            super.onStop();
        }
    }

    private void stopRecordScreen() {
        if(virtualDisplay == null)
            return;
        virtualDisplay.release();
        destroyMediaProjection();

    }

    private void destroyMediaProjection() {
        if(mediaProjection != null)
        {
            mediaProjection.unregisterCallback(mediaprojectionCallBack);
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode)
        {
            case REQUEST_PERMISSION:
            {
                if((grantResults.length > 0) && (grantResults[0] + grantResults[1] == PackageManager.PERMISSION_GRANTED))
                {
                    toggleScreenShare(toggleButton);
                }
                else
                {
                   toggleButton.setChecked(false);
                    Snackbar.make(rootLayout, "Permissions", Snackbar.LENGTH_INDEFINITE)
                            .setAction("Enable", new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {

                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            new String[]{

                                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                    Manifest.permission.RECORD_AUDIO

                                            },REQUEST_PERMISSION);
                                }
                            }).show();
                }
                return;
            }
        }
    }
}