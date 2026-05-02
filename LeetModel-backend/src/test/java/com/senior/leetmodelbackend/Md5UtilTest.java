package com.senior.leetmodelbackend;

import com.senior.leetmodelbackend.common.utils.Md5Util;
import org.junit.jupiter.api.Test;

public class Md5UtilTest {

    private static final String RAW_PASSWORD = "123456";

    @Test
    void printMd5() {
        String encoded = Md5Util.encode(RAW_PASSWORD);
        System.out.println("明文: " + RAW_PASSWORD);
        System.out.println("MD5:  " + encoded);
    }
}
