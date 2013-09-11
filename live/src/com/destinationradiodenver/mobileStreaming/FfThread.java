package com.destinationradiodenver.mobileStreaming;

import java.io.IOException;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;
import org.red5.service.httpstream.model.MobileProfile;

import com.destinationradiodenver.mobileStreaming.messages.EncoderStatusMessage;
import com.destinationradiodenver.mobileStreaming.messages.EncoderStatusMessage.Status;
import com.xuggle.xuggler.IAudioResampler;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IAudioSamples.Format;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.ICodec.ID;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IContainerFormat;
import com.xuggle.xuggler.IContainerFormat.Flags;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat.Type;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;

public class FfThread implements Runnable {
	private static final Logger log = Logger.getLogger(FfThread.class.getName());
	
	private MobileProfile mobileProfile;
	private String sourceURI;
	private boolean mIsRunning;
	private String pipePath;
	private String streamName;
	private int mAudioStreamId;
	private int mVideoStreamId;
	private IContainer inC;
	private IContainer outC;
	private IContainerFormat outCF;
	private IContainerFormat inCF;
	private IStreamCoder mOutAudioCoder;
	private IStreamCoder mOutVideoCoder;
	private IStreamCoder mInAudioCoder;
	private IStreamCoder mInVideoCoder;
	private boolean codersSetup;
	private int codersSetupCounter;
	@SuppressWarnings("unused")
	private boolean doOnce;

	private IPacket iPacket;

	private IVideoResampler mVideoResampler;

	private IAudioResampler mAudioResampler;

	private IStream vOutStream;

	private IStream aOutStream;

	@SuppressWarnings("unused")
	private IStream mAInStream;

	@SuppressWarnings("unused")
	private IStream mVInStream;
	
	private boolean containersOpen;
	
	private final AtomicReference<Thread> currentThread = new AtomicReference<Thread>();

	private boolean interruptMe;

	private EncoderStatusDispatcher encoderStatusDispatcher;

	public FfThread(){
	}

	public FfThread(String sourceUri, MobileProfile mP){
		setEncoderStatusDispatcher(new EncoderStatusDispatcher());
		doOnce = true;
		mAudioStreamId = -1;
	    mVideoStreamId = -1;
	    codersSetup = false;
	    this.streamName = mP.getName();
	    setSourceURI(sourceUri);
	    this.mobileProfile = mP;
		//pipePath must be set on instantiation of the FfThread so that it can be consumed by an SegmentConsumer
		String pipeName = "/tmp/p_";
		Random random = new Random();
		pipeName += Math.abs(random.nextLong()); //Avoiding colliding or predictable pipe names to avoid cross-site attacks in /tmp
		pipeName += streamName;
		pipeName += mP.getName();
		pipePath = pipeName;
		setupPipes();
	}
	
	//Alternate constructor specifies existing pipes
	//	Does not create new pipes
	public FfThread(String streamName, MobileProfile mP, String pipeName){
		doOnce = true;
		mAudioStreamId = -1;
	    mVideoStreamId = -1;
	    codersSetup = false;
	    this.streamName = streamName;
	    this.mobileProfile = mP;
	    this.pipePath = pipeName;
	}
	
	private void setupPipes() {
		//TODO: Tunable by properties file
		String[] pipes = new String[2];
		pipes[0] =  pipePath+"0.ts";
		pipes[1] = pipePath+"1.ts";
		for(int i=0; i< pipes.length; i++) {
			try {
				Runtime.getRuntime().exec("mkfifo " + pipes[i]);
			} catch (IOException e) {
				try {
					Runtime.getRuntime().exec("rm -f" + pipes[i]);
				} catch (IOException e1) {
					try {
						Runtime.getRuntime().exec("mkfifo " + pipes[i]);
					} catch (IOException e2) {
						log.errorf("couldn't create pipes: %s", e2);
					}
					log.errorf("couldn't create pipes: %s", e1);
				}
				log.errorf("couldn't create pipes: %s", e);
			}
		}
	}
	
