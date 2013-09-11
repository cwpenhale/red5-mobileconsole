package com.destinationradiodenver.mobileStreaming.messages;

import java.io.Serializable;


public class EncoderStatusMessage extends StatusMessage implements Serializable {

	/**
	 * @author cpenhale
	 */
	private static final long serialVersionUID = -7429262189889079636L;
	
	public enum Status {
		STARTED_ENCODING, STOPPED_ENCODING_AS_EXPECTED, STOPPED_ENCODING_INTERRUPTED, STOPPED_ENCODING_FAILURE
	}

	private Status status;
	private String uri;
	private String name;
	private int bandwidth;
	private int width;
	private int height;
	
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getBandwidth() {
		return bandwidth;
	}
	public void setBandwidth(int bandwidth) {
		this.bandwidth = bandwidth;
	}
	public int getWidth() {
		return width;
	}
	public void setWidth(int width) {
		this.width = width;
	}
	public int getHeight() {
		return height;
	}
	public void setHeight(int height) {
		this.height = height;
	}
	public Status getStatus() {
		return status;
	}
	public void setStatus(Status status) {
		this.status = status;
	}

}
