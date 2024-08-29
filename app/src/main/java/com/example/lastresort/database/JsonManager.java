package com.example.lastresort.database;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.example.lastresort.data.GenData;
import com.example.lastresort.model.FacilitySpawnConfiguration;
import com.example.lastresort.model.GameObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class JsonManager {
    private  Context context;
    private final Gson gson;

    public JsonManager(Context contex){
        this.context = contex;
        gson = new GsonBuilder().setPrettyPrinting().create(); 
    }

    //LOADER METHODS
    public Map<String, GameObject> loadGridData(String  filePath) {
        Map<String, GameObject> gridData;

        // Use FileInputStream to open files from internal storage
        try (FileInputStream fis = context.openFileInput(filePath);
             Reader reader = new InputStreamReader(fis)) {
            gridData = gson.fromJson(reader, new TypeToken<Map<String, GameObject>>() {}.getType());
        }
        catch (IOException e)
        {
            // Handle exceptions gracefully! Log error, throw custom exception, etc.
            e.printStackTrace();
            return null;
        }

        return gridData;
    }

    public Map<String, int[]> loadConsumableMap(String  filePath) {
        Map<String, int[]> consumableData;

        try (FileInputStream fis = context.openFileInput(filePath);
             Reader reader = new InputStreamReader(fis)) {
            consumableData = gson.fromJson(reader, new TypeToken<Map<String, int[]>>() {}.getType());
        }
        catch (IOException e)
        {
            // Handle exceptions gracefully! Log error, throw custom exception, etc.
            e.printStackTrace();
            return null;
        }

        return consumableData;
    }

    public int[] loadArray(String  filePath)
    {
        int[] array;
        try (FileInputStream fis = context.openFileInput(filePath);
             Reader reader = new InputStreamReader(fis)) {
            array = gson.fromJson(reader, new TypeToken<int[]>() {}.getType());
        }
        catch (IOException e)
        {
            // Handle exceptions gracefully! Log error, throw custom exception, etc.
            e.printStackTrace();
            return null;
        }

        return array;
    }

    public ArrayList<String> loadArrayList(String  filePath)
    {
        ArrayList<String> array;
        try (FileInputStream fis = context.openFileInput(filePath);
             Reader reader = new InputStreamReader(fis)) {
            array = gson.fromJson(reader, new TypeToken<ArrayList<String>>() {}.getType());
        }
        catch (IOException e)
        {
            // Handle exceptions gracefully! Log error, throw custom exception, etc.
            e.printStackTrace();
            return null;
        }

        return array;
    }

    public LinkedList<GenData> loadRewardQueue(String  filePath)
    {
        LinkedList<GenData> queue;
        try (FileInputStream fis = context.openFileInput(filePath);
             Reader reader = new InputStreamReader(fis)) {
            queue = gson.fromJson(reader, new TypeToken<LinkedList<GenData>>() {}.getType());
        }
        catch (IOException e)
        {
            // Handle exceptions gracefully! Log error, throw custom exception, etc.
            e.printStackTrace();
            return null;
        }

        return queue;
    }

    public Map<String, ArrayList<FacilitySpawnConfiguration>> loadArrMap(String  filePath)
    {
        try (FileInputStream fis = context.openFileInput(filePath);
             Reader reader = new InputStreamReader(fis)) {
            return gson.fromJson(reader, new TypeToken<Map<String, ArrayList<FacilitySpawnConfiguration>>>(){}.getType());
        } catch (IOException e) {
            // Handle exceptions gracefully! Log error, throw custom exception, etc.
            e.printStackTrace();
            return null;
        }
    }

    public Map<String, Object> loadJsonData(String  filePath) {
        try (FileInputStream fis = context.openFileInput(filePath);
             Reader reader = new InputStreamReader(fis)) {

            return gson.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
        } catch (IOException e) {
            // Handle exceptions gracefully! Log error, throw custom exception, etc.
            e.printStackTrace();
            return null;
        }
    }

    public Map<String, Boolean> loadStatus(String  filePath) {
        try (FileInputStream fis = context.openFileInput(filePath);
             Reader reader = new InputStreamReader(fis)) {
            return gson.fromJson(reader, new TypeToken<Map<String, Boolean>>(){}.getType());
        } catch (IOException e) {
            // Handle exceptions gracefully! Log error, throw custom exception, etc.
            e.printStackTrace();
            return null;
        }
    }

    public Map<String,int[]> loadGlobalCounterMap(String filePath) {
        try (FileInputStream fis = context.openFileInput(filePath);
             Reader reader = new InputStreamReader(fis)) {
            return gson.fromJson(reader, new TypeToken<Map<String, int[]>>(){}.getType());
        } catch (IOException e) {
            // Handle exceptions gracefully! Log error, throw custom exception, etc.
            e.printStackTrace();
            return null;
        }
    }

    //SAVER METHODS
    public void saveResources(String filePath,   Map<String, int[]> resMap)
    {
        Log.d("JsonManagerSaveResource", " Before try block");
        try (FileOutputStream fos = context.openFileOutput(filePath, Context.MODE_PRIVATE);
             OutputStreamWriter writer = new OutputStreamWriter(fos))
        {
            Log.d("JsonManagerSaveResource", "This is what was saved:" + resMap);
            gson.toJson(resMap, writer);
        } catch (IOException e)
        {
            Log.e("SaveGridObjects", "Failed to save grid objects", e);
            e.printStackTrace();
        }
    }

    public void saveArrayList(String filePath, ArrayList<String> arr)
    {
        try (FileOutputStream fos = context.openFileOutput(filePath, Context.MODE_PRIVATE);
             OutputStreamWriter writer = new OutputStreamWriter(fos))
        {
            gson.toJson(arr, writer);
        } catch (IOException e)
        {
            Log.e("SaveGridObjects", "Failed to save grid objects", e);
            e.printStackTrace();
        }
    }


    public void saveArray(String filePath, int[] arr)
    {
        try (FileOutputStream fos = context.openFileOutput(filePath, Context.MODE_PRIVATE);
             OutputStreamWriter writer = new OutputStreamWriter(fos))
        {
            gson.toJson(arr, writer);
        } catch (IOException e)
        {
            Log.e("SaveGridObjects", "Failed to save grid objects", e);
            e.printStackTrace();
        }
    }

    public void saveArray(String filePath, LinkedList<?> queue)
    {
        try (FileOutputStream fos = context.openFileOutput(filePath, Context.MODE_PRIVATE);
             OutputStreamWriter writer = new OutputStreamWriter(fos))
        {
            gson.toJson(queue, writer);
        } catch (IOException e)
        {
            Log.e("SaveGridObjects", "Failed to save grid objects", e);
            e.printStackTrace();
        }
    }

    public void saveGridObjects(String filePath, Map<String, GameObject> objMap)
    {
        try (FileOutputStream fos = context.openFileOutput(filePath, Context.MODE_PRIVATE);
             OutputStreamWriter writer = new OutputStreamWriter(fos))
        {
            gson.toJson(objMap, writer);
        } catch (IOException e)
        {
            Log.e("SaveGridObjects", "Failed to save grid objects", e);
            e.printStackTrace();
        }
    }

    public void saveCounterMap(String filePath, Map<String, int[]> objMap)
    {
        try (FileOutputStream fos = context.openFileOutput(filePath, Context.MODE_PRIVATE);
             OutputStreamWriter writer = new OutputStreamWriter(fos))
        {
            gson.toJson(objMap, writer);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    //TODO REPLACE STRING FILEPATH WITH ID FILEPATH AND IMPLEMENT R.RAW.FILENAME SAVING CONVENTION
}
