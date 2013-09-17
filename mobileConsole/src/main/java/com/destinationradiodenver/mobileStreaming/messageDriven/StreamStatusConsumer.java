package com.destinationradiodenver.mobileStreaming.messageDriven;

import java.io.Serializable;

import javax.annotation.Resource;
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

import com.destinationradiodenver.mobileStreaming.application.Servers;
import com.destinationradiodenver.mobileStreaming.application.Streams;
import com.destinationradiodenver.mobileStreaming.messages.StreamStatusMessage;
import com.destinationradiodenver.mobileStreaming.messages.StreamStatusMessage.Status;
import com.destinationradiodenver.mobileStreaming.web.entity.Red5Server;
import com.destinationradiodenver.mobileStreaming.web.entity.Stream;

@MessageDriven(name = "streamStatusConsumer", activationConfig = {
		@ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
		@ActivationConfigProperty(propertyName = "destination", propertyValue = "java:jboss/jms/topic/streamStatusTopic") })
public class StreamStatusConsumer implements Serializable, MessageListener {

	/**
	 * @author cpenhale
	 */
	private static final long serialVersionUID = 1102064321674684886L;
	
	private Logger log = Logger.getLogger(StreamStatusConsumer.class);
	
	@Inject
	private Servers servers;
	
	@Inject
	private Streams streams;
	
	@PersistenceContext(unitName="mobileConsolePersistence")
	EntityManager em;
	
	
	public Stream generateStream(Red5Server server, String hostname, String scopeName, String publishedName){
		Stream newStream = new Stream();
		newStream.setServer(server);
		String rtmpUri = "rtmp://";
		rtmpUri += hostname;
		rtmpUri += "/";
		rtmpUri += scopeName;
		rtmpUri += "/";
		rtmpUri += publishedName;
		log.infof("Constructed rtmpUri %s", rtmpUri);
		newStream.setRtmpUri(rtmpUri);
		newStream.setAutomaticallyStartEncoders(false);
		newStream.setDescription("Automatically Generated Stream");
		newStream.setFriendlyName(publishedName);
		newStream.setRestartEncodersEveryMinutes(0);
		return newStream;
	}

	public void onMessage(Message message) {
		log.info("Recorder received a message");
		try{
			ObjectMessage objM = (ObjectMessage) message;
			Object object = objM.getObject();
				StreamStatusMessage statusMessage = (StreamStatusMessage) object;
				log.infof("statusMessage received from JMS Message (%s). Checking for associated server...", objM.getJMSMessageID().toString());
				String hostname = statusMessage.getServerHostname();
				TypedQuery<Red5Server> tQ = em.createQuery("select red5server from Red5Server as red5server where red5server.hostname is :hostname", Red5Server.class).setParameter("hostname", hostname);
				try{
					Red5Server server = tQ.getSingleResult();
					log.infof("Found a Red5 server for hostname %s", hostname);
					if(server.getEnabled()){
						log.infof("statusMessage recieved from active server %s", hostname);
						Stream stream = generateStream(server, hostname, statusMessage.getScopeName(), statusMessage.getPublishedName());
						try{
							TypedQuery<Stream> tQToo = em.createQuery("select stream from Stream as stream where stream.rtmpUri is :rtmpUri", Stream.class).setParameter("rtmpUri", stream.getRtmpUri());
							Stream persistedStream = tQToo.getSingleResult();
							stream = persistedStream;
						}catch (NoResultException ex){
							log.info("Did not find stream, persisting");
							em.persist(stream);
							try{
								TypedQuery<Stream> tQthree = em.createQuery("select stream from Stream as stream where stream.rtmpUri is :rtmpUri", Stream.class).setParameter("rtmpUri", stream.getRtmpUri());
								Stream persistedStream = tQthree.getSingleResult();
								stream = persistedStream;
								log.info(stream.toString());
							}catch (NoResultException extoo){
								log.error("Unable to find persisted stream");
								throw new RuntimeException("Unable to find persisted stream");
							}
						}
						if(statusMessage.getStatus() == Status.ADDED){
							streams.addStream(stream);
						}else if(statusMessage.getStatus() == Status.REMOVED){
							if(!streams.removeStream(stream))
								log.errorf("attempted to remove non-existent stream from Streams singleton");
						}else if(statusMessage.getStatus() == Status.SUBSCRIBER_ADDED){
							streams.addListener(stream);
						}else if(statusMessage.getStatus() == Status.SUBSCRIBER_REMOVED){
							streams.removeListener(stream);
						}else if(statusMessage.getStatus() == Status.STARTED_RECORDING){
							streams.addStreamRecording(stream);
						}else if(statusMessage.getStatus() == Status.STOPPED_RECORDING){
							streams.removeStreamRecording(stream);
						}else{
							log.errorf("Received invalid status %s in JMS Message %s", statusMessage.getStatus().toString(), objM.getJMSMessageID().toString());
						}
					}else{
						log.infof("statusMessage recieved from inactive server %s", hostname);
					}
				}catch (NoResultException ex){
					log.infof("Received a JMS message from Red5 server %s that hasn't been added", hostname);
				}
		}catch (ClassCastException ccx){
			log.info("Not an ObjectMessage, ignoring");
		} catch (JMSException e) {
			log.errorf("JMS Exception: \n %s", e.getMessage());
		} catch (Exception ex){
			log.error("CATCHALL: "+ex.getMessage());
		}
	}
}