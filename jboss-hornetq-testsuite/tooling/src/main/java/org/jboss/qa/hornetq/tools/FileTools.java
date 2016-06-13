package org.jboss.qa.hornetq.tools;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class FileTools {

    public static String getFingerPrint(File file) throws Exception {
        return getFingerPrintInternal(file, file.getAbsolutePath(), null);
    }

    public static String getFingerPrint(File file, String excludePattern) throws Exception {
        return getFingerPrintInternal(file, file.getAbsolutePath(), Pattern.compile(excludePattern));
    }

    private static String getFingerPrintInternal(File file, String relativeTo, Pattern excludePattern) throws Exception {

        if (excludePattern != null && excludePattern.matcher(file.getName()).matches()) {
            return null;
        }

        if (file.isFile()) {
            String path = file.getAbsolutePath();
            FileInputStream is = new FileInputStream(file);
            String md5 = DigestUtils.md5Hex(is);
            is.close();
            StringBuilder sb = new StringBuilder(relativize(path, relativeTo)).append(": ").append(md5);
            return sb.toString();
        } else {
            List<String> fingerPrints = new ArrayList<String>();
            for (File child : file.listFiles()) {
                String fingerPrint = getFingerPrintInternal(child, relativeTo, excludePattern);

                if (fingerPrint != null) {
                    fingerPrints.add(fingerPrint);
                }
            }
            Collections.sort(fingerPrints);
            if (fingerPrints.size() == 0) {
                return null;
            } else {
                return StringUtils.join(fingerPrints, '\n');
            }
        }
    }

    private static String relativize(String path, String base) {
        return new File(base).toURI().relativize(new File(path).toURI()).getPath();
    }

}
