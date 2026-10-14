package me.zed_0xff.zombie_buddy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * COMPILE-ONLY STUB of ZombieBuddy's patch annotation.
 *
 * This file is never packaged into vpfps.jar (build.ps1 only ships the
 * dev/vpfps package). At runtime the annotation resolves against the real
 * me.zed_0xff.zombie_buddy.Patch shipped inside ZombieBuddy.jar, which
 * loads our mod. Only the elements actually used by this mod are declared
 * here; defaults mirror the documented ZombieBuddy API.
 *
 * If you have the real ZombieBuddy.jar, prefer compiling against it:
 *   javac -cp ZombieBuddy.jar ...
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Patch {

    /** Fully qualified target class name, e.g. "zombie.GameWindow". */
    String className();

    /** Target method name. Use "<init>" for constructors. */
    String methodName();

    /** Optional JVM descriptor to disambiguate overloads. */
    String methodDescriptor() default "";

    /** true (default): ByteBuddy Advice (inlined enter/exit). false: MethodDelegation. */
    boolean isAdvice() default true;

    /** Load the target class immediately during agent init. */
    boolean warmUp() default false;

    /** Require an exact signature match instead of inference. */
    boolean strictMatch() default false;

    /** Conditions ("client", "server") under which the patch is skipped. */
    String[] skipOn() default {};

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface OnEnter {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface OnExit {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface This {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Argument {
        int value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Return {}
}
