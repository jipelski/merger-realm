package com.example.lastresort.data;

public class TokenData extends GenData{
    protected int value;
    protected String token_type;

    //NULL CONSTRUCTOR
    public TokenData()
    {}

    //BASIC CONSTRUCTOR
    public TokenData(String sprite_path, String description, int maxLVL,  int value, String token_type)
    {
        super(sprite_path, description, maxLVL);
        this.value = value;
        this.token_type = token_type;
    }

    //GETTERS

    public int getValue() {
        return value;
    }

    public String getTokenType() {
        return token_type;
    }
    //SETTERS

    public void setValue(int value) {
        this.value = value;
    }

    public void setTokenType(String token_type) {
        this.token_type = token_type;
    }
}
