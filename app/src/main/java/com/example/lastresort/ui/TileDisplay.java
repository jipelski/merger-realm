package com.example.lastresort.ui;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.example.lastresort.R;

public class TileDisplay extends View {
    private ImageView groundView = findViewById(R.id.groundImage);
    private ImageView objectView = findViewById(R.id.objectImage);

    public TileDisplay(Context context) {
        super(context);
        init(context);
    }

    public TileDisplay(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init(context);
    }

    private void init(Context context)
    {
        groundView = findViewById(R.id.groundImage);
        objectView = findViewById(R.id.objectImage);
    }

    public void setGroundView(int imageResource)
    {
        Log.d("TileDisplay", "objectView is null? " + (groundView == null)); // Check if objectView is null
        //groundView.setImageResource(imageResource);
    }
    public void setObjectImage(int imageResource) {
        Log.d("TileDisplay", "objectView is null? " + (objectView == null)); // Check if objectView is null
        //inflate();
        Log.d("TileDisplay", "objectView is null? second check " + (objectView == null)); // Check if objectView is null
        //objectView.setImageResource(imageResource);
    }
}