	private void tearDownPipes(){
		String[] pipes = new String[2];
		pipes[0] =  pipePath+"0.ts";
		pipes[1] = pipePath+"1.ts";
		for(int i=0; i< pipes.length; i++) {
			try {
				Runtime.getRuntime().exec("rm -f " + pipes[i]);
			} catch (IOException e) {
				log.errorf("couldn't tear down pipes: %s", e);
			}
		}
	}
	
	@Override
	public void run() {
	    currentThread.set(Thread.currentThread());
	    EncoderStatusMessage statusMessage = new EncoderStatusMessage();
	    statusMessage.setUri(sourceURI);
	    statusMessage.setHeight(mobileProfile.getHeight());
	    statusMessage.setWidth(mobileProfile.getWidth());
	    statusMessage.setBandwidth(mobileProfile.getBandwidth());
	    statusMessage.setName(mobileProfile.getName());
	    try{
	    	codersSetupCounter = 0;
			openContainer();
			containersOpen = true;
	    }catch (Exception e){
	    	log.errorf("Unhandled exception when attempting to open container: %s", e.getMessage());
	    }
	    if(containersOpen){
				try{
					transcode();
					log.info("Finished transcoding");
					statusMessage.setStatus(Status.STOPPED_ENCODING_AS_EXPECTED);
				}catch (InterruptedException e){
					log.infof("Caught Interruption During Transcoding: %s", e.getMessage());
					statusMessage.setStatus(Status.STOPPED_ENCODING_INTERRUPTED);
				}catch (Exception e){
					log.errorf("Uncaught During Transcoding: %s", e.getMessage());
					e.printStackTrace();
					statusMessage.setStatus(Status.STOPPED_ENCODING_FAILURE);
				}
	    }else{
	    	log.errorf("FfThread %s complete, containers never finished opening", Thread.currentThread().getName());
	    	statusMessage.setStatus(Status.STOPPED_ENCODING_FAILURE);
	    }
	    log.infof("FfThread %s is interrupted, closing containers", Thread.currentThread().getName());
	    closeContainer();
	    getEncoderStatusDispatcher().dispatch(statusMessage);
	}

	private void transcode() throws InterruptedException {
		iPacket = IPacket.make();
		log.tracef("Packets and Audio buffers created");
		log.tracef("FFThread declares ready: %s", pipePath);
		// 02-12-13 -- We now signal JMS to say we are running
		EncoderStatusMessage statusMessage = new EncoderStatusMessage();
		statusMessage.setStatus(Status.STARTED_ENCODING);
		statusMessage.setUri(sourceURI);
		statusMessage.setHeight(mobileProfile.getHeight());
		statusMessage.setWidth(mobileProfile.getWidth());
		statusMessage.setBandwidth(mobileProfile.getBandwidth());
		statusMessage.setName(mobileProfile.getName());
		log.info("Dispatching FfThread started message");
		getEncoderStatusDispatcher().dispatch(statusMessage);
		log.info("Dispatched FfThread started message");
		// end 02-12-13
		log.tracef("About your in-coder: %s", inC);
		while (inC.readNextPacket(iPacket) >= 0) {
			if(isInterruptMe())
				throw new InterruptedException("Transcoding is interrupted");
			log.tracef("reading packet");
			if (iPacket.isComplete()) {
				log.tracef("Packet is complete");
				if (!codersSetup){
					if(iPacket.isKeyPacket()) {
						log.tracef("Packet is key packet");
						openInputCoders(iPacket);
						log.tracef("openInputCoders finished");
					}
				}
				if (codersSetup) {
					int i = iPacket.getStreamIndex();
					if (i == mAudioStreamId) {
						log.tracef("Packet is audio");
						decodeAudio(iPacket);
					} else if (i == mVideoStreamId) {
						log.tracef("Packet is video");
						decodeVideo(iPacket);
					} else {
						log.errorf("dropping packet from stream we haven't set-up: %s", i);
					}
				}
			}
		}

	}
	
