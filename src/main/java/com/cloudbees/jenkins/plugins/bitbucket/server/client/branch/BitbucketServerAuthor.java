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
package com.cloudbees.jenkins.plugins.bitbucket.server.client.branch;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the author information given by Bitbucket Server.
 *
 * @author Nikolas Falco
 * @since 2.2.14
 */
public class BitbucketServerAuthor {
    private String name;
    @JsonProperty("emailAddress")
    private String email;

    /**
     * Returns the author name provided by the commit.
     *
     * @return the commit author name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the author name provided by the commit.
     *
     * @param name the commit author name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the author email provided by the commit.
     *
     * @return the commit author email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the author email provided by the commit.
     *
     * @param email the commit author email
     */
    public void setEmail(String email) {
        this.email = email;
    }

}
