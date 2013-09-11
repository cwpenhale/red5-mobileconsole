package com.destinationradiodenver.mobileConsole.tasks;

import javax.inject.Inject;

import org.jboss.logging.Logger;

import com.destinationradiodenver.mobileStreaming.application.EncoderDispatcher;
import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage;
import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage.Task;
import com.destinationradiodenver.mobileStreaming.web.entity.Encoder;

public class StopEncoderTask implements Runnable {
	private static final Logger log = Logger.getLogger(StopEncoderTask.class);
	
	@Inject
	EncoderDispatcher encoderDispatcher;
	
	private Encoder encoder;
	
	public StopEncoderTask(Encoder encoder){
		log.info("Creating a StopEncoderTask");
		setEncoder(encoder);
		log.infof("A StopEncoderTask has been created for encoder %s", getEncoder().getStream().getFriendlyName()+"_"+getEncoder().getMobileProfile().getName());
	}

	@Override
	public void run() {
		log.infof("Running StopEncoderTask for %s", getEncoder().getStream().getFriendlyName()+"_"+getEncoder().getMobileProfile().getName());
		EncoderDispatchMessage edm = EncoderDispatchMessage.generateEncoderDispatchMessage(getEncoder());
		edm.setTask(Task.STOP_ENCODING);
		encoderDispatcher.dispatch(edm);
		log.infof("Dispatched StopEncoderTask message for %s", getEncoder().getStream().getFriendlyName()+"_"+getEncoder().getMobileProfile().getName());
	}

	public Encoder getEncoder() {
		return encoder;
	}

	public void setEncoder(Encoder encoder) {
		this.encoder = encoder;
	}

}
