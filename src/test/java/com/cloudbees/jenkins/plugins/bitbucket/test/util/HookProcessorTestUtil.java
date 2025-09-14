/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.test.util;

import java.util.Map;
import java.util.UUID;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

public final class HookProcessorTestUtil {

    private HookProcessorTestUtil() {
    }

    public static Map<String, String> getCloudHeaders() {
        Map<String, String> headers = new CaseInsensitiveMap<>();
        headers.put("User-Agent", "Bitbucket-Webhooks/2.0");
        headers.put("X-Attempt-Number", "1");
        headers.put("Content-Type", "application/json");
        headers.put("X-Hook-UUID", UUID.randomUUID().toString());
        headers.put("X-Request-UUID", UUID.randomUUID().toString());
        headers.put("traceparent", UUID.randomUUID().toString());
        headers.put("User-Agent", "Bitbucket-Webhooks/2.0");
        return headers;
    }

    public static Map<String, String> getNativeHeaders() {
        Map<String, String> headers = new CaseInsensitiveMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("X-Request-Id", UUID.randomUUID().toString());
        headers.put("User-Agent", "Atlassian HttpClient 4.2.0 / Bitbucket-9.5.2 (9005002) / Default");
        return headers;
    }

    public static Map<String, String> getPluginHeaders() {
        Map<String, String> headers = new CaseInsensitiveMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("X-Bitbucket-Type", "server");
        headers.put("User-Agent", "Bitbucket version: 8.18.0, Post webhook plugin version: 7.13.41-SNAPSHOT");
        return headers;
    }
}
