package me.matl114.hooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ViaProtocols {
    // ==================== 协议迁移常量（字段名大写，字符串值原样） ====================

    public static final String BASE = "base";
    public static final String TEMPLATE = "template";
    public static final String V1_8_TO_1_9 = "v1_8to1_9";
    public static final String V1_9_TO_1_9_1 = "v1_9to1_9_1";
    public static final String V1_9_1_TO_1_9_3 = "v1_9_1to1_9_3";
    public static final String V1_9_3_TO_1_10 = "v1_9_3to1_10";
    public static final String V1_10_TO_1_11 = "v1_10to1_11";
    public static final String V1_11_TO_1_11_1 = "v1_11to1_11_1";
    public static final String V1_11_1_TO_1_12 = "v1_11_1to1_12";
    public static final String V1_12_TO_1_12_1 = "v1_12to1_12_1";
    public static final String V1_12_1_TO_1_12_2 = "v1_12_1to1_12_2";
    public static final String V1_12_2_TO_1_13 = "v1_12_2to1_13";
    public static final String V1_13_TO_1_13_1 = "v1_13to1_13_1";
    public static final String V1_13_1_TO_1_13_2 = "v1_13_1to1_13_2";
    public static final String V1_13_2_TO_1_14 = "v1_13_2to1_14";
    public static final String V1_14_TO_1_14_1 = "v1_14to1_14_1";
    public static final String V1_14_1_TO_1_14_2 = "v1_14_1to1_14_2";
    public static final String V1_14_2_TO_1_14_3 = "v1_14_2to1_14_3";
    public static final String V1_14_3_TO_1_14_4 = "v1_14_3to1_14_4";
    public static final String V1_14_4_TO_1_15 = "v1_14_4to1_15";
    public static final String V1_15_TO_1_15_1 = "v1_15to1_15_1";
    public static final String V1_15_1_TO_1_15_2 = "v1_15_1to1_15_2";
    public static final String V1_15_2_TO_1_16 = "v1_15_2to1_16";
    public static final String V1_16_TO_1_16_1 = "v1_16to1_16_1";
    public static final String V1_16_1_TO_1_16_2 = "v1_16_1to1_16_2";
    public static final String V1_16_2_TO_1_16_3 = "v1_16_2to1_16_3";
    public static final String V1_16_3_TO_1_16_4 = "v1_16_3to1_16_4";
    public static final String V1_16_4_TO_1_17 = "v1_16_4to1_17";
    public static final String V1_17_TO_1_17_1 = "v1_17to1_17_1";
    public static final String V1_17_1_TO_1_18 = "v1_17_1to1_18";
    public static final String V1_18_TO_1_18_2 = "v1_18to1_18_2";
    public static final String V1_18_2_TO_1_19 = "v1_18_2to1_19";
    public static final String V1_19_TO_1_19_1 = "v1_19to1_19_1";
    public static final String V1_19_1_TO_1_19_3 = "v1_19_1to1_19_3";
    public static final String V1_19_3_TO_1_19_4 = "v1_19_3to1_19_4";
    public static final String V1_19_4_TO_1_20 = "v1_19_4to1_20";
    public static final String V1_20_TO_1_20_2 = "v1_20to1_20_2";
    public static final String V1_20_2_TO_1_20_3 = "v1_20_2to1_20_3";
    public static final String V1_20_3_TO_1_20_5 = "v1_20_3to1_20_5";
    public static final String V1_20_5_TO_1_21 = "v1_20_5to1_21";
    public static final String V1_21_TO_1_21_2 = "v1_21to1_21_2";
    public static final String V1_21_2_TO_1_21_4 = "v1_21_2to1_21_4";
    public static final String V1_21_4_TO_1_21_5 = "v1_21_4to1_21_5";
    public static final String V1_21_5_TO_1_21_6 = "v1_21_5to1_21_6";
    public static final String V1_21_6_TO_1_21_7 = "v1_21_6to1_21_7";
    public static final String V1_21_7_TO_1_21_9 = "v1_21_7to1_21_9";
    public static final String V1_21_9_TO_1_21_11 = "v1_21_9to1_21_11";

    // ==================== 所有常量的不可变列表（存储原始字符串值） ====================

    public static final List<String> ALL_PROTOCOLS;

    static {
        List<String> list = new ArrayList<>();
        list.add(BASE);
        list.add(TEMPLATE);
        list.add(V1_8_TO_1_9);
        list.add(V1_9_TO_1_9_1);
        list.add(V1_9_1_TO_1_9_3);
        list.add(V1_9_3_TO_1_10);
        list.add(V1_10_TO_1_11);
        list.add(V1_11_TO_1_11_1);
        list.add(V1_11_1_TO_1_12);
        list.add(V1_12_TO_1_12_1);
        list.add(V1_12_1_TO_1_12_2);
        list.add(V1_12_2_TO_1_13);
        list.add(V1_13_TO_1_13_1);
        list.add(V1_13_1_TO_1_13_2);
        list.add(V1_13_2_TO_1_14);
        list.add(V1_14_TO_1_14_1);
        list.add(V1_14_1_TO_1_14_2);
        list.add(V1_14_2_TO_1_14_3);
        list.add(V1_14_3_TO_1_14_4);
        list.add(V1_14_4_TO_1_15);
        list.add(V1_15_TO_1_15_1);
        list.add(V1_15_1_TO_1_15_2);
        list.add(V1_15_2_TO_1_16);
        list.add(V1_16_TO_1_16_1);
        list.add(V1_16_1_TO_1_16_2);
        list.add(V1_16_2_TO_1_16_3);
        list.add(V1_16_3_TO_1_16_4);
        list.add(V1_16_4_TO_1_17);
        list.add(V1_17_TO_1_17_1);
        list.add(V1_17_1_TO_1_18);
        list.add(V1_18_TO_1_18_2);
        list.add(V1_18_2_TO_1_19);
        list.add(V1_19_TO_1_19_1);
        list.add(V1_19_1_TO_1_19_3);
        list.add(V1_19_3_TO_1_19_4);
        list.add(V1_19_4_TO_1_20);
        list.add(V1_20_TO_1_20_2);
        list.add(V1_20_2_TO_1_20_3);
        list.add(V1_20_3_TO_1_20_5);
        list.add(V1_20_5_TO_1_21);
        list.add(V1_21_TO_1_21_2);
        list.add(V1_21_2_TO_1_21_4);
        list.add(V1_21_4_TO_1_21_5);
        list.add(V1_21_5_TO_1_21_6);
        list.add(V1_21_6_TO_1_21_7);
        list.add(V1_21_7_TO_1_21_9);
        list.add(V1_21_9_TO_1_21_11);
        ALL_PROTOCOLS = Collections.unmodifiableList(list);
    }
}