	private void writePacket(IPacket oPacket) {
		if(outC.writePacket(oPacket, true)<0){
			throw new RuntimeException("couldn't write output packet");
		}
	}


	private void encodeVideo(IVideoPicture postDecode) {
		int retval;
		IPacket oPacket = IPacket.make();
		retval = mOutVideoCoder.encodeVideo(oPacket, postDecode, 0);
		if(retval <= 0){
			log.tracef("couldnt encode IVP, continuing anyway");
		}
		if(oPacket.isComplete()){
			writePacket(oPacket);
		}
	}


	private void encodeAudio(IAudioSamples samples) {
		log.tracef("encode audio called : %s", samples);
		int retval = 0;
		IPacket oPacket = IPacket.make();
	    IAudioSamples preEncode = samples;
	    int numSamplesConsumed = 0;
		log.tracef("entering while loop");
	    while (numSamplesConsumed < preEncode.getNumSamples()) {
	    	if(mOutAudioCoder!=null){
	    		log.tracef("out coder is not null: %s", mOutAudioCoder);
		    	retval = mOutAudioCoder.encodeAudio(oPacket, preEncode, numSamplesConsumed);
		    	log.tracef("encode audio retval: %s", retval);
	    		if(retval <= 0){
		    		log.tracef("could not encode audio samples; continuing anyway");
		    		break;
		    	}
		    	log.tracef("no retval errorf");
		        numSamplesConsumed += retval;
				if(oPacket.isComplete()){
			    	log.tracef("opacket is complete: %s", oPacket);
					writePacket(oPacket);
			    	log.tracef("opacket is written");
				}
	    	}else{
	    		log.errorf("mOutAudioCodec was null");
	    	}
	    }
	}
	


	private void decodeAudio(IPacket packet) {
		int retval = -1;
	    IAudioSamples inSamples = IAudioSamples.make(1024, 2, Format.FMT_S16);
	    IAudioSamples reSamples = null;
	    int offset = 0;
	    log.tracef("ready to decode audio");
	    while (offset < packet.getSize()) {
	        retval = mInAudioCoder.decodeAudio(inSamples, packet, offset);
	        if (retval <= 0) {
	        	log.errorf("couldn't decode audio");
	            throw new RuntimeException("could not decode audio");
	        }
	        offset += retval;
		    log.tracef("audio loop offset: %s", offset);
		    IAudioSamples postDecode = inSamples;
			if (postDecode.isComplete()) {
			  log.tracef("postDecode was complete: %s", postDecode);
			  reSamples = resampleAudio(postDecode);
			  log.tracef("resample finished: %s", reSamples);
			} else {
			  log.tracef("postDecode was NOT completed: %s", postDecode);
			  reSamples = postDecode;
			}
			if (reSamples.isComplete()) {
			  log.tracef("reSamples isComplete: %s", reSamples);
			  encodeAudio(reSamples);
			} 
	    }
	    log.tracef("finished decoding audio packet");
	} 
	
	@SuppressWarnings("unused")
	private IAudioSamples resampleAudio(IAudioSamples samples){
	    IAudioSamples reSamples;
	    openAudioResampler(samples);
	    IAudioSamples outSamples = IAudioSamples.make(1024, 2, Format.FMT_S16);
	    if (mAudioResampler != null && samples.getNumSamples() > 0) {
	      log.tracef("ready to resample audio");
	      IAudioSamples preResample = samples;
	      if (preResample == null)
	    	  preResample = samples;
	      int retval = -1;
	      retval = mAudioResampler.resample(outSamples, preResample, preResample.getNumSamples());
	      if (retval < 0)
	        throw new RuntimeException("could not resample audio");
	      log.tracef("resampled %s input samples (%skhz %s channels) to %s output samples (%skhz %s channels)",
	          new Object[]{
	          preResample.getNumSamples(),
	          preResample.getSampleRate(),
	          preResample.getChannels(),
	          outSamples.getNumSamples(),
	          outSamples.getSampleRate(),
	          outSamples.getChannels()
	      	});
	      	IAudioSamples postResample= outSamples;
			if (postResample == null)
				postResample = outSamples;
			reSamples = postResample;
		    } else {
		      reSamples = samples;
		    }
		    return reSamples;
	  }
	
