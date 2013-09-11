package org.red5.io.flv.impl;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.io.ITag;
import org.red5.io.flv.IKeyFrameDataAnalyzer.KeyFrameMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FLVReaderTest {

	private static Logger log = LoggerFactory.getLogger(FLVReaderTest.class);

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testFLVReaderFile() {
		File[] files = new File[] { new File("test/fixtures/h264_aac.flv"), new File("test/fixtures/h264_mp3.flv"), new File("test/fixtures/h264_speex.flv") };

		try {
			for (File file : files) {
				FLVReader reader = new FLVReader(file, true);

				KeyFrameMeta meta = reader.analyzeKeyFrames();
				log.debug("Meta: {}", meta);

				ITag tag = null;
				for (int t = 0; t < 32; t++) {
					tag = reader.readTag();
					log.debug("Tag: {}", tag);
				}
				
				reader.close();
				log.info("----------------------------------------------------------------------------------");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
