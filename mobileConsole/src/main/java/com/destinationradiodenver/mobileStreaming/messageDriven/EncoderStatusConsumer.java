package com.destinationradiodenver.mobileStreaming.messageDriven;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.jboss.logging.Logger;

import com.destinationradiodenver.mobileStreaming.application.Encoders;
import com.destinationradiodenver.mobileStreaming.messages.EncoderStatusMessage;
import com.destinationradiodenver.mobileStreaming.messages.EncoderStatusMessage.Status;
import com.destinationradiodenver.mobileStreaming.web.entity.Encoder;

@MessageDriven(name = "encoderStatusConsumer", activationConfig = {
		@ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
		@ActivationConfigProperty(propertyName = "destination", propertyValue = "java:jboss/jms/topic/encoderStatusTopic") })
public class EncoderStatusConsumer implements Serializable, MessageListener {

	/**
	 * @author cpenhale
	 */
	private static final long serialVersionUID = 1102064321674684886L;
	
	//Matches: rtmp://127.0.0.1/live/derp key=value key=value key=value
	private static final Pattern LONG_FORM_URI_PATTERN = Pattern.compile("(^rtmp://.*/.*/.*)\\s(.*)\\s(.*)\\s(.*)");
	
	private Logger log = Logger.getLogger(EncoderStatusConsumer.class);
	
	
	@Inject
	private Encoders encoders;
	
	@PersistenceContext(unitName="mobileConsolePersistence")
	EntityManager em;
	
	public void onMessage(Message message) {
		log.info("Recorder received a message");
		try{
			ObjectMessage objM = (ObjectMessage) message;
			Object object = objM.getObject();
			try{
				EncoderStatusMessage statusMessage = (EncoderStatusMessage) object;
				String rtmpUri = statusMessage.getUri();
				Matcher matcher = LONG_FORM_URI_PATTERN.matcher(rtmpUri);
				if(matcher.matches()){
					rtmpUri = matcher.group(1);
				}
				log.infof("EncoderStatusMessage received from JMS Message (%s). Checking for associated encoder for RTMP URI \"%s\" with MobileProfile Name \"%s\"...",
						objM.getJMSMessageID().toString(), rtmpUri, statusMessage.getName());
				TypedQuery<Encoder> tQ = em.createQuery(
						"select encoder from Encoder as encoder where encoder.stream.rtmpUri is :rtmpUri and encoder.mobileProfile.name is :mPName",
						Encoder.class)
						.setParameter("rtmpUri", rtmpUri)
						.setParameter("mPName", statusMessage.getName());
				try{
					Encoder encoder = tQ.getSingleResult();
					log.infof("Found a Encoder for RTMP URI  %s", rtmpUri);
					if(statusMessage.getStatus() == Status.STARTED_ENCODING){
						log.infof("Adding Encoder for RTMP URI  %s to list of active encoders", rtmpUri);
						encoders.add(encoder);
					}else if(encoders.contains(encoder)){
						log.infof("Removing encoder for RTMP URI %s from list of active encoders", rtmpUri);
						encoders.remove(encoder);
					}else{
						log.infof("Received a message from an inactive encoder for %s", rtmpUri);
					}
				}catch (NoResultException e){
					log.infof("EncoderStatusMessage recieved for unknown RTMP URI %s", rtmpUri);
				}
			}catch(ClassCastException ccxTwo){
				log.infof("Object in ObjectMessge %s not an EncoderStatusMessage", message.getJMSMessageID().toString());
			}
		}catch (ClassCastException ccx){
			log.info("Not an ObjectMessage, ignoring");
		} catch (JMSException e) {
			log.errorf("JMS Exception: \n %s", e.getMessage());
		} catch (Exception e){
			log.error(e.getMessage());
		}
	}
}