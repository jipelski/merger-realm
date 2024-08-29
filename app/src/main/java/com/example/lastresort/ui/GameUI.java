package com.example.lastresort.ui;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.example.lastresort.util.EventManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class GameUI {
    private Context context;
    private EventManager eventManager;

    private LinearLayout    buttonsRow;
    private Button          questButton;
    private Button          buildButton;
    private Button          reqButton;
    private Button          scoutButton;
    private Button          menuButton;

    private LinearLayout                descriptionBar;
    private TextView                    objectLvl;
    private DescriptionCustom           objectDescription;
    private LinearLayout                resourceBar;
    private ResourceCustom              resource1;
    private ResourceCustom              resource2;
    private ResourceCustom              resource3;

    private LinearLayout    rewardBar;
    private Button          rewardButton;

    private TableLayout gridView;
    private List<View> viewList = new ArrayList<>();
    private int             gridHeight;
    private int             gridWidth;
    private GameGridAdapter gridAdapter;

    private CustomCanvas canvas;

    private Field           battleField;

    public GameUI(Context context, EventManager eventManager, CustomCanvasView tableLayout)
    {
        this.context = context;
        this.eventManager = eventManager;
        this.gridHeight = eventManager.getGridInstance().getHeight();
        this.gridWidth  = eventManager.getGridInstance().getWidth();
        //initGrid(gridWidth, gridHeight, tableLayout);
        initGrid(gridWidth, gridHeight, context);
    }

    /**
    private void initGrid(int gridWidth, int gridHeight, TableLayout tableLayout)
    {
        gridView = tableLayout;
        //gridView.setNumColumns(gridWidth);

        gridAdapter = new GameGridAdapter(context, eventManager.getGridInstance());
        //gridView.setAdapter(gridAdapter);

        for (int i = 0; i < gridHeight; i++) {
            TableRow row = new TableRow(context);

            for (int j = 0; j < gridWidth; j++) {
                /*
                //TODO REMEMBER THIS
                View cellView = gridAdapter.createCellView(context, i+j);
                row.addView(cellView);
                cellView.setBackgroundResource(R.drawable.default_tile);
                cellView.setForeground(context.getResources().getDrawable(
                        context.getResources().getIdentifier(getImageResource(eventManager.getGridInstance().getCell(j,i).getOccupant()), "drawable", context.getPackageName())));
                viewList.add(cellView);



                int position = i * gridWidth + j;
                CustomCellView cellView = gridAdapter.createCustomLayout(context, position);

                // Setting the background image based on the occupant
                String objectId = getImageResource(gridAdapter.calculateObjectId(position));
                int imageResource = context.getResources().getIdentifier(objectId, "drawable", context.getPackageName());
                Drawable cellBackground = ContextCompat.getDrawable(context, R.drawable.default_tile);
                cellView.setBackgroundImage(cellBackground);

                row.addView(cellView, new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

            }


            gridView.addView(row);
        }
    }

    **/

    private void initGrid(int gridWidth, int gridHeight, Context context)
    {
        canvas = new CustomCanvas(context, gridWidth, gridHeight);
    }

    public TableLayout getGridView() {
        return gridView;
    }

    private String getImageResource(String objectId) {
        // Add logic to map objectId to an image resource
        // ...
        String[] strings = objectId.split("_");

        return strings[0] + "_" + strings[1];
    }

}
