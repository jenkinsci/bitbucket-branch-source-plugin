/*
 * The MIT License
 *
 * Copyright (c) 2026, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.client.repository;

import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketCloudWebhookTest {

    @Test
    void test_secret() throws IOException {
        BitbucketCloudWebhook sut = JsonParser.toJava("""
                {
                    "type": "webhook_subscription",
                    "uuid": "{a96109fc-8ea6-41aa-9b0d-1c9e3619a7fc}",
                    "description": "Jenkins hook",
                    "url": "http://ci.example.lan:8090/jenkins/bitbucket-scmsource-hook/notify",
                    "active": true,
                    "skip_cert_verification": false,
                    "events": [
                        "pullrequest:created",
                        "pullrequest:fulfilled",
                        "repo:push",
                        "pullrequest:rejected",
                        "pullrequest:updated"
                    ],
                    "source": null,
                    "read_only": false,
                    "history_enabled": false,
                    "secret_set": true
                }
                """, BitbucketCloudWebhook.class);
        assertThat(sut.hasSecret(null)).isFalse();
        assertThat(sut.hasSecret("")).isFalse();
        assertThat(sut.hasSecret("password")).isTrue();
    }
}
