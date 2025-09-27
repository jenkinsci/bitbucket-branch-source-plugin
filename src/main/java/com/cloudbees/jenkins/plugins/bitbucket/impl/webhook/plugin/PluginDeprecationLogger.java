/*
 * The MIT License
 *
 * Copyright (c) 2025, Allan Burdajewicz
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs a deprecation warning for the Bitbucket Addons support in the plugin.
 * If the logger is configured to log FINE messages, the warning is logged at FINE level.
 * Otherwise, the warning is logged at WARNING level at most once every 5 minutes to avoid log flooding in very active environment.
 */
public class PluginDeprecationLogger {

    private static final long LOG_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private static final java.util.concurrent.atomic.AtomicLong lastDeprecationLog =
            new java.util.concurrent.atomic.AtomicLong(0);

    /**
     * Logs the deprecation warning if the last log was more than LOG_INTERVAL_MS ago.
     *
     * @param logger the logger to use
     */
    public static void log(Logger logger) {
        if(logger.isLoggable(Level.FINE)) {
            logger.fine("Bitbucket Addons support are deprecated in favor of native integrations. Please migrate to Bitbucket Server native webhook.");
        } else {
            long now = System.currentTimeMillis();
            long lastLog = lastDeprecationLog.get();
            if (now - lastLog > LOG_INTERVAL_MS) {
                if (lastDeprecationLog.compareAndSet(lastLog, now)) {
                    logger.log(
                        Level.WARNING,
                        () ->
                            "Bitbucket Addons support are deprecated in favor of native integrations. Please migrate to Bitbucket Server native webhook.");
                }
            }
        }
    }
}
