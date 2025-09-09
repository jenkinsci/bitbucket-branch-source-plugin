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
package com.cloudbees.jenkins.plugins.bitbucket.impl.casc;

import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.BitbucketPlugin;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class BitbucketServerEndpointConfiguratorTest {

    private static JenkinsRule r;

    @BeforeAll
    static void init(JenkinsRule rule) {
        r = rule;
        BitbucketPlugin.aliases();
    }

    @SetEnvironmentVariable(key = "SERVER_URL", value = "https://acme.bitbucket.com")
    @Test
    void test() {
        ConfigurationAsCode.get().configure(getClass().getResource("configuration-as-code-resolve-envvars.yml").toString());

        BitbucketEndpointConfiguration instance = BitbucketEndpointConfiguration.get();
        assertThat(instance.getEndpoints())
            .hasSize(1)
            .element(0)
            .satisfies(endpoint -> {
                assertThat(endpoint.getServerURL()).isEqualTo("https://acme.bitbucket.com");
            });
    }
}
