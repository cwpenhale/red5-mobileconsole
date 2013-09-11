package com.destinationradiodenver.mobileStreaming;

import javax.inject.Named;
import javax.jms.JMSException;

import org.jboss.logging.Logger;

import com.destinationradiodenver.mobileStreaming.messages.EncoderStatusMessage;
import com.destinationradiodenver.mobileStreaming.messages.StatusMessageDispatcher;

@Named(value="encoderStatusDispatcher")
public class EncoderStatusDispatcher extends StatusMessageDispatcher {

	private static final Logger log = Logger.getLogger(EncoderStatusDispatcher.class);
	
	public EncoderStatusDispatcher(){
		setJmsTopicLookup("java:jboss/jms/topic/encoderStatusTopic");
	}
	
	public void dispatch(EncoderStatusMessage statusMessage) {
	    log.info("Sending status message");
	    try{
	    	sendObjectMessage(statusMessage, "encoder");
	    }catch (JMSException ex){
	    	log.error("Error sending status message");
	    	ex.printStackTrace();
	    }
	    log.info("Sent status message");
	}
}