	private void openAudioResampler(IAudioSamples samples) {
	    if (mAudioResampler == null && mOutAudioCoder != null) {
			mAudioResampler = IAudioResampler.make(mOutAudioCoder.getChannels(), samples.getChannels(), mOutAudioCoder.getSampleRate(), samples.getSampleRate());
	        if (mAudioResampler == null) {
	          throw new RuntimeException("needed to resample audio but couldn't allocate a resampler");
	        }
	        log.tracef("Setup resample to convert \"%skhz %s channel audio\" to \"%skhz %s channel\" audio",
	            new Object[]{
	            mAudioResampler.getInputRate(),
	            mAudioResampler.getInputChannels(),
	            mAudioResampler.getOutputRate(),
	            mAudioResampler.getOutputChannels()
	        });
	    	// and we write the output header
	    	log.tracef("Converting \"%s %skhz %s channel\" input audio to \"%s %skhz %s channel\" output audio", new Object[]{
				mInAudioCoder.getCodecID().toString(),
				samples.getSampleRate(),
				samples.getChannels(),
				mOutAudioCoder.getCodecID().toString(),
				mOutAudioCoder.getSampleRate(),
				mOutAudioCoder.getChannels()
	    	});
	    }
	  }


	

	private void decodeVideo(IPacket packet) {
	    int retval = -1;
		IVideoPicture inPicture = IVideoPicture.make(mInVideoCoder
				.getPixelType(), mInVideoCoder.getWidth(), mInVideoCoder
				.getHeight());
		IVideoPicture reSample = null;
	    int offset = 0;
	    while (offset < packet.getSize()) {
	        retval = mInVideoCoder.decodeVideo(inPicture, packet, offset);
	        if(retval <= 0){
	        	log.tracef("noisy shit: decoding video failed for: %s", getSourceURI());
	        	return;
	        }
	        offset += retval;
		    IVideoPicture postDecode = inPicture;
		    if(postDecode.isComplete()){
		    	reSample = resampleVideo(postDecode);
		    }
		    if(reSample!=null)
			    if(reSample.isComplete()){
			    	encodeVideo(reSample);
			    }
	    }
	}

	private IVideoPicture resampleVideo(IVideoPicture postDecode) {
		IVideoPicture reSample;
		openVideoResampler(postDecode);
		if (mVideoResampler != null) {
			log.tracef("ready to resample video");
			IVideoPicture outPicture = IVideoPicture.make(mOutVideoCoder.getPixelType(), mOutVideoCoder.getWidth(), mOutVideoCoder.getHeight());
			IVideoPicture preResample = postDecode;
			if (mVideoResampler.resample(outPicture, preResample) < 0)
				throw new RuntimeException("could not resample video");
			if (log.isTraceEnabled())
				log.tracef("resampled input picture (type: %s; width: %s; height: %s) to output (type: %s; width: %s; height: %s)",
					new Object[] { preResample.getPixelType(),
						preResample.getWidth(),
						preResample.getHeight(),
						outPicture.getPixelType(),
						outPicture.getWidth(),
						outPicture.getHeight() });
			IVideoPicture postResample = outPicture;
			reSample = postResample;
		} else {
			reSample = postDecode;
		}
		return reSample;
	}
	
