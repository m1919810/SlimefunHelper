package me.matl114.hacks;

//todo:
public @interface Module {
    String value();
    String[] extra() default {};
}
