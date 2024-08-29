package com.example.lastresort.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TableLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lastresort.R;
import com.example.lastresort.database.InternalMigrator;
import com.example.lastresort.database.JsonManager;
import com.example.lastresort.databinding.ActivityMainBinding;
import com.example.lastresort.ui.CustomCanvas;
import com.example.lastresort.ui.CustomCanvasView;
import com.example.lastresort.ui.GameGridAdapter;
import com.example.lastresort.ui.GameUI;
import com.example.lastresort.util.EventManager;

import java.io.File;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private JsonManager jsonManager;
    private EventManager eventManager;
    private GameUI gameUI;
    //private TableLayout gridView;
    private CustomCanvasView gridView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //binding = ActivityMainBinding.inflate(getLayoutInflater());
        //setContentView(binding.getRoot());
        InternalMigrator i = new InternalMigrator(this);

        boolean b = i.performDataMigrationIfNeeded(this);
        Log.d("Performed migration", b + "");


        jsonManager = new JsonManager(this);
        File file = new File(this.getFilesDir(), "unit");
        if (!file.exists()) {
            Log.e("FileExistence", "unit.json does not exist in the internal storage.");
        } else {
            Log.i("FileExistence", "unit.json exists. Proceeding to load.");
        }
        Map<String, Object> data = jsonManager.loadJsonData( "unit");
        assert data != null : "Data should not be null";
        if (data.isEmpty()) {
            Log.w("DataCheck", "Data is not null but is empty");
        }
        eventManager = new EventManager(jsonManager);



        gridView = findViewById(R.id.gameGridLayout);

        gameUI = new GameUI(this, eventManager, gridView);

        ConstraintLayout rootlayout = findViewById(R.id.rootLayout);
        rootlayout.setVerticalScrollBarEnabled(false);
        rootlayout.setHorizontalScrollBarEnabled(false);


        gridView.setHorizontalScrollBarEnabled(false);
        gridView.setVerticalScrollBarEnabled(false);

        gridView.setBackground(findViewById(R.id.background_layer));
        gridView.setForeground(findViewById(R.id.foreground_layer));

        gridView.init(eventManager.getGridInstance().getWidth(), eventManager.getGridInstance().getHeight(), eventManager);
        gridView.updateGrid(eventManager.getGridInstance().getCells());
        //gridView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setupCanvas();
    }

    @Override
    protected void onPause() {
        super.onPause();
        jsonManager.saveResources("consumable_map", eventManager.getResourceManager().getConsumableMap());
        jsonManager.saveArray("grid_size", eventManager.getGridInstance().getXoY());
        jsonManager.saveArrayList("grid_array", eventManager.getGridInstance().getArr());
        jsonManager.saveGridObjects("object_map", eventManager.getGRID_OBJECT_MANAGER().getObjectMap());
        Log.d("On Pause", "Files should be saved");
    }

    @Override
    protected void onStop() {
        super.onStop();
        //jsonManager.saveResources("consumable_map", eventManager.getResourceManager().getConsumableMap());
        //jsonManager.saveArray("grid_size", eventManager.getGridInstance().getXoY());
        //jsonManager.saveArrayList("grid_array", eventManager.getGridInstance().getArr());
        Log.d("On Stop", "Files should be saved");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //jsonManager.saveResources("consumable_map", eventManager.getResourceManager().getConsumableMap());
        //jsonManager.saveArray("grid_size", eventManager.getGridInstance().getXoY());
        //jsonManager.saveArrayList("grid_array", eventManager.getGridInstance().getArr());
        Log.d("On Destroy", "Files should be saved");
    }

    private void setupCanvas() {
        gridView.invalidate(); // Request a redraw
        Log.d("DRAW CANVAS INSTANCE", "DONE");
    }
}
