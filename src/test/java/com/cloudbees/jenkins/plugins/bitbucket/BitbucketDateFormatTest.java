/*
 * The MIT License
 *
 * Copyright (c) 2018, Nikolas Falco
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

import com.cloudbees.jenkins.plugins.bitbucket.JsonParser.BitbucketDateFormat;
import com.cloudbees.jenkins.plugins.bitbucket.client.DateUtils;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

// TODO remove this class after update to jackson2 (api-plugin) version 2.9.2
public class BitbucketDateFormatTest {

    @Test
    public void test_custom_bitbucket_ISO8601_date_format() throws Exception {
        BitbucketDateFormat bdf = new BitbucketDateFormat();

        assertThat(bdf.parse("2018-09-12T14:56:34.008922+00:00"), is(DateUtils.getDate(2018, 9, 12, 14, 56, 34, 8)));
        assertThat(bdf.parse("2018-09-12T14:56:34.008922-00:00"), is(DateUtils.getDate(2018, 9, 12, 14, 56, 34, 8)));
        assertThat(bdf.parse("2018-09-12T14:56:34.008922Z"), is(DateUtils.getDate(2018, 9, 12, 14, 56, 34, 8)));
    }

}
