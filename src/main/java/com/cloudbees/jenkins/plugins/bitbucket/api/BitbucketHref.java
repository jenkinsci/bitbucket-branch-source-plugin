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
package com.cloudbees.jenkins.plugins.bitbucket.api;

import java.util.ArrayList;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * A Href for something on bitbucket.
 */
public class BitbucketHref {
    private String name;
    private String href;

    // Used for marshalling/unmarshalling
    @Restricted(DoNotUse.class)
    public BitbucketHref() {
    }

    public BitbucketHref(String href) {
        this.href = href;
    }

    public BitbucketHref(String name, String href) {
        this.name = name;
        this.href = href;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public static class Deserializer extends ValueDeserializer<List<BitbucketHref>> {

        @Override
        public List<BitbucketHref> deserialize(JsonParser p, DeserializationContext ctx) {
            List<BitbucketHref> result = new ArrayList<>();
            JsonNode node = ctx.readTree(p);
            if (node.isArray()) {
                for (JsonNode n : node) {
                    result.add(ctx.readTreeAsValue(n, BitbucketHref.class));
                }
            } else {
                result.add(ctx.readTreeAsValue(node, BitbucketHref.class));
            }
            return result;
        }
    }

}
