package org.red5.service.httpstream.model;

public class MobileProfile {

	private int bandwidth;
	private String name;
	private int width;
	private int height;

	public void setName(String name) {
		this.name = name;
	}

	public void setBandwidth(int bandwidth) {
		this.bandwidth = bandwidth;
	}

	public int getBandwidth() {
		// TODO Auto-generated method stub
		return bandwidth;
	}

	public String getName() {
		// TODO Auto-generated method stub
		return name;
	}

	public int getWidth() {
		// TODO Auto-generated method stub
		return width;
	}

	public int getHeight() {
		// TODO Auto-generated method stub
		return height;
	}

	public void setWidth(int i) {
		width = i;
	}

	public void setHeight(int i) {
		height = i;
	}

	public Object getPipePath() {
		// TODO Auto-generated method stub
		return null;
	}

}
