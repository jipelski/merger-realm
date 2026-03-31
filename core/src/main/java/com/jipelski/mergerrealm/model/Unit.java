package com.jipelski.mergerrealm.model;



import com.jipelski.mergerrealm.data.UnitData;

public class Unit extends GameObject {

    protected String resource;
    protected int    gen_rate;
    protected int    hp;
    protected int    max_hp;
    protected int    damage;
    protected int    xp_rate;
    protected String nemesis;
    protected int    nemesis_rate;

    // NULL CONSTRUCTOR
    public Unit() {
        super();
        this.resource     = null;
        this.gen_rate     = 0;
        this.hp           = 0;
        this.max_hp       = 0;
        this.damage       = 0;
        this.xp_rate      = 0;
        this.nemesis      = null;
        this.nemesis_rate = 0;
    }

    // FULL CONSTRUCTOR
    public Unit(String type, String id, int lvl, int maxLVL,
                int xPos, int yPos, String sprite, String description,
                String resource, int gen_rate, int hp, int damage, int xp_rate,
                String nemesis, int nemesis_rate) {
        super(type, id, lvl, maxLVL, xPos, yPos, sprite, description);
        this.resource     = resource;
        this.gen_rate     = gen_rate;
        this.hp           = hp;
        this.damage       = damage;
        this.xp_rate      = xp_rate;
        this.nemesis      = nemesis;
        this.nemesis_rate = nemesis_rate;
    }

    // DATA STRUCTURE CONSTRUCTOR
    public Unit(UnitData unitData, String type, String id, int lvl, int xPos, int yPos) {
        super(type, id, lvl, unitData.getMaxLVL(), xPos, yPos,
                unitData.getSprite_path(), unitData.getDescription());
        this.resource     = unitData.getResource();
        this.gen_rate     = unitData.getGen_rate();
        this.hp           = unitData.getHp();
        this.max_hp       = unitData.getHp();
        this.damage       = unitData.getDamage();
        this.xp_rate      = unitData.getXP_Rate();
        this.nemesis      = unitData.getNemesis();
        this.nemesis_rate = unitData.getNemesis_Rate();
    }

    // GETTERS
    public String getResource()    { return resource;     }
    public int    getGen_rate()    { return gen_rate;     }
    public int    getHp()          { return hp;           }
    public int    getMax_hp()      { return max_hp; }
    public int    getDamage()      { return damage;       }
    public int    getXP_Rate()     { return xp_rate;      }
    public String getNemesis()     { return nemesis;      }
    public int    getNemesis_rate(){ return nemesis_rate; }

    // SETTERS
    public void setResource(String resource)      { this.resource     = resource;     }
    public void setGen_rate(int gen_rate)          { this.gen_rate     = gen_rate;     }
    public void setHp(int hp)                      { this.hp           = hp;           }
    public void setMax_hp(int max_hp)              { this.max_hp = max_hp; }
    public void setDamage(int damage)              { this.damage       = damage;       }
    public void setXP_Rate(int xp_rate)            { this.xp_rate      = xp_rate;      }
    public void setNemesis(String nemesis)         { this.nemesis      = nemesis;      }
    public void setNemesis_rate(int nemesis_rate)  { this.nemesis_rate = nemesis_rate; }


    // COMBAT METHODS

    public boolean isAlive()   { return hp > 0; }
    public boolean isWounded() { return hp > 0 && hp < max_hp; }
    public boolean canFight()  { return max_hp > 0 && hp > 0 && damage > 0; }

    /**
     * Take damage. Returns true if unit died (HP <= 0).
     */
    public boolean takeDamage(int amount) {
        hp = Math.max(0, hp - amount);
        return hp <= 0;
    }

    /**
     * Heal by amount, capped at max_hp.
     */
    public void heal(int amount) {
        hp = Math.min(max_hp, hp + amount);
    }

    /**
     * Heal to full HP.
     */
    public void healToFull() {
        hp = max_hp;
    }
    @Override
    public String toString() {
        return "Unit{id='" + id + "', type='" + type
                + "', lvl=" + lvl
                + ", pos=[" + xPos + "," + yPos + "]"
                + ", resource='" + resource + "', gen_rate=" + gen_rate
                + ", hp=" + hp + "damage=" + damage + ", nemesis='" + nemesis + "'}";
    }
}
