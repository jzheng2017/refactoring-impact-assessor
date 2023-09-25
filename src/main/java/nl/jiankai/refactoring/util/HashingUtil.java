package nl.jiankai.refactoring.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashingUtil {

    public static String md5Hash(String plain) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        return new String(messageDigest.digest(plain.getBytes()));
    }
}
