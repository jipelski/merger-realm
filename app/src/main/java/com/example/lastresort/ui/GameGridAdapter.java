package com.example.lastresort.ui;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lastresort.R;
import com.example.lastresort.grid.Grid;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

import java.util.Timer;
import java.util.TimerTask;

public class GameGridAdapter {
    private Grid    gameGrid;
    private int     width;
    private int     height;
    private Context context;

    // Constructor
    public GameGridAdapter(Context context, Grid gameGrid) {
        this.gameGrid   = gameGrid;
        this.width      = gameGrid.getWidth();
        this.height     = gameGrid.getHeight();
        this.context    = context;
    }

    // Inner class ViewHolder
    /*public static class ViewHolder extends RecyclerView.ViewHolder {
        public TileDisplay tileDisplay;

        public ViewHolder(View itemView) {
            super(itemView);
            tileDisplay = itemView.findViewById(R.id.tileDisplay);
        }
    }*/

    /*
    // getItemCount: Total number of cells in the grid
    @Override
    public int getCount() {
        Log.d("GameGridSize", "Apparent widht: " + gameGrid.getWidth() + " Apparent height: " + gameGrid.getHeight());
        Log.d("Game_GRID_ACTUALSIZE", "TOTAL SIZE IS " + (gameGrid.getHeight() * gameGrid.getWidth()));
        return gameGrid.getWidth() * gameGrid.getHeight();
    }

    @Override
    public Object getItem(int position) {
        int row     = position / width;
        int column  = position % width;

        return gameGrid.getCell(column, row).getOccupant();
    }



    @Override
    public long getItemId(int position) {
        return 0;
    }
*/
    public String calculateObjectId(int position)
    {
        int row     = position / width;
        int column  = position % width;
        String id = gameGrid.getCell(column, row).getOccupant();

        return getImageResource(id);
    }

    /*@Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Reuse convertView if possible (for performance)
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.cell_layout, parent, false);
        }

        TileDisplay tileDisplay = convertView.findViewById(R.id.tileDisplay);

        // Your logic to get the objectId based on 'position'
        String objectId = calculateObjectId(position);

        int objectSource = context.getResources().getIdentifier(objectId, "drawable", context.getPackageName());
        int tileSource   = context.getResources().getIdentifier("default_tile.png", "drawable", context.getPackageName());

        // Update the TileDisplay ... (similar to your onBindViewHolder logic)
        //tileDisplay.setGroundView(tileSource);
        //tileDisplay.setObjectImage(objectSource);

        return convertView;
    }*/

    public View createCellView(Context context, int position) {
        LayoutInflater  inflater = LayoutInflater.from(context);
        View cellView = inflater.inflate(R.layout.cell_layout, null, false);
        //TileDisplay tileDisplay = cellView.findViewById(R.id.tileDisplay); // Or inflate from layout
        //TODO create a custom View

        // Your logic to get the objectId based on 'position'
        String objectId = calculateObjectId(position);
        Log.d("GameGridSize", "Apparent widht: " + gameGrid.getWidth() + " Apparent height: " + gameGrid.getHeight());
        Log.d("Game_GRID_ACTUALSIZE", "TOTAL SIZE IS " + (gameGrid.getHeight() * gameGrid.getWidth()));
        int objectSource = context.getResources().getIdentifier(objectId, "drawable", context.getPackageName());
        int tileSource = context.getResources().getIdentifier("archer_7.png", "drawable", context.getPackageName());

        Log.d("OBJECTID: ", objectId);
        Log.d("OBJECTSOURCE: ", String.valueOf(objectSource));
        Log.d("FLOORID: ", "default_tile.png");
        Log.d("FLOORSOURCE: ", String.valueOf(tileSource));

        // Update the TileDisplay
        //tileDisplay.inflate();
        //tileDisplay.setGroundView(tileSource);
        //tileDisplay.setObjectImage(objectSource);

        return cellView;
    }

    public CustomCellView createCustomLayout(Context context, int position)
    {
        String objectId = calculateObjectId(position);
        return new CustomCellView(context);
    }

    // Helper method (You might need a similar mapping in your Grid class)
    private String getImageResource(String objectId) {
        // Add logic to map objectId to an image resource
        // ...
        String[] strings = objectId.split("_");

        return strings[0] + "_" + strings[1] + ".png";
    }
}
