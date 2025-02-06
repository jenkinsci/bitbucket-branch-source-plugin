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
package com.cloudbees.jenkins.plugins.bitbucket.avatars;

import com.cloudbees.jenkins.plugins.bitbucket.impl.BitbucketPlugin;
import com.cloudbees.jenkins.plugins.bitbucket.impl.avatars.BitbucketTeamAvatarMetadataAction;
import hudson.model.Items;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketTeamAvatarMetadataActionTest {

    @Test
    void test_deserialisation() {
        BitbucketPlugin.aliases();

        Object state = Items.XSTREAM2.fromXML(this.getClass().getResource("state.xml"));
        assertThat(state)
            .isNotNull()
            .hasFieldOrProperty("actions")
            .extracting("actions")
            .asInstanceOf(InstanceOfAssertFactories.MAP)
            .hasSize(1)
            .extractingByKey("com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator::https://bitbucket.org::amuniz")
            .asInstanceOf(InstanceOfAssertFactories.LIST)
            .hasAtLeastOneElementOfType(BitbucketTeamAvatarMetadataAction.class);
    }
}
