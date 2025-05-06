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
package com.cloudbees.jenkins.plugins.bitbucket.impl.util;

import com.fasterxml.jackson.databind.util.StdDateFormat;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public final class DateUtils {

    private DateUtils() {
    }

    @Nullable
    public static String formatToISO(@CheckForNull Date date) {
        return date != null ? new StdDateFormat().format(date) : null;
    }

    @Nullable
    public static Date parseISODate(@CheckForNull String isoDate) {
        try {
            return isoDate != null ? new StdDateFormat().parse(isoDate) : null;
        } catch (ParseException e) {
            return null;
        }
    }

    public static long isoDateToMillis(@CheckForNull String isoDate) {
        Date date = parseISODate(isoDate);
        return date != null ? date.getTime() : 0L;
    }

    @NonNull
    public static Date getDate(int year, int month, int day, int hours, int minutes, int seconds, int milliseconds) {
        return getDate(year, month, day, hours, minutes, seconds, milliseconds, "UTC");
    }

    @NonNull
    public static Date getDate(int year, int month, int day, int hours, int minutes, int seconds, int milliseconds, String timezoneId) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timezoneId));
        cal.set(year, month - 1, day, hours, minutes);
        cal.set(Calendar.SECOND, seconds);
        cal.set(Calendar.MILLISECOND, milliseconds);
        return cal.getTime();
    }

}
