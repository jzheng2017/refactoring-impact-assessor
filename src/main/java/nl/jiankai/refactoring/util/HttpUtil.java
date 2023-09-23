package nl.jiankai.refactoring.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

public class HttpUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtil.class);

    public static boolean validUrl(String url) {
        if (url.isBlank()) {
            return false;
        }

        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            LOGGER.warn("Invalid url: {}", url);
            return false;
        }
    }
}
