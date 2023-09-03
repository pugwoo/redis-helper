package com.pugwoo.wooutils.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 内部常用工具类
 */
public class InnerCommonUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(InnerCommonUtils.class);

    private static final List<String> ipList = new ArrayList<>();

    static {
        String regex = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        Pattern pattern = Pattern.compile(regex);

        try {
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) {
                NetworkInterface i = interfaces.nextElement();
                for (Enumeration<InetAddress> addresses = i.getInetAddresses(); addresses.hasMoreElements();) {
                    InetAddress address = addresses.nextElement();
                    if (pattern.matcher(address.getHostAddress()).find() && !address.getHostAddress().startsWith("127.")) {
                        ipList.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            LOGGER.error("get local ip error", e);
        }
    }

    public static boolean isBlank(String str) {
        if (str == null) {
            return true;
        }
        int len = str.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 获得本机的ipv4的所有ip列表，排除本机ip 127.开头的
     */
    public static List<String> getIpv4IPs() throws SocketException {
        return ipList;
    }

}
