package com.cloudbees.jenkins.plugins.bitbucket.impl.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.MalformedURLException;
import java.net.URL;

public final class URLUtils {

    private URLUtils() {
    }

    @Nullable
    public static String removeAuthority(@CheckForNull String url) {
        if (url != null) {
            try {
                URL linkURL = new URL(url);
                URL cleanURL = new URL(linkURL.getProtocol(), linkURL.getHost(), linkURL.getPort(), linkURL.getFile());
                return cleanURL.toExternalForm();
            } catch (MalformedURLException e) {
                // do nothing, URL can not be parsed, leave as is
            }
        }
        return url;
    }
}
