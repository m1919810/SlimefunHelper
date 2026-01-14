package me.matl114.hackUtils;

//todo:
public @interface Module {
    String value();
    String[] extra() default {};
}
