package com.destinationradiodenver.mobileStreaming.messages;

import java.io.Serializable;


public class StreamStatusMessage extends StatusMessage implements Serializable {

	/**
	 * @author cpenhale
	 */
	private static final long serialVersionUID = 4162067933953708375L;
	public enum Status {
		ADDED, REMOVED, SUBSCRIBER_ADDED, SUBSCRIBER_REMOVED, STARTED_RECORDING, STOPPED_RECORDING
	}

	private Status status;
	private String publishedName;
	private String scopeName;
	private String serverHostname;

	public Status getStatus() {
		return status;
	}
	public void setStatus(Status status) {
		this.status = status;
	}
	public String getServerHostname() {
		return serverHostname;
	}
	public void setServerHostname(String serverHostname) {
		this.serverHostname = serverHostname;
	}
	public String getPublishedName() {
		return publishedName;
	}
	public void setPublishedName(String publishedName) {
		this.publishedName = publishedName;
	}
	public String getScopeName() {
		return scopeName;
	}
	public void setScopeName(String scopeName) {
		this.scopeName = scopeName;
	}

}
