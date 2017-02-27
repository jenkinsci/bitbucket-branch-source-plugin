package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionList;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.StringUtils;

@Extension(ordinal = 1000)
public class BitbucketMockApiFactory extends BitbucketApiFactory {
    private static final String NULL = "\u0000\u0000\u0000\u0000";
    private final Map<String, BitbucketApi> mocks = new HashMap<>();

    public static void clear() {
        instance().mocks.clear();
    }

    public static void add(String serverUrl, BitbucketApi api) {
        instance().mocks.put(StringUtils.defaultString(serverUrl, NULL), api);
    }

    public static void remove(String serverUrl) {
        instance().mocks.remove(StringUtils.defaultString(serverUrl, NULL));
    }

    private static BitbucketMockApiFactory instance() {
        return ExtensionList.lookup(BitbucketApiFactory.class).get(BitbucketMockApiFactory.class);
    }


    @Override
    protected boolean isMatch(@Nullable String serverUrl) {
        return mocks.containsKey(StringUtils.defaultString(serverUrl, NULL));
    }

    @NonNull
    @Override
    protected BitbucketApi create(@Nullable String serverUrl, @Nullable StandardUsernamePasswordCredentials credentials,
                                  @NonNull String owner, @CheckForNull String repository) {
        return mocks.get(StringUtils.defaultString(serverUrl, NULL));
    }
}
