package com.destinationradiodenver.mobileStreaming;

import io.humble.video.AudioChannel.Layout;
import io.humble.video.Codec;
import io.humble.video.Decoder;
import io.humble.video.Demuxer;
import io.humble.video.DemuxerFormat;
import io.humble.video.DemuxerStream;
import io.humble.video.Encoder;
import io.humble.video.KeyValueBag;
import io.humble.video.MediaAudio;
import io.humble.video.MediaAudioResampler;
import io.humble.video.MediaDescriptor;
import io.humble.video.MediaPacket;
import io.humble.video.MediaPicture;
import io.humble.video.MediaPictureResampler;
import io.humble.video.Muxer;
import io.humble.video.MuxerFormat;
import io.humble.video.MuxerStream;
import io.humble.video.Rational;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;
import org.red5.service.httpstream.model.MobileProfile;

import com.destinationradiodenver.mobileStreaming.messages.EncoderStatusMessage;
import com.destinationradiodenver.mobileStreaming.messages.EncoderStatusMessage.Status;

public class FfThread implements Runnable {
	private static final Logger log = Logger.getLogger(FfThread.class.getName());
	
	private MobileProfile mobileProfile;
	private String sourceURI;
	private boolean mIsRunning;
	private String pipePath;
	private String streamName;
	private int mAudioStreamId;
	private int mVideoStreamId;
	private Demuxer inC;
	private Muxer outC;
	private Encoder mOutAudioCoder;
	private Encoder mOutVideoCoder;
	private Decoder mInAudioCoder;
	private Decoder mInVideoCoder;
	private boolean codersSetup;
	private int codersSetupCounter;
	@SuppressWarnings("unused")
	private boolean doOnce;

	private MediaPacket iPacket;

	private MediaPictureResampler mVideoResampler;

	private MediaAudioResampler mAudioResampler;

	private MuxerStream vOutStream;

	private MuxerStream aOutStream;

	@SuppressWarnings("unused")
	private DemuxerStream mAInStream;

	@SuppressWarnings("unused")
	private DemuxerStream mVInStream;
	
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