	  private void openVideoResampler(IVideoPicture picture)
	  {
	    if (mVideoResampler == null && mOutVideoCoder != null)
	    {
	      if (picture.getWidth() <= 0 || picture.getHeight()<=0)
	        throw new RuntimeException("frame has no data in it so cannot resample");

	      // We set up our resampler.
	      if (mOutVideoCoder.getPixelType() != picture.getPixelType() ||
	          mOutVideoCoder.getWidth() != picture.getWidth() ||
	          mOutVideoCoder.getHeight() != picture.getHeight())
	      {
	        mVideoResampler = IVideoResampler.make(
	            mOutVideoCoder.getWidth(),
	            mOutVideoCoder.getHeight(),
	            mOutVideoCoder.getPixelType(),
	            picture.getWidth(),
	            picture.getHeight(),
	            picture.getPixelType());
	        if (mVideoResampler == null)
	        {
	          log.errorf("Could not create a video resampler; this object is only available in the GPL version of aaffmpeg");
	          throw new RuntimeException("needed to resample video but couldn't allocate a resampler; you need the GPL version of AAFFMPEG installed?");
	        }
	        log.tracef("Setup resample to convert \"%sx%s %s video\" to \"%sx%s %s video\" audio",
	            new Object[]{
	            mVideoResampler.getInputWidth(),
	            mVideoResampler.getInputHeight(),
	            mVideoResampler.getInputPixelFormat(),
	            mVideoResampler.getOutputWidth(),
	            mVideoResampler.getOutputHeight(),
	            mVideoResampler.getOutputPixelFormat()
	            });
	      }
	    }
	  }


	private void openInputCoders(IPacket packet) {
        int numStreams = inC.getNumStreams();
        log.tracef("number of streams: %s", numStreams);
	    if (mAudioStreamId == -1 || mVideoStreamId == -1){
	        log.tracef("first condition true");
	    	for(int i = 0; i < numStreams; i++){
		        log.tracef("beginning for loop");
	            IStream stream = inC.getStream(i);
	            if(stream!=null){
			        log.tracef("got stream: %s", stream);
	            	IStreamCoder coder = stream.getStreamCoder();
	            	if(coder != null){
				        log.tracef("got coder: %s", coder);
	            		//is audio stream
	            		if((coder.getCodecType()==ICodec.Type.CODEC_TYPE_AUDIO)&&(mAudioStreamId==-1)&&(packet.getStreamIndex()==i)){
	            			log.tracef("got audio coder: %s", coder);
	            			if(coder.getCodec() != null){
	            				mInAudioCoder = coder;
	            		    	if (mInAudioCoder.open(null, null) < 0){
	            					log.errorf("died on open a coder");
	            		            throw new RuntimeException("could not open audio coder for stream: " + mAudioStreamId);
	            		    	}
		            			log.tracef("didnt die on audio, stream id is: %s", i);
		            			mAInStream = stream;
	                            mAudioStreamId = i;
	            			}else{
	            				log.errorf("died on open audio codec");
	            				throw new RuntimeException("died on open audio codec");
	            			}
	            		}
	            		//is video stream
	            		if((coder.getCodecType()==ICodec.Type.CODEC_TYPE_VIDEO)&&(mVideoStreamId==-1)&&(packet.getStreamIndex()==i)){
	            			log.tracef("got video coder: %s", coder);
	            			if(coder.getCodec() != null){
	            				mInVideoCoder = coder;
	            				mInVideoCoder.setAutomaticallyStampPacketsForStream(false);
	            				mInVideoCoder.setTimeBase(IRational.make(1, 1000));
	            			  	if (mInVideoCoder.open(null, null) < 0){
	            					log.tracef("died on open v coder");
	            		            throw new RuntimeException("could not open video coder for stream: %s" + mVideoStreamId);
	            		    	}
	            			  	mVInStream = stream;
	                            mVideoStreamId = i;
	            			}else{
	            				log.tracef("died on open video codec");
	            				throw new RuntimeException("died on open video codec");
	            			}
	            		}
	            		if((coder.getCodec()==null)){
	            			log.tracef("shits null dog: %s", coder);
	       		    		ICodec newCodec = ICodec.findDecodingCodec(ID.CODEC_ID_AAC);
	    		    		coder.setCodec(newCodec);
	       		    		coder.setChannels(2);
	       		    		coder.setSampleFormat(Format.FMT_S16);
	       		    		coder.setSampleRate(44100);
	       		    		coder.setBitRate(128000);
	            			log.tracef("shit should no longer be null: %s", coder.getCodec());
	            			mInAudioCoder = coder;
	    		    		if(mInAudioCoder.open(null, null) <0){
	        					log.errorf("died on open new audio coder");
	        		            throw new RuntimeException("could not open audio coder for stream: %s" + mAudioStreamId);
	    		    		}
	            			log.tracef("didnt die on audio, stream id is: %s", i);
	            			mAInStream = stream;
                            mAudioStreamId = i;
	            		}
	            	}else{
        				log.errorf("died on open coder");
	            		throw new RuntimeException("died on open coder");
	            	}
	            }else{
    				log.errorf("died on stream input");
	            	throw new RuntimeException("died on stream input");
	            }
	    	}
	    }
	    if(mAudioStreamId >=0 && mVideoStreamId >=0){
		    codersSetup = true;
	    }else{
			log.errorf("STATUS: v - %s a - %s", mVideoStreamId, mAudioStreamId);
	    	codersSetup= false;
			codersSetupCounter++;
			if(codersSetupCounter>74){
				throw new RuntimeException("died on open coder");
			}
	    }
	}

