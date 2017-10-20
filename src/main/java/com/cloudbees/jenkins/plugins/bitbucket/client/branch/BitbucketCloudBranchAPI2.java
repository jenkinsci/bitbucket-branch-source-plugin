package com.cloudbees.jenkins.plugins.bitbucket.client.branch;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.text.ParseException;
import java.text.SimpleDateFormat;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCloudBranchAPI2 implements BitbucketBranch {

	private String name;

	private Target target;

	@Override
	public String getRawNode() {
		return this.target.getHash();
	}

	public String getName() {
		return name;
	}

	@Override
	public long getDateMillis() {
		final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
		try {
			return dateParser.parse(this.getTarget().getDate()).getTime();
		} catch (ParseException e) {
			return 0;
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public Target getTarget() {
		return target;
	}

	public void setTarget(Target target) {
		this.target = target;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Target {

		private String hash;
		private String date;

		public String getHash() {
			return hash;
		}

		public void setHash(String hash) {
			this.hash = hash;
		}

		public String getDate() {
			return date;
		}

		public void setDate(String date) {
			this.date = date;
		}
	}

}
