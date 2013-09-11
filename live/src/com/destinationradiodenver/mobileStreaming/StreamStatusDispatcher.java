package com.destinationradiodenver.mobileStreaming;

import javax.jms.JMSException;

import org.jboss.logging.Logger;

import com.destinationradiodenver.mobileStreaming.messages.StatusMessageDispatcher;
import com.destinationradiodenver.mobileStreaming.messages.StreamStatusMessage;

public class StreamStatusDispatcher extends StatusMessageDispatcher {
	private static final Logger log = Logger.getLogger(StreamStatusDispatcher.class);
	
	public StreamStatusDispatcher(){
		setJmsTopicLookup("java:jboss/jms/topic/streamStatusTopic");
	}
	
	public void dispatch(StreamStatusMessage statusMessage) {
	    log.info("Sending status message");
	    try{
	    	sendObjectMessage(statusMessage, "stream");
	    }catch (JMSException ex){
	    	log.error("Error sending status message");
	    	ex.printStackTrace();
	    }
	    log.info("Sent status message");
	}
}