	private void transcode() throws InterruptedException, IOException {
		iPacket = MediaPacket.make();
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
		while (inC.read(iPacket) >= 0) {
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
	
	private void writePacket(MediaPacket oPacket) {
		outC.write(oPacket, true);
	}


	private void encodeVideo(MediaPicture postDecode) {
		MediaPacket oPacket = MediaPacket.make();
		mOutVideoCoder.encodeVideo(oPacket, postDecode);
		if(oPacket.isComplete()){
			writePacket(oPacket);
		}
	}


	private void encodeAudio(MediaAudio samples) {
		log.tracef("encode audio called : %s", samples);
		int retval = 0;
		MediaPacket oPacket = MediaPacket.make();
	    MediaAudio preEncode = samples;
	    int numSamplesConsumed = 0;
		log.tracef("entering while loop");
	    while (numSamplesConsumed < preEncode.getNumSamples()) {
	    	if(mOutAudioCoder!=null){
	    		log.tracef("out coder is not null: %s", mOutAudioCoder);
		    	mOutAudioCoder.encodeAudio(oPacket, preEncode);
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
	


	private void decodeAudio(MediaPacket packet) {
		int retval = -1;
	    MediaAudio inSamples = MediaAudio.make(1024, 44100, 2, Layout.CH_LAYOUT_STEREO, (io.humble.video.AudioFormat.Type.SAMPLE_FMT_S16));
	    MediaAudio reSamples = null;
	    int offset = 0;
	    log.tracef("ready to decode audio");
	    while (offset < packet.getSize()) {
	    	retval = mInAudioCoder.decodeAudio(null, packet, offset);
	        if (retval <= 0) {
	        	log.errorf("couldn't decode audio");
	            throw new RuntimeException("could not decode audio");
	        }
	        offset += retval;
		    log.tracef("audio loop offset: %s", offset);
		    MediaAudio postDecode = inSamples;
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
	private MediaAudio resampleAudio(MediaAudio samples){
		MediaAudio reSamples;
	    openAudioResampler(samples);
	    MediaAudio outSamples = MediaAudio.make(1024, 44100, 2, Layout.CH_LAYOUT_STEREO, (io.humble.video.AudioFormat.Type.SAMPLE_FMT_S16));
	    if (mAudioResampler != null && samples.getNumSamples() > 0) {
	      log.tracef("ready to resample audio");
	      MediaAudio preResample = samples;
	      if (preResample == null)
	    	  preResample = samples;
	      int retval = -1;
	      retval = mAudioResampler.resample(outSamples, preResample);
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
	      	MediaAudio postResample= outSamples;
			if (postResample == null)
				postResample = outSamples;
			reSamples = postResample;
		    } else {
		      reSamples = samples;
		    }
		    return reSamples;
	  }
	
	private void openAudioResampler(MediaAudio samples) {
	    if (mAudioResampler == null && mOutAudioCoder != null) {
	    	mAudioResampler = MediaAudioResampler.make(mOutAudioCoder.getChannelLayout(), mOutAudioCoder.getSampleRate(), mOutAudioCoder.getSampleFormat(), mInAudioCoder.getChannelLayout(), mInAudioCoder.getSampleRate(), mInAudioCoder.getSampleFormat());
	        if (mAudioResampler == null) {
	          throw new RuntimeException("needed to resample audio but couldn't allocate a resampler");
	        }
	        log.tracef("Setup resample to convert \"%skhz %s channel audio\" to \"%skhz %s channel\" audio",
	            new Object[]{
	            mAudioResampler.getInputSampleRate(),
	            mAudioResampler.getInputChannels(),
	            mAudioResampler.getOutputSampleRate(),
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


	

	private void decodeVideo(MediaPacket packet) {
	    int retval = -1;
		MediaPicture inPicture = MediaPicture.make(mInVideoCoder.getWidth(), mInVideoCoder.getHeight(), mInVideoCoder.getPixelFormat());
		MediaPicture reSample = null;
	    int offset = 0;
	    while (offset < packet.getSize()) {
	        retval = mInVideoCoder.decodeVideo(inPicture, packet, offset);
	        if(retval <= 0){
	        	log.tracef("noisy shit: decoding video failed for: %s", getSourceURI());
	        	return;
	        }
	        offset += retval;
	        MediaPicture postDecode = inPicture;
		    if(postDecode.isComplete()){
		    	reSample = resampleVideo(postDecode);
		    }
		    if(reSample!=null)
			    if(reSample.isComplete()){
			    	encodeVideo(reSample);
			    }
	    }
	}

	private MediaPicture resampleVideo(MediaPicture postDecode) {
		MediaPicture reSample;
		openVideoResampler(postDecode);
		if (mVideoResampler != null) {
			log.tracef("ready to resample video");
			MediaPicture outPicture = MediaPicture.make(mOutVideoCoder.getWidth(), mOutVideoCoder.getHeight(), mOutVideoCoder.getPixelFormat());
			MediaPicture preResample = postDecode;
			mVideoResampler.resample(outPicture, preResample);
			if (log.isTraceEnabled())
				log.tracef("resampled input picture (type: %s; width: %s; height: %s) to output (type: %s; width: %s; height: %s)",
					new Object[] { preResample.getType(),
						preResample.getWidth(),
						preResample.getHeight(),
						outPicture.getTimeBase(),
						outPicture.getWidth(),
						outPicture.getHeight() });
			MediaPicture postResample = outPicture;
			reSample = postResample;
		} else {
			reSample = postDecode;
		}
		return reSample;
	}
	
	  private void openVideoResampler(MediaPicture picture)
	  {
	    if (mVideoResampler == null && mOutVideoCoder != null)
	    {
	      if (picture.getWidth() <= 0 || picture.getHeight()<=0)
	        throw new RuntimeException("frame has no data in it so cannot resample");

	      // We set up our resampler.
	      if (mOutVideoCoder.getPixelFormat() != picture.getFormat() ||
	          mOutVideoCoder.getWidth() != picture.getWidth() ||
	          mOutVideoCoder.getHeight() != picture.getHeight())
	      {
	        mVideoResampler = MediaPictureResampler.make(mOutVideoCoder.getWidth(), mOutVideoCoder.getHeight(), mOutVideoCoder.getPixelFormat(), mInVideoCoder.getWidth(), mInVideoCoder.getHeight(), mInVideoCoder.getPixelFormat(), 0);
	        if (mVideoResampler == null)
	        {
	          log.errorf("Could not create a video resampler; this object is only available in the GPL version of aaffmpeg");
	          throw new RuntimeException("needed to resample video but couldn't allocate a resampler; you need the GPL version of AAFFMPEG installed?");
	        }
	        log.tracef("Setup resample to convert \"%sx%s %s video\" to \"%sx%s %s video\" audio",
	            new Object[]{
	            mVideoResampler.getInputWidth(),
	            mVideoResampler.getInputHeight(),
	            mVideoResampler.getInputFormat(),
	            mVideoResampler.getOutputWidth(),
	            mVideoResampler.getOutputHeight(),
	            mVideoResampler.getOutputFormat()
	            });
	      }
	    }
	  }


	private void openInputCoders(MediaPacket packet) {
        int numStreams = -1;
		try {
			numStreams = inC.getNumStreams();
		} catch (InterruptedException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(numStreams<0){
			//TODO: broken windows, clean this up.
			throw new RuntimeException("No streams");
		}
        log.tracef("number of streams: %s", numStreams);
	    if (mAudioStreamId == -1 || mVideoStreamId == -1){
	        log.tracef("first condition true");
	    	for(int i = 0; i < numStreams; i++){
		        log.tracef("beginning for loop");
	            DemuxerStream stream = null;
				try {
					stream = inC.getStream(i);
				} catch (InterruptedException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	            if(stream!=null){
			        log.tracef("got stream: %s", stream);
	            	Decoder coder = stream.getDecoder();
	            	if(coder != null){
				        log.tracef("got coder: %s", coder);
	            		//is audio stream
	            		if((coder.getCodecType().equals(MediaDescriptor.Type.MEDIA_AUDIO))&&(mAudioStreamId==-1)&&(packet.getStreamIndex()==i)){
	            			log.tracef("got audio coder: %s", coder);
	            			if(coder.getCodec() != null){
	            				mInAudioCoder = coder;
	            		    	mInAudioCoder.open(null, null);
            					log.errorf("didn't die on open a coder");
		            			log.tracef("didnt die on audio, stream id is: %s", i);
		            			mAInStream = stream;
	                            mAudioStreamId = i;
	            			}else{
	            				log.errorf("died on open audio codec");
	            				throw new RuntimeException("died on open audio codec");
	            			}
	            		}
	            		//is video stream
	            		if(coder.getCodecType().equals(MediaDescriptor.Type.MEDIA_VIDEO)&&(mVideoStreamId==-1)&&(packet.getStreamIndex()==i)){
	            			log.tracef("got video coder: %s", coder);
	            			if(coder.getCodec() != null){
	            				mInVideoCoder = coder;
	            				//mInVideoCoder.setAutomaticallyStampPacketsForStream(false);
	            				mInVideoCoder.setTimeBase(Rational.make(1, 1000));
	            			  	mInVideoCoder.open(null, null);
	            			  	log.tracef("didn't die on open v coder");
	            			  	mVInStream = stream;
	                            mVideoStreamId = i;
	            			}else{
	            				log.tracef("died on open video codec");
	            				throw new RuntimeException("died on open video codec");
	            			}
	            		}
	            		if((coder.getCodec()==null)){
	            			log.tracef("shits null dog: %s", coder);
	            			coder.setChannels(2);
	            			coder.setSampleFormat(io.humble.video.AudioFormat.Type.SAMPLE_FMT_S16);
	            			coder.setSampleRate(44100);
	            			//coder.setBitRate(128000);
	            			log.tracef("shit should no longer be null: %s", coder.getCodec());
	            			mInAudioCoder = coder;
	    		    		mInAudioCoder.open(null, null);
        					log.errorf("did not die on open new audio coder");
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
				//outC.writeTrailer();
				outC.close();
			}
			if (inC != null)
				inC.close();
			if (mInAudioCoder != null)
				mInAudioCoder.flush();
			if (mInVideoCoder != null)
				mInVideoCoder.flush();
			if (mOutAudioCoder != null)
				mOutAudioCoder.encodeAudio(null, null);
			if (mOutVideoCoder != null)
				mOutVideoCoder.encodeVideo(null, null);
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
		Demuxer inC = Demuxer.make();
		try {
			inC.open(getSourceURI(), DemuxerFormat.findFormat("flv"), true, false, null, null);
		} catch (InterruptedException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		log.infof("Current state: %s", inC.getState().toString());
		
		outC = Muxer.make(pipePath, MuxerFormat.guessFormat("segment", null, null), "segment");
		log.tracef("Containers made");
		log.tracef("Segment format set");
		// ffmpeg expects file%1d.ts so that it can write file0.ts and file1.ts
		String pipeName = getPipePath();
		KeyValueBag outCoderBag = KeyValueBag.make();
		outCoderBag.setValue("segment_time", "10");
		outCoderBag.setValue("segment_format", "mpegts");
		outCoderBag.setValue("segment_wrap", "2");
		log.tracef("pipe path got");
		pipeName += "%1d.ts";
		try {
			outC.open(null, outCoderBag);
		} catch (InterruptedException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		log.tracef("container opened");
		Codec vcodec = Codec.findEncodingCodec(Codec.ID.CODEC_ID_H264);
		log.tracef("codec created");
		mOutVideoCoder = Encoder.make(vcodec);
		log.tracef("encoder created");
		vOutStream = outC.addNewStream(mOutVideoCoder);
		log.tracef("vOutStream created");
		log.tracef("video coder created");
		mOutVideoCoder.setPixelType(io.humble.video.PixelFormat.Type.PIX_FMT_YUVA420P);
		mOutVideoCoder.setTimeBase(Rational.make(1, 90000));
//		mOutVideoCoder.setAutomaticallyStampPacketsForStream(false);
		mOutVideoCoder.setSampleRate(44100);
		mOutVideoCoder.setWidth(getMobileProfile().getWidth());
		mOutVideoCoder.setHeight(getMobileProfile().getHeight());
//		mOutVideoCoder.setBitRate(getMobileProfile().getBandwidth());
		log.errorf("video coder done");
		//ICodec codec = ICodec.guessEncodingCodec(null, "mp4", null, "audio/mp4", ICodec.Type.CODEC_TYPE_AUDIO);
		Codec acodec = Codec.findDecodingCodec(Codec.ID.CODEC_ID_AAC);
		mOutAudioCoder = Encoder.make(acodec);
		mOutAudioCoder.setSampleRate(44100);
		//mOutAudioCoder = aOutStream.getStreamCoder();
		//mOutAudioCoder.setBitRate(128000);
		mOutAudioCoder.setChannels(2);
		aOutStream = outC.addNewStream(mOutAudioCoder);
		log.tracef("audio created");
		log.tracef("audio done");
		mOutVideoCoder.open(null, null);
		log.errorf("opened video coder");
		mOutAudioCoder.open(null, null);
		log.errorf("opened audio coder");
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
