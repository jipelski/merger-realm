package com.jipelski.mergerrealm.data;



public class UnitData extends GenData {

    protected String resource;
    protected int    gen_rate;
    protected int    hp;
    protected int    damage;
    protected int    xp_rate;
    protected String nemesis;
    protected int    nemesis_rate;

    // NULL CONSTRUCTOR
    public UnitData() {
        super();
        this.resource     = null;
        this.gen_rate     = 0;
        this.hp           = 0;
        this.damage       = 0;
        this.xp_rate      = 0;
        this.nemesis      = null;
        this.nemesis_rate = 0;
    }

    // CONSTRUCTOR
    public UnitData(String sprite_path, String description, int maxLVL,
                    String resource, int gen_rate, int hp, int damage, int xp_rate,
                    String nemesis, int nemesis_rate) {
        super(sprite_path, description, maxLVL);
        this.resource     = resource;
        this.gen_rate     = gen_rate;
        this.damage       = damage;
        this.hp           = hp;
        this.xp_rate      = xp_rate;
        this.nemesis      = nemesis;
        this.nemesis_rate = nemesis_rate;
    }

    // GETTERS
    public String getResource()    { return resource;     }
    public int    getGen_rate()    { return gen_rate;     }
    public int    getHp()          { return hp;     }
    public int    getDamage()      { return damage;       }
    public int    getXP_Rate()     { return xp_rate;      }
    public String getNemesis()     { return nemesis;      }
    public int    getNemesis_Rate(){ return nemesis_rate; }

    // SETTERS
    public void setResource(String resource)        { this.resource     = resource;     }
    public void setGen_rate(int gen_rate)           { this.gen_rate     = gen_rate;     }
    public void setHp(int hp)                       { this.hp = hp;  }
    public void setDamage(int damage)               { this.damage       = damage;       }
    public void setXP_Rate(int xp_rate)             { this.xp_rate      = xp_rate;      }
    public void setNemesis(String nemesis)          { this.nemesis      = nemesis;      }
    public void setNemesis_rate(int nemesis_rate)   { this.nemesis_rate = nemesis_rate; }


    @Override
    public String toString() {
        return "UnitData{resource='" + resource
                + "', gen_rate=" + gen_rate
                + ", hp=" + hp
                + ", damage=" + damage
                + ", xp_rate=" + xp_rate
                + ", nemesis='" + nemesis
                + "', nemesis_rate=" + nemesis_rate
                + ", maxLVL=" + maxLVL + "}";
    }
}