	private void closeContainer() {
		log.errorf("close containers is being called");
		try {
			log.error("Closing out container");
			if (outC != null) {
				outC.writeTrailer();
				outC.close();
			}
			if (inC != null)
				inC.close();
			if (mInAudioCoder != null)
				mInAudioCoder.close();
			if (mInVideoCoder != null)
				mInVideoCoder.close();
			if (mOutAudioCoder != null)
				mOutAudioCoder.close();
			if (mOutVideoCoder != null)
				mOutVideoCoder.close();
			log.error("Closed out container successfully");
		} catch (Throwable e){
			log.errorf("Throwable caught during container closure: %s", e.toString());
		}finally {
			outC = null;
			inC = null;
			mInAudioCoder = null;
			mInVideoCoder = null;
			mOutAudioCoder = null;
			mOutVideoCoder = null;
			mVideoResampler = null;
			mAudioResampler = null;
			vOutStream = null;
			aOutStream = null;
			mAInStream = null;
			mVInStream = null;
			tearDownPipes();
		}
	}

	private void openContainer() {
		String threadName = "Transcoder[" + getPipePath() + "]";
		log.tracef("Changing thread name: %s; to %s;", Thread.currentThread()
				.getName(), threadName);
		Thread.currentThread().setName(threadName);
		// set up input container
		inC = IContainer.make();
		inCF = IContainerFormat.make();
		inCF.setInputFormat("flv");
		if (inC.open(getSourceURI(), IContainer.Type.READ, inCF, true, false) < 0)
			log.errorf("input container open failed for: %s", getSourceURI());
		// set up output container
		/* known working:
		 * /usr/local/xuggler/bin/ffmpeg
		 * -re 
		 * -i "rtmp://127.0.0.1/live/derp app=live subscribe=derp live=1" 
		 * -vf scale=320:240 
		 * -vcodec libx264 
		 * -b:v 300k 
		 * -acodec libfaac 
		 * -ac 2 
		 * -ar 48000 
		 * -ab 192k 
		 * -map 0 
		 * -vbsf h264_mp4toannexb 
		 * -f segment 
		 * -segment_time 10 
		 * -segment_format mpegts 
		 * -segment_list derp.m3u8 
		 * -segment_wrap 2 
		 * -flags -global_header 
		 * "output%1d.ts"
		 */
		outC = IContainer.make();
		Collection<String> names = outC.getPropertyNames();
		for(String name : names)
			log.errorf("Property at make: %s", name);
		outCF = IContainerFormat.make();
		log.tracef("Containers made");
		outCF.setOutputFormat("segment", null, null);
		log.tracef("Segment format set");
		outCF.setOutputFlag(Flags.FLAG_GLOBALHEADER, false);
		log.tracef("flag 1 set");
		outCF.setOutputFlag(Flags.FLAG_TS_DISCONT, true);
		log.tracef("flag 2 set");
		// ffmpeg expects file%1d.ts so that it can write file0.ts and file1.ts
		String pipeName = getPipePath();
		log.tracef("pipe path got");
		pipeName += "%1d.ts";
		if (outC.open(pipeName, IContainer.Type.WRITE, outCF, true, false, null, null) < 0)
			log.errorf("output container open failed for: %s", getSourceURI());
		log.tracef("container opened");
		//ICodec vcodec = ICodec.guessEncodingCodec(null, "mp4", null, "audio/mp4", ICodec.Type.CODEC_TYPE_VIDEO);
		ICodec vcodec = ICodec.findEncodingCodec(ICodec.ID.CODEC_ID_H264);
		log.tracef("codec created");
		vOutStream = outC.addNewStream(vcodec);
		log.tracef("vOutStream created");
		mOutVideoCoder = vOutStream.getStreamCoder();
		log.tracef("video coder created");
		mOutVideoCoder.setPixelType(Type.YUV420P);
		mOutVideoCoder.setTimeBase(IRational.make(1, 90000));
		mOutVideoCoder.setAutomaticallyStampPacketsForStream(false);
		mOutVideoCoder.setSampleRate(44100);
		mOutVideoCoder.setWidth(getMobileProfile().getWidth());
		mOutVideoCoder.setHeight(getMobileProfile().getHeight());
		mOutVideoCoder.setBitRate(getMobileProfile().getBandwidth());
		log.errorf("video coder done");
		//ICodec codec = ICodec.guessEncodingCodec(null, "mp4", null, "audio/mp4", ICodec.Type.CODEC_TYPE_AUDIO);
		ICodec codec = ICodec.findEncodingCodec(ICodec.ID.CODEC_ID_AAC);
		//ICodec codec = ICodec.findEncodingCodecByName("libfaac");
		aOutStream = outC.addNewStream(codec);
		log.tracef("audio created");
		mOutAudioCoder = aOutStream.getStreamCoder();
		mOutAudioCoder.setSampleRate(44100);
		mOutAudioCoder.setBitRate(128000);
		mOutAudioCoder.setChannels(2);
		log.tracef("audio done");
		if(mOutVideoCoder.open(null, null)<0)
			log.errorf("Couldn't open video coder");
		if(mOutAudioCoder.open(null, null) <0)
			log.errorf("Couldn't open audio coder");
		if(outC.writeHeader()<0){
			log.errorf("we fucked up writing the header");
			throw new RuntimeException("couldn't write header");
		}
		log.tracef("we wrote the header");
	}

