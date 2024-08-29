package com.example.lastresort.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class ResourceCustom extends View {
    private Bitmap image;
    private int    number;
    private Paint  textPaint;
    private int    imageSize;
    private int    padding;

    public ResourceCustom(Context context, Bitmap image, int number, Paint textPaint) {
        super(context);
        this.image      = image;
        this.number     = number;
        this.textPaint  = textPaint;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw the image on the left side with padding
        padding = 10;  // Adjust padding as needed
        canvas.drawBitmap(image, padding, (getHeight() - imageSize) / 2, null);

        // Draw the number to the right of the image with padding
        int textX = padding + imageSize + padding;
        int textY = Integer.parseInt(String.valueOf((getHeight() + textPaint.getTextSize()) / 2));  // Center vertical alignment
        canvas.drawText(String.valueOf(number), textX, textY, textPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // Set a minimum size based on the text size and desired padding
        int minWidth = imageSize + padding * 3 + (int) textPaint.measureText(String.valueOf(number));
        int minHeight = imageSize + padding * 2;

        setMeasuredDimension(Math.max(minWidth, getSuggestedMinimumWidth()),
                Math.max(minHeight, getSuggestedMinimumHeight()));
    }
}
