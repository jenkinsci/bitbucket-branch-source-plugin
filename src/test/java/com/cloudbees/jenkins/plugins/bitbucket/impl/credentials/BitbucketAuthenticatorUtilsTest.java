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
package com.cloudbees.jenkins.plugins.bitbucket.impl.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.github.scribejava.core.model.OAuth2AccessTokenErrorResponse;
import com.github.scribejava.core.model.OAuthResponseException;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.oauth2.OAuth2Error;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketAuthenticatorUtilsTest {

    @Test
    void test_unwrap() throws Exception {
        Response rawResponse = new Response(401, "message", Collections.emptyMap(), "{\"type\": \"error\", \"error\": {\"message\": \"You may not have access to this repository or it no longer exists in this workspace. If you think this repository exists and you have access, make sure you are authenticated.\"}}");

        OAuthResponseException expectedException = new OAuth2AccessTokenErrorResponse(OAuth2Error.INVALID_CLIENT, "description", new URI(BitbucketCloudEndpoint.SERVER_URL), rawResponse);
        Exception e = new ExecutionException(new RuntimeException(expectedException));
        OAuth2AccessTokenErrorResponse cause = BitbucketAuthenticatorUtils.unwrap(e, OAuth2AccessTokenErrorResponse.class);
        assertThat(cause)
            .isNotNull()
            .isInstanceOf(OAuth2AccessTokenErrorResponse.class)
            .isSameAs(expectedException);

        OAuthResponseException oauth2Ex = new OAuthResponseException(rawResponse);
        e = new ExecutionException(oauth2Ex);
        cause = BitbucketAuthenticatorUtils.unwrap(e, OAuth2AccessTokenErrorResponse.class);
        assertThat(cause).isNull();
    }
}
