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

import java.awt.image.BufferedImage;

/**
 *
 * Interface for Avatar Cache Item Source
 *
 */
public interface AvatarCacheSource {
    /**
     * Holds Image and lastModified date
     */
    public static class AvatarImage {
        public final BufferedImage image;
        public final long lastModified;

        public static final AvatarImage EMPTY = new AvatarImage(null, 0);

        public AvatarImage(final BufferedImage image, final long lastModified) {
            this.image = image;
            this.lastModified = lastModified;
        }
    }

    /**
     *
     * Fetch image from source
     *
     * @return AvatarImage object
     */
    public AvatarImage fetch();

    /**
     * Get unique hashKey for this item
     *
     * @return AvatarImage object
     */
    public String hashKey();

    /**
     * Make sure we can fetch
     *
     * @return true if can fetch
     */
    public boolean canFetch();
}
