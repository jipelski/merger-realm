package com.jipelski.mergerrealm.model;



import com.jipelski.mergerrealm.data.TokenData;

public class Token extends GameObject {

    protected int    value;
    protected String token_type;

    // NULL CONSTRUCTOR
    public Token() {
        super();
        this.value      = 0;
        this.token_type = null;
    }

    // FULL CONSTRUCTOR
    public Token(String type, String id, int lvl, int maxLVL,
                 int xPos, int yPos, String sprite, String description,
                 int value, String token_type) {
        super(type, id, lvl, maxLVL, xPos, yPos, sprite, description);
        this.value      = value;
        this.token_type = token_type;
    }

    // DATA STRUCTURE CONSTRUCTOR
    public Token(TokenData tokenData, String type, String id, int lvl, int xPos, int yPos) {
        super(type, id, lvl, tokenData.getMaxLVL(), xPos, yPos,
                tokenData.getSprite_path(), tokenData.getDescription());
        this.value      = tokenData.getValue();
        this.token_type = tokenData.getTokenType();
    }

    // GETTERS
    public int    getValue()     { return value;      }
    public String getTokenType() { return token_type; }

    // SETTERS
    public void setValue(int value)            { this.value      = value;      }
    public void setTokenType(String token_type){ this.token_type = token_type; }


    @Override
    public String toString() {
        return "Token{id='" + id + "', token_type='" + token_type
                + "', value=" + value
                + ", lvl=" + lvl
                + ", pos=[" + xPos + "," + yPos + "]}";
    }
}
