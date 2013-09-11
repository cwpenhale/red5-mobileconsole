package com.destinationradiodenver.mobileStreaming.application;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.ejb.AccessTimeout;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.inject.Named;

import org.jboss.logging.Logger;

import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage;
import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage.Task;
import com.destinationradiodenver.mobileStreaming.web.entity.Encoder;
import com.destinationradiodenver.mobileStreaming.web.entity.Stream;

@Startup
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@AccessTimeout(value=10, unit=TimeUnit.SECONDS)
@Named
public class Streams extends CopyOnWriteArrayList<Stream> implements Serializable {
	/**
	 * @author cpenhale
	 */
	private static final long serialVersionUID = -5411315964008656521L;
	private static final Logger log = Logger.getLogger(Streams.class);
	private ConcurrentHashMap<Stream, Integer> streamSubscribers;
	private CopyOnWriteArrayList<Stream> streamRecording;
	
	
	@Inject
	private Encoders encoders;
	
	@Inject
	private EncoderDispatcher encoderDispatcher;
	
	public Streams(){
	}
	
	@PostConstruct
	private void init(){
		log.info("Encoders Streams initialized");
		streamSubscribers = new ConcurrentHashMap<Stream, Integer>();
		streamRecording = new CopyOnWriteArrayList<Stream>();
	}
	
	public boolean addStream(Stream stream){
		log.infof("Stream %s added to mobileConsole Encoder list",stream.getFriendlyName());
		if(stream.getAutomaticallyStartEncoders()){
			log.infof("Stream %s added to mobileConsole Encoder list wants to automatically start Encoders", stream.getFriendlyName());
			for(Encoder enc : stream.getEncoders()){
				log.infof("Starting Encoder %s for Stream %s", enc.getMobileProfile(), stream.getFriendlyName());
				EncoderDispatchMessage edm = EncoderDispatchMessage.generateEncoderDispatchMessage(enc);
				edm.setTask(Task.START_ENCODING);
				encoderDispatcher.dispatch(edm);
			}
		}
		getStreamSubscribers().put(stream, 0);
		return super.add(stream);
	}
	
	public boolean removeStream(Stream stream){
		for(Encoder enc : stream.getEncoders()){
			if(encoders.contains(enc)){
				EncoderDispatchMessage edm = EncoderDispatchMessage.generateEncoderDispatchMessage(enc);
				edm.setTask(Task.STOP_ENCODING);
				encoderDispatcher.dispatch(edm);
			}
		}
		getStreamSubscribers().remove(stream);
		return super.remove(stream);
	}
	
	public void addListener(Stream stream){
		Integer listeners = getStreamSubscribers().get(stream);
		listeners++;
		getStreamSubscribers().replace(stream, listeners);
	}
	
	public void removeListener(Stream stream){
		Integer listeners = getStreamSubscribers().get(stream);
		listeners--;
		getStreamSubscribers().replace(stream, listeners);
	}
	
	public Integer getListeners(Stream stream){
		Integer listeners = getStreamSubscribers().get(stream);
		for(Encoder enc : stream.getEncoders()){
			if(encoders.contains(enc)){
				listeners--;
			}
		}
		return listeners;
	}
	
	public void addStreamRecording(Stream stream){
		getStreamRecording().add(stream);
	}
	
	public void removeStreamRecording(Stream stream){
		getStreamRecording().remove(stream);
	}
	
	public boolean getStreamRecordingStatus(Stream stream){
		return getStreamRecording().contains(stream);
	}

	public ConcurrentHashMap<Stream, Integer> getStreamSubscribers() {
		return streamSubscribers;
	}

	public void setStreamSubscribers(ConcurrentHashMap<Stream, Integer> streamSubscribers) {
		this.streamSubscribers = streamSubscribers;
	}

	public CopyOnWriteArrayList<Stream> getStreamRecording() {
		return streamRecording;
	}

	public void setStreamRecording(CopyOnWriteArrayList<Stream> streamRecording) {
		this.streamRecording = streamRecording;
	}

	
}