package me.matl114.hacks;

public @interface Module {
    String value();

    String[] extra() default {};
}
