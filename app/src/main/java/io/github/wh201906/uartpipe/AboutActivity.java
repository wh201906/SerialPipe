package io.github.wh201906.uartpipe;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Button backButton = findViewById(R.id.backButton);
        TextView versionTextView = findViewById(R.id.versionTextView);
        backButton.setOnClickListener(v ->
        {
            AboutActivity.this.finish();
        });
        versionTextView.setText(getString(R.string.activity_about_version, BuildConfig.VERSION_NAME));
    }
}
