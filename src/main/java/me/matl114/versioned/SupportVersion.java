package me.matl114.versioned;

import lombok.Getter;
import net.minecraft.SharedConstants;

@Getter
public class SupportVersion {
    int major;
    int minor;
    public SupportVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public boolean isHigherOrEqualTo(int major, int minor) {
        if(major < this.major){
            return true;
        }else if(major > this.major){
            return false;
        }else if(minor <= this.minor){
            return true;
        }else {
            return false;
        }
    }

    public boolean isLowerOrEqualTo(int major, int minor) {
        if(major < this.major){
            return false;
        }else if(major > this.major){
            return true;
        }else if(minor < this.minor){
            return false;
        }else {
            return true;
        }
    }

    public boolean isEqual(int major, int minor) {
        return this.major == major && this.minor == minor;
    }

    public static SupportVersion create() {
        String version = SharedConstants.VERSION_NAME;
        String[] versions = version.split("\\.");
        try{
            return new SupportVersion(Integer.parseInt(versions[versions.length- 2]), Integer.parseInt(versions[versions.length - 1]));
        }catch (Throwable e){
            return new SupportVersion(21, 1);
        }
    }
}
