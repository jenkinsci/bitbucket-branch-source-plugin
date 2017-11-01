package com.cloudbees.jenkins.plugins.bitbucket.filesystem;

import java.io.IOException;
import java.io.InputStream;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMFile;


public class BitbucketSCMFile  extends SCMFile {

	private BitbucketApi api;
	private String ref;
	
	public String getRef() {
		return ref;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}

	public BitbucketSCMFile(BitbucketSCMFileSystem bitBucketSCMFileSystem,
							BitbucketApi api,
							String ref) {
		super();
		type(Type.DIRECTORY);
		this.api = api;
		this.ref = ref;
	}
	
    public BitbucketSCMFile(@NonNull BitbucketSCMFile parent, String name, Type type) {
    	super(parent, name);
    	this.api = parent.api;
    	this.ref = parent.ref;
    	type(type);
    	
    }
    
	@Override
	@NonNull
	public Iterable<SCMFile> children() throws IOException,
			InterruptedException {
        if (this.isDirectory()) {
            return api.getDirectoryContent(this);
        } else {
            throw new IOException("Cannot get children from a regular file");
        }
	}

	@Override
	@NonNull
	public InputStream content() throws IOException, InterruptedException {
        if (this.isDirectory()) {
            throw new IOException("Cannot get raw content from a directory");
        } else {
        	return api.getFileContent(this);
        }
	}

	@Override
	public long lastModified() throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	@NonNull
	protected SCMFile newChild(String name, boolean assumeIsDirectory) {
		return new BitbucketSCMFile(this, name, assumeIsDirectory?Type.DIRECTORY:Type.REGULAR_FILE);

	}

	@Override
	@NonNull
	protected Type type() throws IOException, InterruptedException {
		return this.getType();
	}

}
