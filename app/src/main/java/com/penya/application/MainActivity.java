package com.penya.application;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.view.KeyEvent.KEYCODE_VOLUME_DOWN;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 200;
    private static final int SPEECH_REQUEST_CODE = 0;

    private boolean isSpeaking = false;

    private void displaySpeechRecognizer() {
        if (!isSpeaking) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            ArrayList<String> spokenTexts = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String spokenText = spokenTexts.get(0);
            Log.d("SpeechRecognizer", "Recognized text: " + spokenText);

            if (spokenText.equalsIgnoreCase("start scanning")) {
                Log.d("SpeechRecognizer", "Voice command recognized: start scanning");
                findViewById(R.id.buttonstart).performClick();
            } else if (spokenText.equalsIgnoreCase("start youtube")) {
                Log.d("SpeechRecognizer", "Voice command recognized: start youtube");
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
                if (launchIntent != null) {
                    startActivity(launchIntent);
                } else {
                    Toast.makeText(this, "YouTube app is not installed on this device.", Toast.LENGTH_SHORT).show();
                }
            } else if (spokenText.equalsIgnoreCase("repeat")) {
                Log.d("SpeechRecognizer", "Voice command recognized: repeat");
                readTextAloud();
            } else {
                Log.d("SpeechRecognizer", "Unrecognized voice command: " + spokenText);
                textToSpeech.speak("Please give the correct command", TextToSpeech.QUEUE_FLUSH, null, "UnrecognizedCommand");
            }
        }
    }

    private SurfaceView surfaceView;
    private TextView textView;
    private CameraSource cameraSource;
    private TextRecognizer textRecognizer;
    private TextToSpeech textToSpeech;
    private String stringResult = null;
    private Handler stopScanHandler;

    private SpeechRecognizer speechRecognizer;

    private Runnable stopScanRunnable = new Runnable() {
        @Override
        public void run() {
            stopCameraScan();
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KEYCODE_VOLUME_UP == keyCode) {
            displaySpeechRecognizer();  // Call the method to display Google Speech Recognizer dialog
            return true;
        } else if (KEYCODE_VOLUME_DOWN == keyCode) {
            readTextAloud();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request permissions
        if (checkPermission()) {
            initializeComponents();
        } else {
            requestPermission();
        }
    }

    private boolean checkPermission() {
        int cameraPermission = ContextCompat.checkSelfPermission(this, CAMERA);
        int recordAudioPermission = ContextCompat.checkSelfPermission(this, RECORD_AUDIO);
        return cameraPermission == PackageManager.PERMISSION_GRANTED &&
                recordAudioPermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{CAMERA, RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean recordAudioAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                if (cameraAccepted && recordAudioAccepted) {
                    initializeComponents();
                } else {
                    Toast.makeText(this, "Permissions Denied. The app cannot function without required permissions.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void initializeComponents() {
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech.setLanguage(Locale.getDefault());
                    textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            isSpeaking = true;
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            isSpeaking = false;
                            if ("NoTextDetected".equals(utteranceId) || "UnrecognizedCommand".equals(utteranceId)) {
                                displaySpeechRecognizer();
                            }
                        }

                        @Override
                        public void onError(String utteranceId) {
                            isSpeaking = false;
                        }
                    });
                }
            }
        });

        textView = findViewById(R.id.textView);
        textView.setText("");
        stopScanHandler = new Handler();

        // Start listening for the voice command
        displaySpeechRecognizer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }

    private void textRecognizer() {
        textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
        if (!textRecognizer.isOperational()) {
            return;
        }
        cameraSource = new CameraSource.Builder(getApplicationContext(), textRecognizer)
                .setRequestedPreviewSize(1280, 1024)
                .setAutoFocusEnabled(true)
                .build();

        surfaceView = findViewById(R.id.surfaceView);

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @SuppressLint("MissingPermission")
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                try {
                    cameraSource.start(surfaceView.getHolder());
                    if (stopScanHandler != null) {
                        stopScanHandler.postDelayed(stopScanRunnable, 10000);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                cameraSource.stop();
            }
        });

        textRecognizer.setProcessor(new Detector.Processor<TextBlock>() {
            @Override
            public void release() {
            }

            @Override
            public void receiveDetections(@NonNull Detector.Detections<TextBlock> detections) {
                SparseArray<TextBlock> sparseArray = detections.getDetectedItems();
                StringBuilder stringBuilder = new StringBuilder();

                for (int i = 0; i < sparseArray.size(); ++i) {
                    TextBlock textBlock = sparseArray.valueAt(i);
                    if (textBlock != null && textBlock.getValue() != null) {
                        stringBuilder.append(textBlock.getValue()).append(" ");
                    }
                }
                final String stringText = stringBuilder.toString();
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        stringResult = stringText;
                        if (stringResult.isEmpty()) {
                            notifyNoTextDetected();
                        } else {
                            resultObtained();
                        }
                    }
                });
            }
        });
    }

    private void resultObtained() {
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        textView.setText(stringResult);
        textToSpeech.speak(stringResult, TextToSpeech.QUEUE_FLUSH, null, "ExtractedText");
    }

    private void notifyNoTextDetected() {
        setContentView(R.layout.activity_main);
        textToSpeech.speak("No text detected. Please give the correct command.", TextToSpeech.QUEUE_FLUSH, null, "NoTextDetected");
    }

    private void readTextAloud() {
        if (stringResult != null) {
            textToSpeech.speak(stringResult, TextToSpeech.QUEUE_FLUSH, null, "ExtractedText");
        }
    }

    private void stopCameraScan() {
        if (cameraSource != null) {
            cameraSource.stop();
        }
        if (stopScanHandler != null) {
            stopScanHandler.removeCallbacks(stopScanRunnable);
        }
    }

    public void buttonstart(View view) {
        Log.d("MainActivity", "buttonstart method called");
        setContentView(R.layout.surfaceview);
        stopScanHandler = new Handler();
        textRecognizer();
    }
}
