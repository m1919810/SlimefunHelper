package me.matl114.utils;

public class MathUtils {
    public static double s2(double x) {
        return x * x;
    }
    public static double squareSum(double... x){
        double sum = 0;
        for(double y : x){
            sum += y*y;
        }
        return sum;
    }
    public static int sgn(int t){
        return Integer.compare(t, 0);
    }
}
