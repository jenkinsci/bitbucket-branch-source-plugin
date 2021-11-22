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
package com.cloudbees.jenkins.plugins.bitbucket.client;

import com.cloudbees.jenkins.plugins.bitbucket.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.IRequestAudit;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class BitbucketCloudApiClientTest {

    public String loadPayload(String api) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(getClass().getSimpleName() + "/" + api + "Payload.json")) {
            return IOUtils.toString(is, "UTF-8");
        }
    }

    @Test
    public void get_repository_parse_correctly_date_from_cloud() throws Exception {
        BitbucketCloudRepository repository = JsonParser.toJava(loadPayload("getRepository"), BitbucketCloudRepository.class);
        assertNotNull("update on date is null", repository.getUpdatedOn());
        Date date = DateUtils.getDate(2018, 4, 27, 15, 32, 8, 356);
        assertThat(repository.getUpdatedOn().getTime(), CoreMatchers.is(date.getTime()));
    }

    @Test
    public void verifyUpdateWebhookURL() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);
        IRequestAudit audit = ((IRequestAudit) client).getAudit();
        Optional<? extends BitbucketWebHook> webHook = client.getWebHooks().stream().filter(h -> h.getDescription().contains("Jenkins")).findFirst();
        assertTrue(webHook.isPresent());

        reset(audit);
        client.updateCommitWebHook(webHook.get());
        verify(audit).request(Mockito.eq("https://api.bitbucket.org/2.0/repositories/amuniz/test-repos/hooks/%7B202cf34e-7ccf-44b7-ba6b-8827a14d5324%7D"));
    }

}