	public String getSourceURI() {
		return sourceURI;
	}

	public void setSourceURI(String sourceURI) {
		this.sourceURI = sourceURI;
	}

	public void setMobileProfile(MobileProfile mobileProfile) {
		this.mobileProfile = mobileProfile;
	}
	
	public MobileProfile getMobileProfile() {
		return mobileProfile;
	}

	public String getPipePath() {
		return pipePath;
	}

	public void setPipePath(String pipePath) {
		this.pipePath = pipePath;
	}

	public String getStreamName() {
		return streamName;
	}

	public void setStreamName(String streamName) {
		this.streamName = streamName;
	}

	public void interrupt() {
		currentThread.get().interrupt();
	}

	public boolean ismIsRunning() {
		return mIsRunning;
	}

	public void setmIsRunning(boolean mIsRunning) {
		this.mIsRunning = mIsRunning;
	}

	public boolean isInterruptMe() {
		return interruptMe;
	}

	public void setInterruptMe(boolean interruptMe) {
		log.info("Setting interruptMe on an FfThread");
		this.interruptMe = interruptMe;
	}

	public EncoderStatusDispatcher getEncoderStatusDispatcher() {
		return encoderStatusDispatcher;
	}

	public void setEncoderStatusDispatcher(EncoderStatusDispatcher encoderStatusDispatcher) {
		this.encoderStatusDispatcher = encoderStatusDispatcher;
	}

}
