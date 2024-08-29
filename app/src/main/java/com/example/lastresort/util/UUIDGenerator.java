package com.example.lastresort.util;

import java.util.UUID;

public class UUIDGenerator {
    private static UUIDGenerator instance = null;

    //NULL CONSTRUCTOR
    private UUIDGenerator(){}

    //CONSTRUCTOR
    public static UUIDGenerator getInstance(){
        if(instance == null)
        {
            instance = new UUIDGenerator();
        }

        return instance;
    }

    public String generateFormattedID(String objectType, int level)
    {
        UUID uuid = UUID.randomUUID();
        return objectType + "_" + level + "_" + uuid;
    }
}
