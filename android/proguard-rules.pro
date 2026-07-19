# To enable ProGuard in your project, edit project.properties
# to define the proguard.config property as described in that file.
#
# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the ProGuard
# include property in project.properties.
#
# For more details, see
#   https://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-verbose

-dontwarn android.support.**
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication

# Needed by the gdx-controllers official extension.
-keep class com.badlogic.gdx.controllers.android.AndroidControllers

# Needed by the Box2D official extension.
-keepclassmembers class com.badlogic.gdx.physics.box2d.World {
   boolean contactFilter(long, long);
   boolean getUseDefaultContactFilter();
   void    beginContact(long);
   void    endContact(long);
   void    preSolve(long, long);
   void    postSolve(long, long);
   boolean reportFixture(long);
   float   reportRayFixture(long, float, float, float, float, float);
}

# You will need the next three lines if you use scene2d for UI or gameplay.
# If you don't use scene2d at all, you can remove or comment out the next line:
-keep public class com.badlogic.gdx.scenes.scene2d.** { *; }
# You will need the next two lines if you use BitmapFont or any scene2d.ui text:
-keep public class com.badlogic.gdx.graphics.g2d.BitmapFont { *; }
# You will probably need this line in most cases:
-keep public class com.badlogic.gdx.graphics.Color { *; }

# These two lines are used with mapping files; see https://developer.android.com/build/shrink-code#retracing
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

# ── Gson reflection: every persisted save/data class in this project is
# serialized by RAW Java field name (JsonManager's Gson instance uses no
# @SerializedName anywhere in the codebase) — R8 renaming those fields would
# silently break save/load round-trips and bundled-default-data parsing in
# release builds only (debug has no minify, so this doesn't show there). See
# CLAUDE.md's "Persistence rules" and the 2026-07-16 Play Console prep notes.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# TypeToken anonymous subclasses (JsonManager uses `new TypeToken<...>(){}`
# extensively for Map/List/array shapes) need their generic signature kept,
# which the Signature attribute above covers, plus the class itself.
-keep,allowobfuscation class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# Merger Realm persisted model + data classes: GameObject subtypes (Unit,
# Facility, Storage, Monster, Chest, Token, ResourcePouch, Prince — see
# JsonManager's RuntimeTypeAdapterFactory registrations), RaidState and its
# graph (Combatant, ActiveStatusEffect, RaidEnemy, DeadPartySnapshot,
# CombatEvent), ExplorationSlot, Item, GenData subclasses (reward queue),
# FacilitySpawnConfiguration all live here or in data/.
-keep class com.jipelski.mergerrealm.model.** { *; }
-keep class com.jipelski.mergerrealm.data.** { *; }

# Private Gson response POJO used by ServerTimeManager's (currently no-op)
# server time sync — harmless to keep now, needed the moment a real backend
# is wired up.
-keep class com.jipelski.mergerrealm.util.ServerTimeManager$* { *; }
