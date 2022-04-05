package edu.temple.simpletunes;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    private ActivityResultLauncher<Intent> mActivityResultLauncher;
    private ActivityResultLauncher<Intent> folderLauncher;
    private static final int STORAGE_PERMISSION_CODE = 101;


    // Variables and initialization of MediaPlayerService service connection.
    // TODO: use functions available through mAudioControlsBinder to control media.
    // mMediaControlsBinder.play, pause, resume, stop, isPlaying.
    // resume and pause does not check if track is playing or already paused.
    private boolean isConnected = false;
    private MediaPlayerService.ControlsBinder mAudioControlsBinder;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            isConnected = true;
            mAudioControlsBinder = (MediaPlayerService.ControlsBinder) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isConnected = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind the MediaPlayerService to the MainActivity.
        Intent intent = new Intent(this, MediaPlayerService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        mActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode() == RESULT_OK && result.getData() == null){
                Log.d(TAG, "onActivityResult: data was null");
            }else{
                if (result.getData() != null) {
                    Uri audioFile = result.getData().getData();
                    Log.d(TAG, "onActivityResult: got URI " + audioFile.toString());

                    mediaPlayerPlay(audioFile);
                }
            }
        });
        folderLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode() == RESULT_OK && result.getData() == null){
                Log.d(TAG, "onActivityResult: data was null");
            }else{
                if (result.getData() != null) {
                    Uri uri = result.getData().getData();
                    Log.d(TAG, "onActivityResult: got URI " + uri.toString());
                    DocumentFile directory = DocumentFile.fromTreeUri(MainActivity.this, uri);
                    if(directory == null){
                        Log.d(TAG, "onActivityResult: got empty directory");
                    }else{
                        DocumentFile[] contents = directory.listFiles();
                        Log.d(TAG, "onCreate: Folder passed to MediaPlayerService. Items in folder: " + contents.length);
                        mediaPlayerPlayFolder(contents);
                    }
                }
            }
        });
    }

    public boolean checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
            return false;
        }else{
            Log.d(TAG, "checkPermission: permission granted");
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent i = new Intent();
                i.setAction(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("audio/mpeg");
                mActivityResultLauncher.launch(i);
            }
        }
    }

    @Override
    protected void onResume() {
        ImageButton browserButton = findViewById(R.id.browserButton);
        browserButton.setOnClickListener(view -> {
            if(checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE)){
                Intent i = new Intent();
                i.setAction(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("audio/mpeg");
                mActivityResultLauncher.launch(i);
            }
        });
        ImageButton folderButton = findViewById(R.id.libraryButton);
        folderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // https://www.programcreek.com/java-api-examples/?class=android.content.Intent&method=ACTION_OPEN_DOCUMENT_TREE
                Intent i = new Intent();
                i.setAction(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                folderLauncher.launch(i);
            }
        });

        ImageButton playPauseButton = findViewById(R.id.playPauseButton);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               mediaPlayerPauseOrStart();
            }
        });
        ImageButton skipNextButton = findViewById(R.id.skipNextButton);
        skipNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayerNext();
            }
        });

        ImageButton skipPrevButton = findViewById(R.id.skipPrevButton);
        skipPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayerPrev();
            }
        });
        super.onResume();
    }

    /**
     * The mediaPlayerPrev method is used to skip to the previously played track in the file.
     */
    private void mediaPlayerPrev() {
        if (isConnected) {
            mAudioControlsBinder.playPrev();
        }
    }
    /**
     * The mediaPlayerNext method is used to skip to the next track in the file.
     */
    private void mediaPlayerNext() {
        if (isConnected) {
            mAudioControlsBinder.playNext();
        }
    }

    /**
     * The mediaPlayerPauseOrStart method is used to pause the current track or start from the
     * paused position. Checks if service is bound first.
     */
    private void mediaPlayerPauseOrStart() {
        if (isConnected) {
            if (mAudioControlsBinder.isPlaying()){
                mAudioControlsBinder.pause();
            } else if(!mAudioControlsBinder.isPlaying()){
                mAudioControlsBinder.resume();
            }
        }
    }

    /**
     * The mediaPlayerPlay method is used to start the MediaPlayerService and
     * also play the associated Uri.
     * @param myUri The Uri to start playing.
     */
    private void mediaPlayerPlay(Uri myUri) {
        if (isConnected) // Start service if first time playing a track.
            startService(new Intent(this, MediaPlayerService.class));
        mAudioControlsBinder.play(myUri);
    }

    /**
     * The mediaPlayerPlayFolder plays the entire folder found in a DocumentFile array. Stops after
     * last file is completed playing.
     * @param folder The DocumentFile array to play all audio files from.
     */
    private void mediaPlayerPlayFolder(DocumentFile[] folder) {
        if (isConnected)
            startService(new Intent(this, MediaPlayerService.class));
        mAudioControlsBinder.playFolder(folder);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        if (!isChangingConfigurations())
            stopService(new Intent(this, MediaPlayerService.class));
    }
}