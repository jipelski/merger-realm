package com.example.lastresort.model;

import android.media.Image;

import com.example.lastresort.data.StorageData;

public class Storage extends GameObject{
    protected String storage_type;
    protected int    storage_size;

    //protected StorageConfiguration config;

    // NULL CONSTRUCTOR
    public Storage()
    {
        this.storage_type = null;
        this.storage_size = 0;
    }

    // CONSTRUCTOR
    public Storage(String type, String id, int lvl, int maxLVL, int xPos, int yPos, String sprite, String description, String storage_type, int storage_size)
    {
        super(type, id, lvl, maxLVL, xPos, yPos, sprite, description);
        this.storage_type = storage_type;
        this.storage_size = storage_size;
    }

    //STORAGE DATA CONSTRUCTOR
    public Storage(StorageData storageData,String type, String id, int lvl, int xPos, int yPos)
    {
        super(type, id, lvl, storageData.getMaxLVL(), xPos, yPos, storageData.getSprite_path(), storageData.getDescription());
        this.storage_type = storageData.getStorage_type();
        this.storage_size = storageData.getStorage_size();
    }

    // GETTERS
    public String getStorage_type() {
        return storage_type;
    }
    public int getStorage_size() {
        return storage_size;
    }

    // SETTERS
    public void setStorage_type(String storage_type) {
        this.storage_type = storage_type;
    }
    public void setStorage_size(int storage_size) {
        this.storage_size = storage_size;
    }
}
