package com.example.lastresort.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.example.lastresort.R;
import com.example.lastresort.grid.Cell;
import com.example.lastresort.grid.Grid;
import com.example.lastresort.util.EventManager;

import java.util.ArrayList;
import java.util.Objects;

public class CustomCanvas extends View {

    private Bitmap defaultTileBitmap;
    private Cell[][] cells;
    private Paint paint = new Paint();
    private int width;
    private int height;

    private String selectedObject = null;
    private String prevSelected = null;

    private int selectedObjectInitialRow = -1;
    private int selectedObjectInitialColumn = -1;
    private float lastTouchX, lastTouchY;

    private EventManager eventManager;
    int cellSize; // Size of each cell in pixels

    int drawingMode = 0;

    boolean enabled;
    boolean dragStarted = false;
    private CustomCanvasView parent;

    public CustomCanvas(Context context, int width, int height) {
        super(context);
        this.width = width;
        this.height= height;
    }

    public CustomCanvas(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d("CustomCanvas", "Custom Instance Created AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw a rectangle or circle that is definitely within the view's bounds
        //canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        Log.d("OnDrawMethodwidth", String.valueOf(width));
        Log.d("OnDrawMethodheight", String.valueOf(height));

        switch (drawingMode)
        {
            case 0:
            {
                drawGrid(canvas);

            }break;
            case 1:
            {
                if(selectedObject != null)
                {
                    String id = selectedObject;
                    Resources resources = getContext().getResources();
                    int resourceId = resources.getIdentifier(getImageResource(id), "drawable", getContext().getPackageName());
                    Bitmap objBitmap = BitmapFactory.decodeResource(resources, resourceId);
                    int x = (int) lastTouchX - (cellSize / 2); // Center the bitmap on the touch point
                    int y = (int) lastTouchY - (cellSize / 2);
                    canvas.drawBitmap(objBitmap, null, new Rect(x, y, x + cellSize, y + cellSize), null);
                }
                }break;


        }



        // Draw the selected object at the last touch location
        /*if (selectedObject != null) {
            String id = selectedObject.getOccupant();
            Resources resources = getContext().getResources();
            int resourceId = resources.getIdentifier(getImageResource(id), "drawable", getContext().getPackageName());
            Bitmap objBitmap = BitmapFactory.decodeResource(resources, resourceId);
            int x = (int) lastTouchX - (cellSize / 2); // Center the bitmap on the touch point
            int y = (int) lastTouchY - (cellSize / 2);
            canvas.drawBitmap(objBitmap, null, new Rect(x, y, x + cellSize, y + cellSize), null);
        }*/
    }

    public void setDrawingMode(int mode)
    {
        this.drawingMode = mode;
        this.enabled = mode == 1;
    }


    private void drawGrid(Canvas canvas) {
        int numColumns = width; // Number of columns
        int numRows = height; // Number of rows

        for (int i = 0; i < numColumns; i++) {
            for (int j = 0; j < numRows; j++) {
                int x = i * cellSize;
                int y = j * cellSize;
                int extraHeight = (int) (cellSize);// * 1.45);
                int reduction = (int) (cellSize); // / 2.1);

                canvas.drawBitmap(defaultTileBitmap, null, new Rect(x, y, x + cellSize, y + cellSize), null);
                if(!cells[i][j].isEmpty())
                {
                    String id = cells[i][j].getOccupant();
                    Resources resources = getContext().getResources();
                    int resourceId = resources.getIdentifier(getImageResource(id), "drawable", getContext().getPackageName());
                    Bitmap objBitmap = BitmapFactory.decodeResource(resources, resourceId);
                    canvas.drawBitmap(objBitmap, null, new Rect(x, y, x + cellSize, y + extraHeight), null);
                }
            }
        }
    }

    public void updateGrid(Cell[][] cells)
    {
        this.cells = cells;
    }

    private String getImageResource(String objectId) {
        // Add logic to map objectId to an image resource
        // ...
        String[] strings = objectId.split("_");

        return strings[0] + "_" + strings[1];
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate(); // Redraw the canvas whenever the color changes
    }

    public void init(int width, int height, EventManager eventManager, CustomCanvasView parent)
    {
        this.defaultTileBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_tile);
        this.width = width;
        this.height = height;
        this.cellSize = 900 / width;
        this.eventManager = eventManager;
        this.parent = parent;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int cellSize = getWidth() / width;  // Assuming numColumns is the number of columns in the grid
        int row = (int) (x / cellSize);
        int column = (int) (y / cellSize);  // Calculate index based on row and column


        if (row >= cells.length || column >= cells[row].length) {
            return false; // Out of bounds
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Get the ID from the ArrayList
                Cell touchedCell = cells[row][column];
                // Check with your data model
                if (!touchedCell.isEmpty()) {
                    if(Objects.equals(prevSelected, touchedCell.getOccupant()))
                    {
                        Log.d("CustomCanvas", "Previous String " + prevSelected + " current string " + touchedCell.getOccupant());
                        eventManager.tap(touchedCell.getOccupant());
                    }
                    selectedObject = touchedCell.getOccupant();
                    selectedObjectInitialRow = row;
                    selectedObjectInitialColumn = column;
                    lastTouchX = x;
                    lastTouchY = y;
                    Log.d("Following object pressed: ", selectedObject);
                    // Example method in your data model
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (selectedObject != null) {
                    if(!dragStarted)
                    {
                        dragStarted = true;
                        cells[selectedObjectInitialRow][selectedObjectInitialColumn].setOccupant("default_tile");
                        parent.drawBackground();
                    }
                    // Update object position in data model
                    //TODO dataModel.moveObject(selectedObject, column, row);
                    //cells[selectedObject.getX()][selectedObject.getY()].setOccupant("default_tile");
                    lastTouchX = x;
                    lastTouchY = y;
                    Log.d("Following object dragged: ", selectedObject + " at x: " + x + " and y " + y );
                    Log.d("X AND Y", lastTouchX + " " + lastTouchY);
                    //setDrawingMode(1);
                    Log.d("InvalidateRect", "Invalidating from (" + x + "," + y + ") to (" + (x + cellSize) + "," + (y + cellSize) + ")");
                    //invalidate((int)x, (int)y, (int)x + cellSize, (int)y + cellSize);
                    invalidate((int)x, (int)y, (int)x + cellSize, (int)y + cellSize);  // Cause the view to redraw
                }
                break;
            case MotionEvent.ACTION_UP:
                if (selectedObject != null) {
                    //TODO selectedObject.endDrag();  // Finalize the drag in your data model
                    Log.d("Following object released: ", selectedObject);
                    if(dragStarted)
                    {
                        Cell target = cells[(int) (x / cellSize)][(int) (y / cellSize)];
                        if(target.isEmpty())
                        {
                            target.setOccupant(selectedObject);
                        }
                        else
                        {
                            eventManager.swapOrMerge(selectedObject, target.getOccupant());
                        }

                        dragStarted = false;
                    }
                    prevSelected = selectedObject;
                    selectedObject = null;
                    //setDrawingMode(0);
                    invalidate();
                    parent.drawBackground();
                }
                break;
        }
        return true;
    }
}
