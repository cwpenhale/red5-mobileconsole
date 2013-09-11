package org.red5.service.httpstream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.jboss.logging.Logger;
import org.red5.service.httpstream.model.MobileProfile;

import com.destinationradiodenver.mobileStreaming.singleton.AvailabilityService;

public class SegmentConsumer implements Runnable {

	private static final Logger log = Logger.getLogger(SegmentConsumer.class);
	private String streamName;
	private MobileProfile mobileProfile;
	private String pipePath;
	private final AtomicReference<Thread> currentThread = new AtomicReference<Thread>();
	private boolean interruptMe;
	
	private AvailabilityService availabilityService;
	
	public String getCurrentPipePath(int intSwitch){
		//FFMPEG will write first to /path/to/named/pipe0.ts and then to pipe1.ts,  as it wraps every other segment
		int current = -1;
		if(intSwitch<0)
			current = 0;
		if(intSwitch>0)
			current = 1;
		String actualPath = getPipePath()+current;
		actualPath += ".ts";
		log.tracef("actual path to pipe: %s", actualPath);
		return actualPath;
	}
	
	@Override
	public void run() {
	    currentThread.set(Thread.currentThread());
		log.errorf("Running SegmentConsumer");
		int intSwitch = -1;
		int negOne = -1;
		while(!isInterruptMe()){
			try {
				File actualPipe = new File(getCurrentPipePath(intSwitch));
				log.tracef("opening FIS");
				FileInputStream reader = new FileInputStream(actualPipe);
				log.tracef("opened");
				//byte[] array = IOUtils.toByteArray(reader);
				ByteArrayOutputStream oS = new ByteArrayOutputStream();
				log.tracef("new BAOS");
				byte[] b = new byte[1024];
				int bytesRead = 0;
				log.tracef("before open");
				log.tracef("ready to open");
				while ((bytesRead = reader.read(b)) != -1) {
				   log.tracef("reading");
				   oS.write(b, 0, bytesRead);
				}
				log.tracef("finish");
				byte[] array = oS.toByteArray();
				log.tracef("Copied FIS TO BAOS");
				IOUtils.closeQuietly(reader);
				log.tracef("FIS closed");
				log.tracef("converted to byte array");
				getAvailabilityService().update(getStreamName(), array);
				log.tracef("notified and sent");
				/*TODO: Determine if we need to sleep for any amount of time.
				 * because
				 * 	accessing the named pipe should be blocking until the segment is
				 *	done writing to the pipe. Once its done, we should instantly try to
				 *   open the pipe again (FFMPEG is faster than Java, I would think). If
				 *   we do indeed need to wait for a bit, let's play with the code below.
				 *				while(!(Application.transcoderReady(getPipePath()))){
					try {
						log.tracef("not ready to open");
						Thread.sleep(100);
					} catch (InterruptedException e1) {
					}
				}
				 *	try {
				 *		Thread.sleep(500);
				 *	} catch (InterruptedException e) {
				 *		log.errorf("interrupted: %s", e);
				 * 	}
				 */
				intSwitch = intSwitch*negOne;
			} catch (FileNotFoundException e) {
				log.errorf("FNF - Could not open FileInputStream, sleeping: %s", getCurrentPipePath(intSwitch));
				//we might still be setting up the pipe
				//wait half a second for it to come up
				try {
					Thread.sleep(500);
				} catch (InterruptedException e1) {
					log.errorf("Interrupt: %s", e1);
				}
			} catch (IOException e) {
				log.errorf("couldn't read byte array: %s", getCurrentPipePath(intSwitch));
				currentThread.get().interrupt();
			}
		}
	}
	
	public MobileProfile getMobileProfile() {
		return mobileProfile;
	}

	private String getPipePath() {
		return pipePath;
	}

	public void setStreamName(String streamName) {
		this.streamName = streamName;
	}

	public String getStreamName() {
		return streamName;
	}

	public void setPipePath(String path) {
		this.pipePath = path;
	}

	public void setMobileProfile(MobileProfile mP) {
		mobileProfile = mP;
	}

	public boolean isInterruptMe() {
		return interruptMe;
	}

	public void setInterruptMe(boolean interruptMe) {
		this.interruptMe = interruptMe;
	}

	public AvailabilityService getAvailabilityService() {
		return availabilityService;
	}

	public void setAvailabilityService(AvailabilityService availabilityService) {
		this.availabilityService = availabilityService;
	}
	
}
