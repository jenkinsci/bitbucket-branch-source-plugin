package com.cloudbees.jenkins.plugins.bitbucket.client.branch;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCloudBranchs {

	private List<BitbucketCloudBranchAPI2> values;

	private String next;

	public String getNext() {
		return next;
	}

	public void setNext(String next) {
		this.next = next;
	}

	public List<BitbucketCloudBranchAPI2> getValues() {
		return values;
	}

	public void setValues(List<BitbucketCloudBranchAPI2> values) {
		this.values = values;
	}
}
