package com.example.lastresort.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.example.lastresort.R;

public class CustomCellView extends FrameLayout {

    private ImageView backgroundImage;
    private Button button;
    private int weight;

    public CustomCellView(Context context) {
        super(context);
        init(context);
    }

    public CustomCellView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CustomCellView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Inflate layout from XML or programmatically create views
        inflate(context, R.layout.custom_cell_view, this);
        backgroundImage = findViewById(R.id.background_image);
        button = findViewById(R.id.button);
    }

    public void setBackgroundImage(Drawable drawable) {
        backgroundImage.setImageDrawable(drawable);
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    // You can add more methods as needed
}
