/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.plugins.bitbucket.avatars;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * Basic URL based Cache Source - Replacement for original functionality
 *
 */
public class UrlAvatarCacheSource implements AvatarCacheSource {
    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(UrlAvatarCacheSource.class.getName());

    private final String url;

    public UrlAvatarCacheSource(String url) {
        this.url = url;
    }

    @Override
    public boolean canFetch() {
        return (url != null && (url.startsWith("http://") || url.startsWith("https://")));
    }

    @Override
    public AvatarImage fetch(StandardCredentials credentials) {
        LOGGER.log(Level.FINE, "Attempting to fetch remote avatar: {0}", url);
        long start = System.nanoTime();
        try {
            if (!canFetch()) {
                LOGGER.log(Level.FINE, "Unable to fetch remote avatar: {0}", url);
                return AvatarImage.EMPTY;
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            try {
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                if (connection.getResponseCode() >= 400) {
                    LOGGER.log(Level.FINE, "Got invalid content response {1} for remote avatar image from {0}",
                            new String[] { url, String.valueOf(connection.getResponseCode()) });
                    return AvatarImage.EMPTY;
                }
                String contentType = connection.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    LOGGER.log(Level.FINE, "Got invalid content type {1} for remote avatar image from {0}",
                            new String[] { url, contentType });
                    return AvatarImage.EMPTY;
                }
                int length = connection.getContentLength();
                // buffered stream should be no more than 16k if we know the length
                // if we don't know the length then 8k is what we will use
                length = length > 0 ? Math.min(16384, length) : 8192;

                try (InputStream is = connection.getInputStream();
                     BufferedInputStream bis = new BufferedInputStream(is, length)) {
                    BufferedImage image = ImageIO.read(bis);
                    if (image == null) {
                        LOGGER.log(Level.FINE, "Got no remote avatar image from {0}", url);
                        return AvatarImage.EMPTY;
                    }
                    return new AvatarImage(image, connection.getLastModified());
                } catch (Exception e) {
                    LOGGER.log(Level.INFO, "1:" + e.getMessage(), e);
                }
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "2:" + e.getMessage(), e);
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            LOGGER.log(Level.INFO, e.getMessage(), e);
            return AvatarImage.EMPTY;
        } finally {
            long end = System.nanoTime();
            long duration = TimeUnit.NANOSECONDS.toMillis(end - start);
            LOGGER.log(duration > 250 ? Level.INFO : Level.FINE, "Avatar lookup of {0} took {1}ms",
                    new Object[] { url, duration });
        }
        return AvatarImage.EMPTY;
    }

    @Override
    public AvatarImage fetch() {
        return fetch(null);
    }

    @Override
    public String hashKey() {
        // TODO Auto-generated method stub
        return this.url;
    }
}
