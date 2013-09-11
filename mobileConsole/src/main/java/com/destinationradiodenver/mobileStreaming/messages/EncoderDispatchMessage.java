package com.destinationradiodenver.mobileStreaming.messages;

import java.io.Serializable;

import com.destinationradiodenver.mobileStreaming.web.entity.Encoder;


public class EncoderDispatchMessage extends StatusMessage implements Serializable {


	/**
	 * @author cpenhale
	 * 
	 */
	private static final long serialVersionUID = -6469520985594757276L;
	
	public enum Task {
		START_ENCODING, STOP_ENCODING, STOP_RECORDING, START_RECORDING
	}

	private Task task;
	private String uri;
	private String name;
	private int bandwidth;
	private int width;
	private int height;
	
	public Task getTask() {
		return task;
	}
	public void setTask(Task task) {
		this.task = task;
	}
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

   public static EncoderDispatchMessage generateEncoderDispatchMessage(Encoder encoder){
	   EncoderDispatchMessage edm = new EncoderDispatchMessage();
	   edm.setUri(encoder.getStream().getRtmpUri());
	   edm.setBandwidth(encoder.getMobileProfile().getBandwidth());
	   edm.setWidth(encoder.getMobileProfile().getWidth());
	   edm.setHeight(encoder.getMobileProfile().getHeight());
	   edm.setName(encoder.getMobileProfile().getName());
	   return edm;
   }
	
}
