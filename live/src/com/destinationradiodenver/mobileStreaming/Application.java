package com.destinationradiodenver.mobileStreaming;


import java.util.Collection;

import org.jboss.logging.Logger;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.ISubscriberStream;
import org.red5.server.stream.ClientBroadcastStream;

import com.destinationradiodenver.mobileStreaming.messages.StreamStatusMessage;
import com.destinationradiodenver.mobileStreaming.messages.StreamStatusMessage.Status;


public class Application extends MultiThreadedApplicationAdapter {

	private static final Logger log = Logger.getLogger(Application.class);
	private static final StreamStatusDispatcher streamStatusDispatcher = new StreamStatusDispatcher();

	@Override
	public void streamPublishStart(IBroadcastStream stream) {
		//TODO: Programatic hostname
		StreamStatusMessage statusMessage = createStreamStatusMessage(Status.ADDED, stream.getScope().getName(), stream.getPublishedName(), "127.0.0.1");
		streamStatusDispatcher.dispatch(statusMessage);
		super.streamPublishStart(stream);
	}
	  
	@Override
	public void streamBroadcastClose(IBroadcastStream stream) {
		//TODO: Programatic hostname
		log.infof("streamBroadcastClose: %s; %s", stream, stream.getPublishedName());
		StreamStatusMessage statusMessage = createStreamStatusMessage(Status.REMOVED, stream.getScope().getName(), stream.getPublishedName(), "127.0.0.1");
		streamStatusDispatcher.dispatch(statusMessage);
		super.streamBroadcastClose(stream);
	}

	@Override
	public void streamSubscriberStart(ISubscriberStream stream) {
		StreamStatusMessage statusMessage = createStreamStatusMessage(Status.SUBSCRIBER_ADDED, stream.getScope().getName(), stream.getBroadcastStreamPublishName(), "127.0.0.1");
		streamStatusDispatcher.dispatch(statusMessage);
		super.streamSubscriberStart(stream);
	}

	@Override
	public void streamSubscriberClose(ISubscriberStream stream) {
		StreamStatusMessage statusMessage = createStreamStatusMessage(Status.SUBSCRIBER_REMOVED, stream.getScope().getName(), stream.getBroadcastStreamPublishName(), "127.0.0.1");
		streamStatusDispatcher.dispatch(statusMessage);
		super.streamSubscriberClose(stream);
	}
	
    public void recordShow(String publishedName) {
    	 ClientBroadcastStream cbs = (ClientBroadcastStream) getBroadcastStream(getScope(), publishedName);
    	 String recordname = publishedName + String.valueOf(System.currentTimeMillis() / 1000L);
    	 try{
    		cbs.saveAs(recordname, false);
			StreamStatusMessage statusMessage = createStreamStatusMessage(Status.STARTED_RECORDING, getScope().getName(), publishedName, "127.0.0.1");
			streamStatusDispatcher.dispatch(statusMessage);
    	 }catch (Exception e){
    		 e.printStackTrace();
    	 }
    }
 
	public void stopRecordingShow(String publishedName) {
		ClientBroadcastStream cbs = (ClientBroadcastStream) getBroadcastStream(getScope(), publishedName);
		Collection<IStreamListener> isls = cbs.getStreamListeners();
		cbs.stopRecording();
		for(IStreamListener sl : isls)
			cbs.removeStreamListener(sl);
		StreamStatusMessage statusMessage = createStreamStatusMessage(Status.STOPPED_RECORDING, getScope().getName(), publishedName, "127.0.0.1");
		streamStatusDispatcher.dispatch(statusMessage);
	}
	
	public StreamStatusMessage createStreamStatusMessage(Status status, String scopeName, String publishedName, String hostname){
	    StreamStatusMessage statusMessage = new StreamStatusMessage();
	    statusMessage.setStatus(status);
	    statusMessage.setScopeName(scopeName);
	    statusMessage.setPublishedName(publishedName);
	    statusMessage.setServerHostname(hostname);
	    return statusMessage;
	}

}

