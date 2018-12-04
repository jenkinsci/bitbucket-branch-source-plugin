/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc., Nikolas Falco
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.text.ParsePosition;
import java.util.Date;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Jackson based JSON parser
 */
@Restricted(NoExternalUse.class)
public final class JsonParser {

    /**
     * Date formatter is because {@link StdDateFormat} and the
     * ISO8601DateFormat fails to parse some data format returned by the
     * Bitbucket REST APIs.
     *
     * <p>
     * The ISO8601DateFormat parser fail if there are more than 3 milliseconds.
     * The StdDateFormat parser before 2.9.2 returns null when the timezone is expressed in
     * the extended form [+-]hh:mm. and there are more than 3 milliseconds.
     *
     * @deprecated Replace with {@link StdDateFormat} when update jackson2 (api
     *             plugin) to version greater than 2.9.2
     * @author nikolasfalco
     */
    // TODO remove this class when update jackson2 (api plugin) to version greater than 2.9.2
    @Deprecated
    @Restricted(NoExternalUse.class)
    public static class BitbucketDateFormat extends ISO8601DateFormat {
        private static final long serialVersionUID = 1L;

        /*
         * ISO-8601 yyyy-MM-ddThh:mm:ss[.sss][Z|[+-]hh:mm]
         */
        @Override
        public Date parse(String source, ParsePosition pos) {
            String iso8601 = source;

            int msIdx = source.lastIndexOf('.');
            if (msIdx != -1) {
                int plusIdx = source.indexOf('+', msIdx);
                int minusIdx = source.indexOf('-', msIdx);
                int gmtIdx = source.indexOf('Z', msIdx);
                int lastMsIdx = Math.max(Math.max(plusIdx, minusIdx), gmtIdx);
                if (lastMsIdx != -1) {
                    // there are too many milliseconds
                    iso8601 = source.substring(0, msIdx + 4) + source.substring(lastMsIdx);
                }
            }

            return super.parse(iso8601, pos);
        }

    }

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
        // TODO remove date format when update jackson2 (api plugin) to version greater than 2.9.2
        mapper.setDateFormat(new BitbucketDateFormat());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
