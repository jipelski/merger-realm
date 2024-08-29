package com.example.lastresort.util;

import androidx.annotation.NonNull;

import com.example.lastresort.R;
import com.example.lastresort.database.JsonManager;

import java.util.Map;
import java.util.Objects;

public class ResourceManager {

    private Map<String, int[]>   consumableMap; // Key: Resource/Token Name | Value: Amount | GenRate | Pool Size

    //CONSTRUCTOR
    public ResourceManager(JsonManager jsonManager){
        this.consumableMap = jsonManager.loadConsumableMap("consumable_map");
    }

    //GETTERS


    public Map<String, int[]> getConsumableMap() {
        return consumableMap;
    }

    public int getAmount(String resource)
    {
        return Objects.requireNonNull(consumableMap.get(resource))[0];
    }

    public int getRate(String resource){
        return Objects.requireNonNull(consumableMap.get(resource))[1];
    }

    public int getPoolSize(String resource){
        return Objects.requireNonNull(consumableMap.get(resource))[2];
    }

    //SETTERS

    public void setConsumableMap(Map<String, int[]> consumableMap) {
        this.consumableMap = consumableMap;
    }

    public void setAmount(String resource, int amount)
    {
        int[] temp = consumableMap.get(resource);
        assert temp != null;
        temp[0] += amount;
        consumableMap.put(resource, temp);
    }

    public void setRate(String resource, int amount)
    {
        int[] temp = consumableMap.get(resource);
        assert temp != null;
        temp[1] += amount;
        consumableMap.put(resource, temp);
    }

    public void setPoolSize(String resource, int amount)
    {
        int[] temp = consumableMap.get(resource);
        assert temp != null;
        temp[2] += amount;
        consumableMap.put(resource, temp);
    }

    //METHODS
    public void updateResources()
    {
        for (String t: consumableMap.keySet()) {
            if(t.equals("timber") || t.equals("quarrystone") || t.equals("iron"))
            {
                int[] value = consumableMap.get(t);
                if(value[0] + value[1] < value[2])
                {
                    value[0] += value[1];
                    consumableMap.replace(t, value);
                }
            }
        }
    }

    public boolean reduceResource(int amount1, int amount2, int amount3)
    {
        int[] value1 = consumableMap.get("timber");
        int[] value2 = consumableMap.get("quarrystone");
        int[] value3 = consumableMap.get("iron");
        if(     (consumableMap.get("timber")[0] - amount1 >= 0) &&
                (consumableMap.get("quarrystone")[0] - amount2 >= 0) &&
                (consumableMap.get("iron")[0] - amount3 >= 0))
        {
            value1[0] -= amount1;
            value2[0] -= amount2;
            value3[0] -= amount3;
            consumableMap.replace("timber", value1);
            consumableMap.replace("quarrystone", value2);
            consumableMap.replace("iron", value3);
            return true;
        }
        else
            return false;
    }

    public void modifyResourcePoolSize(String resource, int amount, boolean increase)
    {
        int[] value = consumableMap.get(resource);
        assert value != null;
        value[2] =  increase ? value[2] + amount : value[2] - amount ;
        consumableMap.replace(resource, value);
    }

    public void modifyResourceRate(String resource, int amount, boolean increase)
    {
        if(!resource.equals("nothing"))
        {
            int[] value = consumableMap.get(resource);
            assert value != null;
            value[1] = increase ? value[1] + amount : value[1] - amount;
            consumableMap.replace(resource, value);
        }

    }

    public void increaseTokens(int amount1, int amount2, int amount3, int amount4)
    {
        int[] value1 = consumableMap.get("wood");
        int[] value2 = consumableMap.get("wheat");
        int[] value3 = consumableMap.get("stone");
        int[] value4 = consumableMap.get("fire");
        value1[0] += amount1;
        value2[0] += amount2;
        value3[0] += amount3;
        value4[0] += amount4;
        consumableMap.replace("wood", value1);
        consumableMap.replace("wheat", value2);
        consumableMap.replace("stone", value3);
        consumableMap.replace("fire", value4);
    }

    public void increaseToken(String token_type, int amount)
    {
        int[] value = consumableMap.get(token_type);
        value[0] += amount;
        consumableMap.replace(token_type, value);
    }

    public boolean decreaseTokens(int amount1, int amount2, int amount3, int amount4)
    {
        int[] value1 = consumableMap.get("wood");
        int[] value2 = consumableMap.get("wheat");
        int[] value3 = consumableMap.get("stone");
        int[] value4 = consumableMap.get("fire");
        if(     (consumableMap.get("wood")[0] - amount1 >= 0) &&
                (consumableMap.get("wheat")[0] - amount2 >= 0) &&
                (consumableMap.get("stone")[0] - amount3 >= 0) &&
                (consumableMap.get("fire")[0] - amount4 >= 0))
        {
            value1[0] -= amount1;
            value2[0] -= amount2;
            value3[0] -= amount3;
            value4[0] -= amount4;
            consumableMap.replace("wood", value1);
            consumableMap.replace("wheat", value2);
            consumableMap.replace("stone", value3);
            consumableMap.replace("fire", value3);
            return true;
        }
        else
            return false;
    }
}
