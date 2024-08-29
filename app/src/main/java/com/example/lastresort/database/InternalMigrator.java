package com.example.lastresort.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.lastresort.R;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class InternalMigrator {
    int[] resourceIds = new int[] { R.raw.chest, R.raw.chest_config, R.raw.consumable_map, R.raw.counter_map, R.raw.facility, R.raw.facility_config,R.raw.grid_array,R.raw.grid_size,R.raw.monster,R.raw.object_map,R.raw.prince,R.raw.progression,R.raw.reward_queue,R.raw.status,R.raw.storage,R.raw.token,R.raw.unit}; // Add all your resource IDs here
    String[] fileNames = new String[] { "chest","chest_config","consumable_map","counter_map","facility","facility_config","grid_array","grid_size","monster","object_map","prince","progression","reward_queue","status","storage","token","unit"}; // Corresponding file names


    public InternalMigrator(Context context)
    {

    }

    public boolean performDataMigrationIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        //TODO change truth value to false to ensure the initialised values are not remigrated, do the oposite to remigrate the initialise values
        if (!prefs.getBoolean("isDataMigrated", false)) {
            migrateRawFilesToInternal(context, resourceIds, fileNames);

            // Mark the migration as done
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("isDataMigrated", true);
            editor.apply();

            return true;
        }
        return false;
    }

    private void migrateRawFilesToInternal(Context context, int[] resourceIds, String[] fileNames) {
        for (int i = 0; i < resourceIds.length; i++) {
            copyRawToInternal(context, resourceIds[i], fileNames[i]);
        }
    }

    private void copyRawToInternal(Context context, int resourceId, String outputFileName) {
        InputStream in = null;
        FileOutputStream out = null;

        try {
            in = context.getResources().openRawResource(resourceId);
            out = context.openFileOutput(outputFileName, Context.MODE_PRIVATE);

            byte[] buffer = new byte[32768];
            int read;
            StringBuilder sb = new StringBuilder();

            while ((read = in.read(buffer)) > -1) {
                out.write(buffer, 0, read);
                String part = new String(buffer, 0, read, StandardCharsets.UTF_8); // Convert buffer to string
                sb.append(part);
            }
            Log.d("DataCopy", "Data read: " + sb);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
