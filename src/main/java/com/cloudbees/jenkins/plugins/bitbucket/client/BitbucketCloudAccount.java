package com.cloudbees.jenkins.plugins.bitbucket.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nonnull;

/**
 * Class that represents a bitbucket cloud user account
 */
public class BitbucketCloudAccount {
    private final String uuid;
    private final String accountId;
    private final String displayName;
    private final String nickname;

    public BitbucketCloudAccount(@Nonnull @JsonProperty("uuid") String uuid,
                                 @Nonnull @JsonProperty("account_id") String accountId,
                                 @Nonnull @JsonProperty("display_name") String displayName,
                                 @Nonnull @JsonProperty("nickname") String nickname) {
        this.uuid = uuid;
        this.accountId = accountId;
        this.displayName = displayName;
        this.nickname = nickname;
    }

    /**
     * Account's UUID
     * @return uuid
     */
    public @Nonnull String getId() {
        return uuid;
    }

    /**
     * Account's id
     * @return uuid
     */
    public @Nonnull String getAcctId() {
        return accountId;
    }

    /**
     * Accounts's display name
     * @return display name
     */
    public @Nonnull String getDisplayName() {
        return displayName;
    }

    /**
     * Account's nickname, not unique across users
     * @return nickname
     */
    public @Nonnull String getNickname() {
        return nickname;
    }
}
