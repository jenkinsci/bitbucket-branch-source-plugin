/*
 * The MIT License
 *
 * Copyright (c) 2016-2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Jackson based JSON parser
 */
@Restricted(NoExternalUse.class)
public final class JsonParser {
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final ObjectMapper mapper = createObjectMapper();

    public static <T> T toJava(String data, Class<T> type) throws IOException {
        return toJava(new StringReader(data), type);
    }

    public static <T> T toJava(InputStream data, Class<T> type) throws IOException {
        return toJava(new InputStreamReader(data, Charsets.UTF_8), type);
    }

    public static <T> T toJava(Reader data, Class<T> type) throws IOException{
        return mapper.readValue(data, type);
    }

    public static String toJson(Object value) throws IOException {
        return mapper.writeValueAsString(value);
    }

    private static ObjectMapper createObjectMapper(){
        ObjectMapper mapper = new ObjectMapper();
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        mapper.setDateFormat(format);
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
