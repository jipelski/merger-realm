package com.example.lastresort.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Button;

public class TileCustomLayout extends View {
    private Bitmap background;
    private Button object;
    private int weight;


    public TileCustomLayout(Context context, Bitmap backgroundImage, int weight) {
        super(context);
        this.background = backgroundImage;
        this.weight = weight;
    }


}
