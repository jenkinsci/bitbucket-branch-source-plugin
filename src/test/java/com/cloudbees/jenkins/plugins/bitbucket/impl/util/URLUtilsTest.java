package com.cloudbees.jenkins.plugins.bitbucket.impl.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class URLUtilsTest {

    @Test
    void remove_authority_info() {
        String url = URLUtils.removeAuthority("https://user:password@bitbucket.example.com/amuniz/test-repos?pagelen=100");
        assertThat(url).doesNotContain("username", "password");
    }

    @Disabled
    @Test
    void remove_jwt_query_param() {
        String url = URLUtils.removeAuthority("https://bitbucket.example.com/rest/mirroring/latest/upstreamServers/1/repos/2?jwt=TOKEN");
        assertThat(url).doesNotContain("jwt", "TOKEN");
    }
}
