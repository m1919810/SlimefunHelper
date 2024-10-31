package me.matl114.BukkitUtiils;

import com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

public class PropertyMock {
    private final String name;
    private final String value;
    private final String signature;

    public PropertyMock(final String name, final String value) {
        this(name, value, null);
    }

    public PropertyMock(final String name, final String value, final String signature) {
        this.name = name;
        this.value = value;
        this.signature = signature;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getSignature() {
        return signature;
    }

    public boolean hasSignature() {
        return signature != null;
    }

    /**
     * @deprecated Use {@link YggdrasilServicesKeyInfo#validateProperty(com.mojang.authlib.properties.Property)}
     */
    @Deprecated
    public boolean isSignatureValid(final PublicKey publicKey) {
        try {
            final Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initVerify(publicKey);
            signature.update(value.getBytes(StandardCharsets.US_ASCII));
            return signature.verify(Base64.getDecoder().decode(this.signature));
        } catch (final NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            e.printStackTrace();
        }
        return false;
    }
}
