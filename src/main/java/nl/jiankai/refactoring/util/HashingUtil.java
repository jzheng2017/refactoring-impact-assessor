package nl.jiankai.refactoring.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashingUtil {

    public static String md5Hash(String plain) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        byte[] digest = messageDigest.digest(plain.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
