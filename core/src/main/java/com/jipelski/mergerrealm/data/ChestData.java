package com.jipelski.mergerrealm.data;



public class ChestData extends GenData {

    protected String token_type;
    protected int    tap_count;

    // NULL CONSTRUCTOR
    public ChestData() {
        super();
        this.token_type = null;
        this.tap_count  = 0;
    }

    // CONSTRUCTOR
    public ChestData(String sprite_path, String description, int maxLVL,
                     String token_type, int tap_count) {
        super(sprite_path, description, maxLVL);
        this.token_type = token_type;
        this.tap_count  = tap_count;
    }

    // GETTERS
    public String getToken_type() { return token_type; }
    public int    getTap_count()  { return tap_count;  }

    // SETTERS
    public void setToken_type(String token_type) { this.token_type = token_type; }
    public void setTap_count(int tap_count)      { this.tap_count  = tap_count;  }


    @Override
    public String toString() {
        return "ChestData{token_type='" + token_type
                + "', tap_count=" + tap_count
                + ", maxLVL=" + maxLVL + "}";
    }
}
