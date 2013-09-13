package com.destinationradiodenver.mobileStreaming.application;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.ejb.AccessTimeout;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;

import org.jboss.logging.Logger;

import com.destinationradiodenver.mobileConsole.tasks.StopEncoderTask;
import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage;
import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage.Task;
import com.destinationradiodenver.mobileStreaming.web.entity.Encoder;
import com.destinationradiodenver.mobileStreaming.web.entity.Stream;

@Startup
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@AccessTimeout(value = 10, unit = TimeUnit.SECONDS)
@Named
public class Encoders extends CopyOnWriteArrayList<Encoder> implements Serializable {
	/**
	 * @author cpenhale
	 */
	private static final long serialVersionUID = -5411315964008656521L;
	private static final Logger log = Logger.getLogger(Encoders.class);
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	@PersistenceContext
	private EntityManager entityManager;
	
	private static ConcurrentHashMap<String,Future<StopEncoderTask>> running = new ConcurrentHashMap<String,Future<StopEncoderTask>>();

	@Inject
	EncoderDispatcher encoderDispatcher;

	public Encoders() {
	}
	
	@Override
	public boolean add(Encoder o){
		if(super.contains(o)){
			if(o.getStream()!=null){
				log.infof("Encoders was asked to add an Encoder that already exists (%s). Stopping/removing existing Encoder before continuing", o.getStream()+"_"+o.getMobileProfile().getName());
				remove(o);
			}
		}
		log.info("Adding encoder");
		boolean status = super.add(o);
		Stream stream = o.getStream();
		if(stream!=null){
			if(stream.getRestartEncodersEveryMinutes()>0){
				ScheduledFuture<StopEncoderTask> task = (ScheduledFuture<StopEncoderTask>) scheduler.scheduleAtFixedRate(new StopEncoderTask(o), stream.getRestartEncodersEveryMinutes(), stream.getRestartEncodersEveryMinutes(), TimeUnit.MINUTES);
				running.put(o.getStream()+"_"+o.getMobileProfile().getName(),task);
				log.infof("scheduled encoder restart %s minutes from now ad infinitum", stream.getRestartEncodersEveryMinutes());
			}else{
				log.info("not scheduling encoder restart");
			}
		}else{
			log.info("failed to get stream");
			log.info("not scheduling encoder restart");
		}
		return status;
	}

	@Override
	public boolean remove(Object o){
		log.info("Removing encoder");
		boolean status = super.remove(o);
		if(o instanceof Encoder){
			Encoder encoder = (Encoder) o;
			Stream stream = encoder.getStream();
			if(stream!=null){
				if(stream.getAutomaticallyStartEncoders()){
					log.info("got stream");
					EncoderDispatchMessage edm = EncoderDispatchMessage.generateEncoderDispatchMessage(encoder);
					edm.setTask(Task.START_ENCODING);
					encoderDispatcher.dispatch(edm);
					log.info("dispatched automatic restart message");
				}else{
					log.info("not automatically restarting encoder");
					if(stream.getRestartEncodersEveryMinutes()>0){
						log.info("Cancelling auto restart encoder task");
						Future<StopEncoderTask> task = running.get(stream+"_"+encoder.getMobileProfile().getName());
						task.cancel(false);
						running.remove(encoder.getStream()+"_"+encoder.getMobileProfile().getName());
					}
				}
			}else{
				log.info("failed to get stream");
			}
		}else{
			log.info("not an encoder");
		}
		return status;
	}
}