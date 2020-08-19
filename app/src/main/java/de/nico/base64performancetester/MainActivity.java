package de.nico.base64performancetester;

import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();

    private String[] mFiles;
    private Map<String, TextView> mViews;
    private Map<String, byte[]> mFileBinaries;
    private Map<String, List<Pair<Long, Long>>> mFileResults;

    private FloatingActionButton mFAB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFAB = findViewById(R.id.floating_action_button);

        mFileBinaries = new HashMap<>();
        mViews = new HashMap<>();
        mFileResults = new HashMap<>();

        AssetManager am = getApplicationContext().getAssets();
        try {
            String assetPrefix = "input";
            mFiles = am.list(assetPrefix);

            LinearLayout container = findViewById(R.id.data_container);

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            for (String file : mFiles) {
                InputStream is = am.open(assetPrefix + "/" + file);
                byte[] targetArray = new byte[is.available()];
                is.read(targetArray);
                mFileBinaries.put(file, targetArray);

                mFileResults.put(file, new ArrayList<>());

                TextView headLine = new TextView(this);
                headLine.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
                headLine.setLayoutParams(layoutParams);
                headLine.setText(getString(R.string.headline_text, file));
                container.addView(headLine);
                mViews.put(file, headLine);

                TextView valueView = new TextView(this);
                valueView.setTypeface(valueView.getTypeface(), Typeface.BOLD);
                valueView.setTextSize(20);
                valueView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
                valueView.setLayoutParams(layoutParams);
                valueView.setText(getString(R.string.waiting));
                container.addView(valueView);
                mViews.put(file, valueView);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            finish();
            return;
        }
        am.close();

        mFAB.setOnClickListener(this::onClickFAB);
    }

    private void onClickFAB(View view) {
        toggleFAB(false);
        startPerformanceTest();
    }

    @UiThread
    private void assignValuesToView() {
        mViews.forEach((file, view) -> {
            int encodingSum = 0;
            int decodingSum = 0;
            List<Pair<Long, Long>> measurements = mFileResults.get(file);
            for (Pair<Long, Long> measurement : measurements) {
                encodingSum += measurement.first;
                decodingSum += measurement.second;
            }
            int itemCount = measurements.size();
            long encodingAverage = encodingSum / itemCount;
            long decodingAverage = decodingSum / itemCount;
            view.setText(getString(R.string.encoding_decoding, encodingAverage, decodingAverage));
        });
    }

    private void startPerformanceTest() {
        new Thread() {
            @Override
            public void run() {
                int iterations = 100;
                Base64.Encoder encoder = Base64.getEncoder();
                Base64.Decoder decoder = Base64.getDecoder();
                String encoded;
                while (--iterations != 0) {
                    for (String file : mFiles) {
                        byte[] binary = mFileBinaries.get(file);
                        long startTimestamp = System.nanoTime();
                        encoded = encoder.encodeToString(binary);
                        long encodedTimestamp = System.nanoTime();
                        decoder.decode(encoded);
                        long endTimestamp = System.nanoTime();
                        long encodingTime = (encodedTimestamp - startTimestamp) / 1000;
                        long decodingTime = (endTimestamp - encodedTimestamp) / 1000;
                        Log.i(TAG, String.format("Encoding %s took %sµs", file, encodingTime));
                        Log.i(TAG, String.format("Decoding %s took %sµs", file, decodingTime));
                        mFileResults.get(file).add(new Pair<>(encodingTime, decodingTime));
                    }
                }
                runOnUiThread(() -> {
                    assignValuesToView();
                    toggleFAB(true);
                });
            }
        }.start();
    }

    @UiThread
    private void toggleFAB(boolean enable) {
        mFAB.setEnabled(enable);
        mFAB.setImageResource(
                enable ? android.R.drawable.ic_media_play : android.R.drawable.ic_menu_rotate
        );
        mFAB.setBackgroundTintList(
                ColorStateList.valueOf(
                        ContextCompat.getColor(
                                this,
                                enable ?
                                        R.color.colorAccent :
                                        android.R.color.holo_red_light
                        )
                )
        );
    }
}