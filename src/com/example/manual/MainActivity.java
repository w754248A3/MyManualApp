package com.example.manual;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.view.View;
import android.graphics.Color;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 创建根布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.WHITE);

        // 2. 创建一个 TextView
        TextView textView = new TextView(this);
        textView.setText("Hello, No Gradle World!");
        textView.setTextSize(24);
        textView.setTextColor(Color.BLACK);
        layout.addView(textView);

        // 3. 创建一个 Button
        Button button = new Button(this);
        button.setText("Click Me");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Build Success!", Toast.LENGTH_SHORT).show();
                textView.setText("Clicked!");
            }
        });
        layout.addView(button);

        // 4. 设置 Activity 的内容视图
        setContentView(layout);
    }
}