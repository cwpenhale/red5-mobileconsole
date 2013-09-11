package com.destinationradiodenver.mobileStreaming.application;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import javax.jms.JMSException;

import org.jboss.logging.Logger;

import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage;
import com.destinationradiodenver.mobileStreaming.messages.StatusMessageDispatcher;

@ApplicationScoped
@Named
public class EncoderDispatcher extends StatusMessageDispatcher {
	private static final Logger log = Logger.getLogger(EncoderDispatcher.class);

	public EncoderDispatcher(){
		setJmsTopicLookup("java:jboss/jms/topic/encoderDispatchTopic");
	}
	
	public void dispatch(EncoderDispatchMessage statusMessage) {
	    log.info("Sending status message");
	    try{
	    	sendObjectMessage(statusMessage, "encoderdispatcher");
	    }catch (JMSException ex){
	    	log.error("Error sending status message");
	    	ex.printStackTrace();
	    }
	    log.info("Sent status message");
	}
	
}