package pt.hive.cameo.util;

import java.util.Map;

public class Definitions {

    public static final String HTTPBIN_HOST = "httpbin.org";

    public static String getHttpBinUrl() {
        return getHttpBinUrl("http://");
    }

    public static String getHttpBinUrl(String prefix) {
        Map<String, String> environ = System.getenv();
        String httpbinHost = environ.containsKey("HTTPBIN") ?
                environ.get("HTTPBIN") : HTTPBIN_HOST;
        return prefix + httpbinHost;
    }

}
