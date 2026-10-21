package me.zed_0xff.zombie_buddy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * COMPILE-ONLY STUB of ZombieBuddy's Exposer / Exposer.LuaClass annotation.
 *
 * This file is never packaged into vpfps.jar (build.ps1 ships only the
 * dev/vpfps package). At runtime the annotation resolves against the real
 * me.zed_0xff.zombie_buddy.Exposer$LuaClass inside ZombieBuddy.jar.
 *
 * Usage (official ZombieBuddy pattern, per the Modding Guide):
 *   @Exposer.LuaClass(name = "MyGlobalName")
 *   public class MyApi { public static String foo() {...} }
 * ...which ZombieBuddy discovers in the mod's javaPkgName package and
 * registers with the game's Lua engine as a global table, static methods
 * callable as MyGlobalName.foo().
 */
public final class Exposer {

    private Exposer() {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface LuaClass {
        /** Global Lua table name; empty = the class's simple name. */
        String name() default "";
    }
}
