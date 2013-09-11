package com.destinationradiodenver.mobileStreaming.messageDriven;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import org.jboss.logging.Logger;
import org.red5.service.httpstream.model.MobileProfile;

import com.destinationradiodenver.mobileStreaming.EncoderStatusDispatcher;
import com.destinationradiodenver.mobileStreaming.FfThread;
import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage;
import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage.Task;
import com.destinationradiodenver.mobileStreaming.singleton.AvailabilityService;
import com.destinationradiodenver.mobileStreaming.singleton.FfThreads;

@MessageDriven(name = "encoderDispatcher", activationConfig = {
		@ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
		@ActivationConfigProperty(propertyName = "destination", propertyValue = "java:jboss/jms/topic/encoderDispatchTopic") })
public class EncoderDispatchConsumer implements Serializable, MessageListener {

	/**
	 * @author cpenhale
	 */
	private static final long serialVersionUID = 1102064321674684886L;
	//REGEX
	private static final Pattern RTMP_URI_PATTERN = Pattern.compile("^rtmp://(.*)/(.*)/(.*)");
	//end REGEX
	private static final Logger log = Logger.getLogger(EncoderDispatchConsumer.class);
	
	@EJB
	private FfThreads ffThreads;
	
    @EJB
    private AvailabilityService availabilityService;

	
	public void onMessage(Message message) {
		log.info("Recorder received a message");
		try{
			ObjectMessage objM = (ObjectMessage) message;
			Object object = objM.getObject();
			try{
				EncoderDispatchMessage statusMessage = (EncoderDispatchMessage) object;
				log.infof("statusMessage received from JMS Message (%s). Checking for associated server from URI \"%s\"...", objM.getJMSMessageID().toString(), statusMessage.getUri());
				Matcher matcher = RTMP_URI_PATTERN.matcher(statusMessage.getUri());
				if(matcher.matches()){
					String fullUri = matcher.group(0);
					String app = matcher.group(2);
					String publishedName = matcher.group(3);
					//TODO: implement server checking by hostname
					MobileProfile mP = new MobileProfile();
					mP.setBandwidth(statusMessage.getBandwidth());
					mP.setHeight(statusMessage.getHeight());
					mP.setWidth(statusMessage.getWidth());
					mP.setName(statusMessage.getName());
					if(statusMessage.getTask()==Task.START_ENCODING){
						log.info("Starting Encoder");
						FfThread ffThread = new FfThread(statusMessage.getUri(), mP);
						ffThread.setStreamName(publishedName);
						ffThread.setSourceURI(String.format("%s app=%s subscribe=%s live=1", fullUri, app, publishedName));
						ffThreads.startFfThread(ffThread);
						availabilityService.addProfile(publishedName, mP);
					}else if(statusMessage.getTask()==Task.STOP_ENCODING){
						log.info("Stopping Encoder");
						ffThreads.stopFfThread(publishedName+"_"+statusMessage.getName(), fullUri, mP);
						availabilityService.removeProfile(publishedName, mP);
					}else if(statusMessage.getTask()==Task.START_RECORDING){
						log.info("Starting record");
						//application.recordShow(publishedName);
						log.info("Started record");
					}else if(statusMessage.getTask()==Task.STOP_RECORDING){
						//application.stopRecordingShow(publishedName);
					}
				}
			}catch(ClassCastException ccxTwo){
				log.infof("Object in ObjectMessge %s not an EncoderDispatchMessage", message.getJMSMessageID().toString());
			}
		}catch (ClassCastException ccx){
			log.info("Not an ObjectMessage, ignoring");
		} catch (JMSException e) {
			log.errorf("JMS Exception: \n %s", e.getMessage());
		}
	}
}