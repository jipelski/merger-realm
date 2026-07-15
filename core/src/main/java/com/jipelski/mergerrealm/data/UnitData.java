package com.jipelski.mergerrealm.data;



public class UnitData extends GenData {

    protected String resource;
    protected int    gen_rate;
    protected int    hp;
    protected int    damage;
    protected int    xp_rate;
    protected String nemesis;
    protected int    nemesis_rate;
    // Dual-resource legendaries (e.g. elder_villager: food + wood). "none"/0 for
    // everything else — base units don't have these fields in unit.json at all.
    protected String secondary_resource;
    protected int    secondary_gen_rate;
    // Raid V3 status-effect wiring (legendary traits). "none" for everything
    // else — same optional-field convention as secondary_resource above,
    // since plain unit.json entries don't have these keys at all either.
    // onHitEffect: applied to the enemy/ally a unit successfully hits.
    // selfEffect: applied to the unit itself once at raid formation (permanent
    //   for the raid — e.g. shadowbow's double-damage, ironclad's reflect).
    // auraEffect: applied to every alive party member each frame while this
    //   unit is alive (RaidManager.recomputeAuras) — e.g. royal_knight's
    //   damage reduction, high_priest's heal, archangel's immunity.
    protected String onHitEffect;
    protected String selfEffect;
    protected String auraEffect;

    // NULL CONSTRUCTOR
    public UnitData() {
        super();
        this.resource           = null;
        this.gen_rate           = 0;
        this.hp                 = 0;
        this.damage             = 0;
        this.xp_rate            = 0;
        this.nemesis            = null;
        this.nemesis_rate       = 0;
        this.secondary_resource = null;
        this.secondary_gen_rate = 0;
        this.onHitEffect        = "none";
        this.selfEffect         = "none";
        this.auraEffect         = "none";
    }

    // CONSTRUCTOR
    public UnitData(String sprite_path, String description, int maxLVL,
                    String resource, int gen_rate, int hp, int damage, int xp_rate,
                    String nemesis, int nemesis_rate) {
        this(sprite_path, description, maxLVL, resource, gen_rate, hp, damage,
            xp_rate, nemesis, nemesis_rate, null, 0);
    }

    // CONSTRUCTOR (with secondary resource)
    public UnitData(String sprite_path, String description, int maxLVL,
                    String resource, int gen_rate, int hp, int damage, int xp_rate,
                    String nemesis, int nemesis_rate,
                    String secondary_resource, int secondary_gen_rate) {
        super(sprite_path, description, maxLVL);
        this.resource           = resource;
        this.gen_rate           = gen_rate;
        this.damage             = damage;
        this.hp                 = hp;
        this.xp_rate            = xp_rate;
        this.nemesis            = nemesis;
        this.nemesis_rate       = nemesis_rate;
        this.secondary_resource = secondary_resource;
        this.secondary_gen_rate = secondary_gen_rate;
    }

    // GETTERS
    public String getResource()          { return resource;           }
    public int    getGen_rate()          { return gen_rate;           }
    public int    getHp()                { return hp;     }
    public int    getDamage()            { return damage;             }
    public int    getXP_Rate()           { return xp_rate;            }
    public String getNemesis()           { return nemesis;            }
    public int    getNemesis_Rate()      { return nemesis_rate;       }
    public String getSecondaryResource() { return secondary_resource; }
    public int    getSecondaryGenRate()  { return secondary_gen_rate; }
    public String getOnHitEffect()       { return onHitEffect;        }
    public String getSelfEffect()        { return selfEffect;         }
    public String getAuraEffect()        { return auraEffect;         }

    // SETTERS
    public void setResource(String resource)        { this.resource     = resource;     }
    public void setGen_rate(int gen_rate)           { this.gen_rate     = gen_rate;     }
    public void setHp(int hp)                       { this.hp = hp;  }
    public void setDamage(int damage)               { this.damage       = damage;       }
    public void setXP_Rate(int xp_rate)             { this.xp_rate      = xp_rate;      }
    public void setNemesis(String nemesis)          { this.nemesis      = nemesis;      }
    public void setNemesis_rate(int nemesis_rate)   { this.nemesis_rate = nemesis_rate; }
    public void setSecondaryResource(String secondary_resource) {
        this.secondary_resource = secondary_resource;
    }
    public void setSecondaryGenRate(int secondary_gen_rate) {
        this.secondary_gen_rate = secondary_gen_rate;
    }
    public void setOnHitEffect(String onHitEffect) { this.onHitEffect = onHitEffect; }
    public void setSelfEffect(String selfEffect)   { this.selfEffect  = selfEffect;  }
    public void setAuraEffect(String auraEffect)   { this.auraEffect  = auraEffect;  }


    @Override
    public String toString() {
        return "UnitData{resource='" + resource
                + "', gen_rate=" + gen_rate
                + ", hp=" + hp
                + ", damage=" + damage
                + ", xp_rate=" + xp_rate
                + ", nemesis='" + nemesis
                + "', nemesis_rate=" + nemesis_rate
                + ", secondary_resource='" + secondary_resource
                + "', secondary_gen_rate=" + secondary_gen_rate
                + ", maxLVL=" + maxLVL + "}";
    }
}
