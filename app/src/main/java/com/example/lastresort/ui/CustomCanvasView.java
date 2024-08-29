package com.example.lastresort.ui;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.example.lastresort.R;
import com.example.lastresort.grid.Cell;
import com.example.lastresort.util.EventManager;

public class CustomCanvasView extends ViewGroup {
    private CustomCanvas background;
    private CustomCanvas foreground;
    private EventManager eventManager;

    public CustomCanvasView(Context context, int width, int height)
    {
        super(context);
        background = new CustomCanvas(context, width, height);
        foreground = new CustomCanvas(context, width, height);

        background = findViewById(R.id.background_layer);
        foreground = findViewById(R.id.foreground_layer);

    }

    public CustomCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); // Make sure it fills the parent
        background = findViewById(R.id.background_layer);
        foreground = findViewById(R.id.foreground_layer);

    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(l-90, t-490, r-90, b-490); // Layout children to fill this ViewGroup
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    public void setBackground(CustomCanvas background) {
        this.background = background;
    }

    public void setForeground(CustomCanvas foreground) {
        this.foreground = foreground;
    }

    public void init(int width, int height, EventManager eventManager)
    {
        this.eventManager = eventManager;
        background.init(width, height, eventManager, this);
        foreground.init(width, height, eventManager, this);

        background.setDrawingMode(0);
        foreground.setDrawingMode(1);
    }

    public void updateGrid(Cell[][] cells){
        background.updateGrid(cells);
        foreground.updateGrid(cells);
    }

    public void invalidate()
    {
        Log.d("ViewGroup", "Invalidated");
        background.invalidate();
        foreground.invalidate();
    }

    public void drawBackground()
    {
        background.invalidate();
    }
}
