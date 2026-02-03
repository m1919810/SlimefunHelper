package me.matl114.tests;

import me.matl114.bukkit.*;
import me.matl114.utils.Debug;
import org.junit.jupiter.api.Test;

public class CommonTests {
    @Test
    public void test_1(){

        Debug.info("check 1");
        Debug.info("running ItemStack tests 2");

        Debug.info(BukkitConfigDeserializor.TEST_CASE);
        BukkitSerializationMock.initTest();
        try{
            Debug.info(BukkitConfigDeserializor.deserializeItemFromStringTest(BukkitConfigDeserializor.TEST_CASE));
        }catch (Throwable e){
            Debug.info(e);
        }

    }
    public static ConfigurationSerializableDataType<BukkitItemStack> DATATYPE_MOCKITEMSTACK=new ConfigurationSerializableDataType(BukkitItemStack.class);
    @Test
    public void test_2(){
        Debug.info("check 2");
        Debug.info("running ItemStack tests 2" );
        BukkitSerializationMock.initTest();
        try{
            Debug.info(DATATYPE_MOCKITEMSTACK.fromPrimitive(ConfigurationSerializableDataType.TEST_CASE));
        }catch (Throwable e){
            Debug.info(e);
        }
    }
}
