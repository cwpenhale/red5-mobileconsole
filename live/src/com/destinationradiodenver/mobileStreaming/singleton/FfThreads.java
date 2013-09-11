package com.destinationradiodenver.mobileStreaming.singleton;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.ejb.AccessTimeout;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.EJB;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.red5.service.httpstream.model.MobileProfile;

import com.destinationradiodenver.mobileStreaming.EncoderStatusDispatcher;
import com.destinationradiodenver.mobileStreaming.FfThread;
import com.destinationradiodenver.mobileStreaming.messages.EncoderStatusMessage;


@Startup
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@AccessTimeout(value=10, unit=TimeUnit.SECONDS)
@Lock(LockType.READ)
public class FfThreads extends ConcurrentHashMap<String, FfThread> implements Serializable {
	/**
	 * @author cpenhale
	 */
	private static final long serialVersionUID = -7647887359926613782L;
	private Logger log = Logger.getLogger(FfThreads.class);
	
	@Inject
	private AvailabilityService availabilityService;
	
	@EJB
	private SegmentConsumers segmentConsumers;
	
  	private static EncoderStatusDispatcher encoderStatusDispatcher = new EncoderStatusDispatcher();
  	
	public FfThreads(){
		log.info("Creating Ffthreads Singleton");
	}
	
	@PostConstruct
	private void init(){
		log.info("Encoders Ffthreads initialized");
	}
	
	@Lock(LockType.WRITE)
	public void startFfThread(FfThread ffThread){
		String name = ffThread.getStreamName()+"_"+ffThread.getMobileProfile().getName();
		log.infof("Attempting to start a new FfThread %s", name);
		if(super.containsKey(name)){
			log.warnf("Someone is attempting to add FfThread %s to FfThreads. Stopping existing FfThread before starting a new one", name);
			stopFfThread(name, ffThread.getSourceURI(), ffThread.getMobileProfile());
		}
		segmentConsumers.startSegmentConsumerForFfThread(ffThread);
		new Thread(ffThread).start();
		super.put(ffThread.getStreamName()+"_"+ffThread.getMobileProfile().getName(), ffThread);
	}
	
	@Lock(LockType.WRITE)
	public void stopFfThread(String name, String fullUri, MobileProfile mobileProfile){
		log.infof("Stopping FfThread %s", name);
		FfThread ffThread = super.get(name);
		if(ffThread!=null){
			ffThread.setInterruptMe(true);
			segmentConsumers.stopSegmentConsumer(name);
			super.remove(name);
		}else{
			log.error("Attempted to stop FfThread that doesn't exist in FfThreads. Sending 'stopped' message");
		    EncoderStatusMessage statusMessage = new EncoderStatusMessage();
		    statusMessage.setUri(fullUri);
		    statusMessage.setHeight(mobileProfile.getHeight());
		    statusMessage.setWidth(mobileProfile.getWidth());
		    statusMessage.setBandwidth(mobileProfile.getBandwidth());
		    statusMessage.setName(mobileProfile.getName());
		    encoderStatusDispatcher.dispatch(statusMessage);
		}
	}
	
}