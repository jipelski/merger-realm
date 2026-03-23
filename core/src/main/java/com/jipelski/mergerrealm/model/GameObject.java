package com.jipelski.mergerrealm.model;



public class GameObject {

    protected String          type;
    protected String          id;
    protected int             lvl;
    protected int             maxLVL;
    protected int             xPos;
    protected int             yPos;
    protected String          sprite;
    protected GameObjectState state;
    protected String          description;

    // NULL CONSTRUCTOR
    public GameObject() {
        this.type        = null;
        this.id          = null;
        this.lvl         = -1;
        this.maxLVL      = -1;
        this.xPos        = -1;
        this.yPos        = -1;
        this.sprite      = null;
        this.state       = GameObjectState.IDLE;
        this.description = null;
    }

    // FULL CONSTRUCTOR
    public GameObject(String type, String id, int lvl, int maxLVL,
                      int xPos, int yPos, String sprite, String description) {
        this.type        = type;
        this.id          = id;
        this.lvl         = lvl;
        this.maxLVL      = maxLVL;
        this.xPos        = xPos;
        this.yPos        = yPos;
        this.sprite      = sprite;
        this.state       = GameObjectState.IDLE;
        this.description = description;
    }

    // GETTERS
    public String         getType()        { return type;        }
    public String         getId()          { return id;          }
    public int            getLvl()         { return lvl;         }
    public int            getMaxLVL()      { return maxLVL;      }
    public int            getxPos()        { return xPos;        }
    public int            getyPos()        { return yPos;        }
    public String         getSprite()      { return sprite;      }
    public GameObjectState getState()      { return state;       }
    public String         getDescription() { return description; }

    // SETTERS
    public void setType(String type)               { this.type        = type;        }
    public void setId(String id)                   { this.id          = id;          }
    public void setLvl(int lvl)                    { this.lvl         = lvl;         }
    public void setMaxLVL(int maxLVL)              { this.maxLVL      = maxLVL;      }
    public void setxPos(int xPos)                  { this.xPos        = xPos;        }
    public void setyPos(int yPos)                  { this.yPos        = yPos;        }
    public void setSprite(String sprite)           { this.sprite      = sprite;      }
    public void setState(GameObjectState state)    { this.state       = state;       }
    public void setDescription(String description) { this.description = description; }

    // METHODS

    public void onTap() {
        if (this.state == GameObjectState.IDLE) {
            this.state = GameObjectState.SELECTED;
        }
    }


    @Override
    public String toString() {
        return "GameObject{type='" + type + "', id='" + id
                + "', lvl=" + lvl + ", maxLVL=" + maxLVL
                + ", pos=[" + xPos + "," + yPos + "]"
                + ", state=" + state + "}";
    }
}
